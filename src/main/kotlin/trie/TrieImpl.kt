package com.r3.corda.evmbridge.evm.trie

import com.r3.corda.evmbridge.evm.datasource.Source
import com.r3.corda.evmbridge.evm.datasource.inmem.HashMapDB
import org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY
import org.ethereum.util.FastByteComparisons
import com.r3.corda.evmbridge.evm.crypto.HashUtil.EMPTY_TRIE_HASH
import com.r3.corda.evmbridge.evm.rlp.RLP
import org.ethereum.util.ByteUtil
import org.slf4j.LoggerFactory
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import java.util.*

open class TrieImpl @JvmOverloads constructor(
    val cache: Source<ByteArray?, ByteArray>,
    root: ByteArray? = null
) :
    Trie<ByteArray?> {
    enum class NodeType {
        BranchNode,
        KVNodeValue,
        KVNodeNode
    }

    public inner class Node {
        var hash: ByteArray? = null
        private var rlp: ByteArray? = null
        private var parsedRlp: RLP.LList? = null
        var dirty = false
        private var children: Array<Any?>? = null

        // new empty BranchNode
        constructor() {
            children = arrayOfNulls(17)
            dirty = true
        }

        // new KVNode with key and (value or node)
        constructor(key: TrieKey?, valueOrNode: Any?) : this(arrayOf<Any?>(key, valueOrNode)) {
            dirty = true
        }

        // new Node with hash or RLP
        constructor(hashOrRlp: ByteArray) {
            if (hashOrRlp.size == 32) {
                hash = hashOrRlp
            } else {
                rlp = hashOrRlp
            }
        }

        private constructor(parsedRlp: RLP.LList) {
            this.parsedRlp = parsedRlp
            rlp = parsedRlp.encoded
        }

        private constructor(children: Array<Any?>) {
            this.children = children
        }

        fun resolveCheck(): Boolean {
            if (rlp != null || parsedRlp != null || hash == null) return true
            rlp = getHash(hash!!)
            return rlp != null
        }



        private fun resolve() {
            if (!resolveCheck()) {
                logger.error("Invalid Trie state, can't resolve hash " + Numeric.toHexStringNoPrefix(hash))
                throw RuntimeException("Invalid Trie state, can't resolve hash " + Numeric.toHexStringNoPrefix(hash))
            }
        }

        fun encode(): ByteArray {
            return encode(1, true)
        }

        private fun encode(depth: Int, forceHash: Boolean): ByteArray {
            return if (!dirty) {
                if (hash != null) RLP.encodeElement(hash!!) else rlp!!
            } else {
                val type = type
                val ret: ByteArray
                if (type == NodeType.BranchNode) {
                    val encoded = arrayOfNulls<ByteArray>(17)
                    for (i in 0..15) {
                        val child = branchNodeGetChild(i)
                        encoded[i] = child?.encode(depth + 1, false) ?: RLP.EMPTY_ELEMENT_RLP
                    }
                    val value = branchNodeGetValue()
//                        encoded[16] = RLP.encodeElement(value)
//                        ret = RLP.encodeList(encoded)
                    encoded[16] = RLP.encodeElement(value ?: EMPTY_BYTE_ARRAY) // TODO: nullable
                    ret = RLP.encodeList(*encoded.requireNoNulls())
                } else if (type == NodeType.KVNodeNode) {
                    ret = RLP.encodeList(
                        RLP.encodeElement(kvNodeGetKey()!!.toPacked()),
                        kvNodeGetChildNode()!!.encode(depth + 1, false)
                    )
                } else {
                    val value = kvNodeGetValue()
                    ret = RLP.encodeList(
                        RLP.encodeElement(kvNodeGetKey()!!.toPacked()),
                        RLP.encodeElement(value ?: EMPTY_BYTE_ARRAY)
                    )
                }
                if (hash != null) {
                    deleteHash(hash!!)
                }
                dirty = false
                if (ret.size < 32 && !forceHash) {
                    rlp = ret
                    ret
                } else {
                    hash = Hash.sha3(ret)
                    addHash(hash, ret)
                    //RLP.encodeElement(hash)
                    RLP.encodeElement(hash!!) // TODO: nullable
                }
            }
        }

        fun raw(): ByteArray {
            return raw(1)
        }

        private fun raw(depth: Int): ByteArray {
            val type = type
            val ret: ByteArray
            if (type == NodeType.BranchNode) {
                val encoded = arrayOfNulls<ByteArray>(17)
                for (i in 0..15) {
                    val child = branchNodeGetChild(i)
                    encoded[i] = child?.encode(depth + 1, false) ?: RLP.EMPTY_ELEMENT_RLP
                }
                val value = branchNodeGetValue()
//                        encoded[16] = RLP.encodeElement(value)
//                        ret = RLP.encodeList(encoded)
                encoded[16] = RLP.encodeElement(value ?: EMPTY_BYTE_ARRAY) // TODO: nullable
                ret = RLP.encodeList(*encoded.requireNoNulls())
            } else if (type == NodeType.KVNodeNode) {
                ret = RLP.encodeList(
                    RLP.encodeElement(kvNodeGetKey()!!.toPacked()),
                    kvNodeGetChildNode()!!.encode(depth + 1, false)
                )
            } else {
                val value = kvNodeGetValue()
                ret = RLP.encodeList(
                    RLP.encodeElement(kvNodeGetKey()!!.toPacked()),
                    RLP.encodeElement(value ?: EMPTY_BYTE_ARRAY)
                )
            }
            return ret
        }

        private fun parse() {
            if (children != null) return
            resolve()
            val list = if (parsedRlp == null) RLP.decodeLazyList(rlp!!) else parsedRlp
            if (list!!.size() == 2) {
                children = arrayOfNulls(2)
                val key = TrieKey.fromPacked(list.getBytes(0))
                children!![0] = key
                if (key.isTerminal) {
                    children!![1] = list.getBytes(1)
                } else {
                    //children!![1] = if (list.isList(1)) Node(list.getList(1)) else Node(
                    children!![1] = if (list.isList(1)) Node(list.getList(1)!!) else Node( // TODO: nullable
                        list.getBytes(1)
                    )
                }
            } else {
                children = arrayOfNulls(17)
                parsedRlp = list
            }
        }

        fun branchNodeGetChild(hex: Int): Node? {
            parse()
            assert(type == NodeType.BranchNode)
            var n = children!![hex]
            if (n == null && parsedRlp != null) {
                n = if (parsedRlp!!.isList(hex)) {
                    //n = Node(parsedRlp!!.getList(hex))
                    Node(parsedRlp!!.getList(hex)!!) // TODO: nullable
                } else {
                    val bytes = parsedRlp!!.getBytes(hex)
                    if (bytes.isEmpty()) {
                        NULL_NODE
                    } else {
                        Node(bytes)
                    }
                }
                children!![hex] = n
            }
            return if (n === NULL_NODE) null else n as Node?
        }

        fun branchNodeSetChild(hex: Int, node: Node?): Node {
            parse()
            assert(type == NodeType.BranchNode)
            children!![hex] = node ?: NULL_NODE
            dirty = true
            return this
        }

        fun branchNodeGetValue(): ByteArray? {
            parse()
            assert(type == NodeType.BranchNode)
            var n = children!![16]
            if (n == null && parsedRlp != null) {
                val bytes = parsedRlp!!.getBytes(16)
                n = if (bytes.size == 0) {
                    NULL_NODE
                } else {
                    bytes
                }
                children!![16] = n
            }
            return if (n === NULL_NODE) null else n as ByteArray?
        }

        fun branchNodeSetValue(`val`: ByteArray?): Node {
            parse()
            assert(type == NodeType.BranchNode)
            children!![16] = `val` ?: NULL_NODE
            dirty = true
            return this
        }

        fun branchNodeCompactIdx(): Int {
            parse()
            assert(type == NodeType.BranchNode)
            var cnt = 0
            var idx = -1
            for (i in 0..15) {
                if (branchNodeGetChild(i) != null) {
                    cnt++
                    idx = i
                    if (cnt > 1) return -1
                }
            }
            return if (cnt > 0) idx else if (branchNodeGetValue() == null) -1 else 16
        }

        fun branchNodeCanCompact(): Boolean {
            parse()
            assert(type == NodeType.BranchNode)
            var cnt = 0
            for (i in 0..15) {
                cnt += if (branchNodeGetChild(i) == null) 0 else 1
                if (cnt > 1) return false
            }
            return cnt == 0 || branchNodeGetValue() == null
        }

        fun kvNodeGetKey(): TrieKey? {
            parse()
            assert(type != NodeType.BranchNode)
            return children!![0] as TrieKey?
        }

        fun kvNodeGetChildNode(): Node? {
            parse()
            assert(type == NodeType.KVNodeNode)
            return children!![1] as Node?
        }

        fun kvNodeGetValue(): ByteArray? {
            parse()
            assert(type == NodeType.KVNodeValue)
            return children!![1] as ByteArray?
        }

        fun kvNodeSetValue(value: ByteArray?): Node {
            parse()
            assert(type == NodeType.KVNodeValue)
            children!![1] = value
            dirty = true
            return this
        }

        fun kvNodeGetValueOrNode(): Any? {
            parse()
            assert(type != NodeType.BranchNode)
            return children!![1]
        }

        fun kvNodeSetValueOrNode(valueOrNode: Any?): Node {
            parse()
            assert(type != NodeType.BranchNode)
            children!![1] = valueOrNode
            dirty = true
            return this
        }

        val type: NodeType
            get() {
                parse()
                return if (children!!.size == 17) NodeType.BranchNode else if (children!![1] is Node) NodeType.KVNodeNode else NodeType.KVNodeValue
            }

        fun dispose() {
            if (hash != null) {
                deleteHash(hash!!)
            }
        }

        fun invalidate(): Node {
            dirty = true
            return this
        }

        /***********  Dump methods   */
        fun dumpStruct(indent: String, prefix: String): String {
            var ret = indent + prefix + type + (if (dirty) " *" else "") +
                    if (hash == null) "" else "(hash: " + Numeric.toHexStringNoPrefix(hash).substring(0, 6) + ")"
            if (type == NodeType.BranchNode) {
                val value = branchNodeGetValue()
                ret += (if (value == null) "" else " [T] = " + Numeric.toHexStringNoPrefix(value)) + "\n"
                for (i in 0..15) {
                    val child = branchNodeGetChild(i)
                    if (child != null) {
                        ret += child.dumpStruct("$indent  ", "[$i] ")
                    }
                }
            } else if (type == NodeType.KVNodeNode) {
                ret += " [" + kvNodeGetKey() + "]\n"
                ret += kvNodeGetChildNode()!!.dumpStruct("$indent  ", "")
            } else {
                ret += " [" + kvNodeGetKey() + "] = " + Numeric.toHexStringNoPrefix(kvNodeGetValue()) + "\n"
            }
            return ret
        }

        fun dumpTrieNode(compact: Boolean): List<String> {
            val ret: MutableList<String> = ArrayList()
            if (hash != null) {
                ret.add(hash2str(hash!!, compact) + " ==> " + dumpContent(false, compact))
            }
            if (type == NodeType.BranchNode) {
                for (i in 0..15) {
                    val child = branchNodeGetChild(i)
                    if (child != null) ret.addAll(child.dumpTrieNode(compact))
                }
            } else if (type == NodeType.KVNodeNode) {
                ret.addAll(kvNodeGetChildNode()!!.dumpTrieNode(compact))
            }
            return ret
        }

        private fun dumpContent(recursion: Boolean, compact: Boolean): String {
            if (recursion && hash != null) return hash2str(hash!!, compact)
            var ret: String
            if (type == NodeType.BranchNode) {
                ret = "["
                for (i in 0..15) {
                    val child = branchNodeGetChild(i)
                    ret += if (i == 0) "" else ","
                    ret += child?.dumpContent(true, compact) ?: ""
                }
                val value = branchNodeGetValue()
                ret += if (value == null) "" else ", " + val2str(value, compact)
                ret += "]"
            } else if (type == NodeType.KVNodeNode) {
                ret = "[<" + kvNodeGetKey() + ">, " + kvNodeGetChildNode()!!.dumpContent(true, compact) + "]"
            } else {
                ret = "[<" + kvNodeGetKey() + ">, " + val2str(kvNodeGetValue(), compact) + "]"
            }
            return ret
        }

        override fun toString(): String {
            return type.toString() + (if (dirty) " *" else "") + if (hash == null) "" else "(hash: " + Numeric.toHexStringNoPrefix(
                hash
            ) + " )"
        }
    }

    interface ScanAction {
        fun doOnNode(hash: ByteArray?, node: Node?)
        fun doOnValue(nodeHash: ByteArray?, node: Node?, key: ByteArray?, value: ByteArray?)
    }

    protected var root: Node? = null
    //private var async = true

    @JvmOverloads
    constructor(root: ByteArray? = null) : this(HashMapDB<ByteArray>(), root)

    init {
        setRoot(root)
    }

    private fun encode() {
        if (root != null) {
            root!!.encode()
        }
    }

    override fun setRoot(root: ByteArray?) {
        if (root != null && !FastByteComparisons.equal(root, EMPTY_TRIE_HASH)) {
            this.root = Node(root)
        } else {
            this.root = null
        }
    }

    private fun hasRoot(): Boolean {
        return root != null && root!!.resolveCheck()
    }

    private fun getHash(hash: ByteArray): ByteArray {
        return cache[hash]
    }

    private fun addHash(hash: ByteArray?, ret: ByteArray) {
        cache.put(hash, ret)
    }

    private fun deleteHash(hash: ByteArray) {
        cache.delete(hash)
    }

    override operator fun get(key: ByteArray?): ByteArray? {
        if (!hasRoot()) return null // treating unknown root hash as empty trie
        //val k = TrieKey.fromNormal(key)
        val k = TrieKey.fromNormal(key!!) // TODO: nullable
        return get(root, k)!!
    }

    private operator fun get(n: Node?, k: TrieKey): ByteArray? {
        if (n == null) return null
        val type = n.type
        return if (type == NodeType.BranchNode) {
            if (k.isEmpty) return n.branchNodeGetValue()
            val childNode = n.branchNodeGetChild(k.getHex(0))
            get(childNode, k.shift(1))
        } else {
            val k1 = k.matchAndShift(n.kvNodeGetKey()) ?: return null
            if (type == NodeType.KVNodeValue) {
                if (k1.isEmpty) n.kvNodeGetValue() else null
            } else {
                get(n.kvNodeGetChildNode(), k1)
            }
        }
    }

    override fun put(key: ByteArray?, value: ByteArray?) {
        val k = TrieKey.fromNormal(key!!)// TODO: nullable
        if (root == null) {
            if (value != null && value.size > 0) {
                root = Node(k, value)
            }
        } else {
            root = if (value == null || value.size == 0) {
                delete(root, k)
            } else {
                insert(root, k, value)
            }
        }
    }

    private fun insert(n: Node?, k: TrieKey?, nodeOrValue: Any?): Node {
        val type = n!!.type
        return if (type == NodeType.BranchNode) {
            if (k!!.isEmpty) return n.branchNodeSetValue(nodeOrValue as ByteArray?)
            val childNode = n.branchNodeGetChild(k.getHex(0))
            if (childNode != null) {
                n.branchNodeSetChild(k.getHex(0), insert(childNode, k.shift(1), nodeOrValue))
            } else {
                val childKey = k.shift(1)
                val newChildNode: Node
                if (!childKey.isEmpty) {
                    newChildNode = Node(childKey, nodeOrValue)
                } else {
                    newChildNode =
                        if (nodeOrValue is Node) nodeOrValue else Node(
                            childKey,
                            nodeOrValue
                        )
                }
                n.branchNodeSetChild(k.getHex(0), newChildNode)
            }
        } else {
            val currentNodeKey = n.kvNodeGetKey()
            val commonPrefix = k!!.getCommonPrefix(currentNodeKey)
            if (commonPrefix.isEmpty) {
                val newBranchNode: Node = Node()
                insert(newBranchNode, currentNodeKey, n.kvNodeGetValueOrNode())
                insert(newBranchNode, k, nodeOrValue)
                n.dispose()
                newBranchNode
            } else if (commonPrefix.equals(k)) {
                n.kvNodeSetValueOrNode(nodeOrValue)
            } else if (commonPrefix.equals(currentNodeKey)) {
                insert(n.kvNodeGetChildNode(), k.shift(commonPrefix.length), nodeOrValue)
                n.invalidate()
            } else {
                val newBranchNode: Node = Node()
                val newKvNode: Node =
                    Node(commonPrefix, newBranchNode)
                // TODO can be optimized
                insert(newKvNode, currentNodeKey, n.kvNodeGetValueOrNode())
                insert(newKvNode, k, nodeOrValue)
                n.dispose()
                newKvNode
            }
        }
    }

    override fun delete(key: ByteArray?) {
        val k = TrieKey.fromNormal(key!!)// TODO: nullable
        if (root != null) {
            root = delete(root, k)
        }
    }

    private fun delete(n: Node?, k: TrieKey): Node? {
        val type = n!!.type
        val newKvNode: Node
        if (type == NodeType.BranchNode) {
            if (k.isEmpty) {
                n.branchNodeSetValue(null)
            } else {
                val idx = k.getHex(0)
                val child = n.branchNodeGetChild(idx) ?: return n
                // no key found
                val newNode = delete(child, k.shift(1))
                n.branchNodeSetChild(idx, newNode)
                if (newNode != null) return n // newNode != null thus number of children didn't decrease
            }

            // child node or value was deleted and the branch node may need to be compacted
            val compactIdx = n.branchNodeCompactIdx()
            if (compactIdx < 0) return n // no compaction is required

            // only value or a single child left - compact branch node to kvNode
            n.dispose()
            if (compactIdx == 16) { // only value left
                return Node(TrieKey.empty(true), n.branchNodeGetValue())
            } else { // only single child left
                newKvNode = Node(TrieKey.singleHex(compactIdx), n.branchNodeGetChild(compactIdx))
            }
        } else { // n - kvNode
            val k1 = k.matchAndShift(n.kvNodeGetKey())
            newKvNode = if (k1 == null) {
                // no key found
                return n
            } else if (type == NodeType.KVNodeValue) {
                return if (k1.isEmpty) {
                    // delete this kvNode
                    n.dispose()
                    null
                } else {
                    // else no key found
                    n
                }
            } else {
                val newChild = delete(n.kvNodeGetChildNode(), k1) ?: throw RuntimeException("Shouldn't happen")
                n.kvNodeSetValueOrNode(newChild)
            }
        }

        // if we get here a new kvNode was created, now need to check
        // if it should be compacted with child kvNode
        val newChild = newKvNode.kvNodeGetChildNode()
        return if (newChild!!.type != NodeType.BranchNode) {
            // two kvNodes should be compacted into a single one
            val newKey = newKvNode.kvNodeGetKey()!!.concat(newChild.kvNodeGetKey())
            val newNode: Node =
                Node(newKey, newChild.kvNodeGetValueOrNode())
            newChild.dispose()
            newKvNode.dispose()
            newNode
        } else {
            // no compaction needed
            newKvNode
        }
    }

    override val rootHash: ByteArray?
        get() {
            encode()
            return if (root != null) root!!.hash else EMPTY_TRIE_HASH
        }

    override fun clear() {
        throw RuntimeException("Not implemented yet")
    }

    override fun flush(): Boolean {
        return if (root != null && root!!.dirty) {
            // persist all dirty nodes to underlying Source
            encode()
            // release all Trie Node instances for GC
            //root = Node(root!!.hash)
            root = Node(root!!.hash!!)  // TODO: nullable
            true
        } else {
            false
        }
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val trieImpl1 = o as TrieImpl
        //return FastByteComparisons.equal(rootHash, trieImpl1.rootHash)
        return FastByteComparisons.equal(rootHash!!, trieImpl1.rootHash!!) // TODO: nullable
    }

    fun dumpStructure(): String {
        return if (root == null) "<empty>" else root!!.dumpStruct("", "")
    }

    @JvmOverloads
    fun dumpTrie(compact: Boolean = true): String {
        if (root == null) return "<empty>"
        encode()
        val ret = StringBuilder()
        val strings = root!!.dumpTrieNode(compact)
        ret.append("Root: " + hash2str(rootHash!!, compact) + "\n")
        for (s in strings) {
            ret.append(s).append('\n')
        }
        return ret.toString()
    }

    fun scanTree(scanAction: ScanAction) {
        scanTree(root, TrieKey.empty(false), scanAction)
    }

    fun scanTree(node: Node?, k: TrieKey, scanAction: ScanAction) {
        if (node == null) return
        if (node.hash != null) {
            scanAction.doOnNode(node.hash, node)
        }
        if (node.type == NodeType.BranchNode) {
            if (node.branchNodeGetValue() != null) scanAction.doOnValue(
                node.hash,
                node,
                k.toNormal(),
                node.branchNodeGetValue()
            )
            for (i in 0..15) {
                scanTree(node.branchNodeGetChild(i), k.concat(TrieKey.singleHex(i)), scanAction)
            }
        } else if (node.type == NodeType.KVNodeNode) {
            scanTree(node.kvNodeGetChildNode(), k.concat(node.kvNodeGetKey()), scanAction)
        } else {
            scanAction.doOnValue(node.hash, node, k.concat(node.kvNodeGetKey()).toNormal(), node.kvNodeGetValue())
        }
    }

    companion object {
        private val NULL_NODE = Any()
        private const val MIN_BRANCHES_CONCURRENTLY = 3

        private val logger = LoggerFactory.getLogger("state")
        private fun hash2str(hash: ByteArray, shortHash: Boolean): String {
            val ret: String = Numeric.toHexStringNoPrefix(hash)
            return "0x" + if (shortHash) ret.substring(0, 8) else ret
        }

        private fun val2str(`val`: ByteArray?, shortHash: Boolean): String {
            var ret: String = Numeric.toHexStringNoPrefix(`val`)
            if (`val`!!.size > 16) {
                ret = ret.substring(0, 10) + "... len " + `val`.size
            }
            return "\"" + ret + "\""
        }
    }


}