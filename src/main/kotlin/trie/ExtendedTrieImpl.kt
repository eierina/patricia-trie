import com.r3.corda.evmbridge.evm.datasource.Source
import com.r3.corda.evmbridge.evm.rlp.RLP.decode2
import com.r3.corda.evmbridge.evm.rlp.RLPList
import com.r3.corda.evmbridge.evm.trie.TrieImpl
import com.r3.corda.evmbridge.evm.trie.TrieKey
import org.web3j.utils.Numeric
import java.util.*


interface KeyValueStore {
    fun get(key: ByteArray): ByteArray?

    fun isEmpty(): Boolean
}

class ExtendedTrieImpl : TrieImpl {
    constructor() : super()
    constructor(valueSource: Source<ByteArray?, ByteArray>) : super(valueSource)

    class SimpleKeyValueStore : KeyValueStore {

        data class ArrayKey(val data: ByteArray) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is ArrayKey) return false
                if (!data.contentEquals(other.data)) return false
                return true
            }

            override fun hashCode(): Int {
                return data.contentHashCode()
            }
        }

        private val store: HashMap<ArrayKey, ByteArray> = HashMap()

        override fun get(key: ByteArray): ByteArray? {
            return store[ArrayKey(key)]
        }

        fun put(key: ByteArray, value: ByteArray) {
            store[ArrayKey(key)] = value
        }

        override fun isEmpty() = store.isEmpty()
    }


    fun generateMerkleProof(key: ByteArray): KeyValueStore {
        val trieKey = TrieKey.fromNormal(key)
        return generateMerkleProof(root, trieKey, SimpleKeyValueStore())
    }

    private fun generateMerkleProof(n: Node?, k: TrieKey, proof: SimpleKeyValueStore): KeyValueStore {
        var key = k
        var node = n
        while (true) {
            if (node == null) throw IllegalArgumentException("Node not found")

            val rawNode = node.raw()
            proof.put(node.hash!!, rawNode)
            val str = "hash ${Numeric.toHexString(node.hash!!)} buf ${Numeric.toHexString(rawNode)}}"

            if (node.type == NodeType.KVNodeValue) {
                if (!key.isEmpty) throw IllegalArgumentException("Key not found (longer than path)")
                return proof
            }

            if (node.type == NodeType.BranchNode) {
                if (key.isEmpty) {
                    return proof // TODO: if key is empty, then branch node must have value?
                } else {
                    node = node.branchNodeGetChild(key.getHex(0))
                    key = key.shift(1)
                    continue
                }
            }

            if (node.type == NodeType.KVNodeNode) {
                key = key.matchAndShift(node.kvNodeGetKey())
                    ?: throw IllegalArgumentException("Node is not a prefix to the search key")
                if (key.isEmpty) throw IllegalArgumentException("Node should lead to another node")
                node = node.kvNodeGetChildNode()
                continue
            }

            throw InvalidNodeTypeException()
        }
    }

    fun verifyProof(rootHash: ByteArray, key: ByteArray, proof: KeyValueStore): ByteArray? {
        var hash = rootHash
        var trieKey = TrieKey.fromNormal(key)
        var i: Int = 0
        // TODO: probably needs also to check the key nibbles too along the path
        while (true) {
            var buf = proof.get(hash) // buf == go-buf
            var node = deserialize(buf, trieKey.getHex(0))
            val str = "hash ${Numeric.toHexString(node!!.hash)} buf  ${Numeric.toHexString(buf)}"

            if (node == null) { return null }

            if (node.type === NodeType.BranchNode || node.type === NodeType.KVNodeNode) {
                hash = node.hash!!
                trieKey = trieKey.shift(1)
                node = null
                buf = null
            } else if (node.type === NodeType.KVNodeValue) {
                val value = node.kvNodeGetValue()
                return value
            } else {
                throw InvalidNodeTypeException()
            }
            ++i
        }
    }

    private fun deserialize(bytes: ByteArray?, index: Int): Node? {
        if (bytes == null) return null
        val nodeElement = decode2(bytes)[0] as RLPList?
        val nodeRlp = nodeElement!![index]!!.rLPData
        return Node(nodeRlp!!)
    }

    class KeyNotFoundException(val key: String) : Exception("Trie does not contain key: $key") {
        constructor(keyBytes: ByteArray) : this(Numeric.toHexString(keyBytes))
    }

    class InvalidNodeTypeException : Exception("Invalid node type")
}