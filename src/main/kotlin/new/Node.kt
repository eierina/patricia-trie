package new

import org.web3j.crypto.Hash.sha3
import org.web3j.rlp.RlpDecoder
import org.web3j.rlp.RlpEncoder
import org.web3j.rlp.RlpList
import org.web3j.rlp.RlpString

class PatriciaTrie() {

    var root: Node = EmptyNode()

    fun put(key: ByteArray, value: ByteArray) {
        root = put(root, key.toNibbles(), value)
    }

    private fun put(node: Node, nibblesKey: ByteArray, value: ByteArray): Node {
        if (node is EmptyNode) {
            return LeafNode.createFromNibbles(nibblesKey, value)
        }

        if (node is LeafNode) {
            val matchingLength = node.path.prefixMatchingLength(nibblesKey)

            if(matchingLength == node.path.size && matchingLength == nibblesKey.size) {
                return LeafNode.createFromNibbles(nibblesKey, value)
            }

            val branchNode = if(matchingLength == node.path.size) {
                BranchNode.createWithValue(node.value)
            } else if(matchingLength == nibblesKey.size) {
                BranchNode.createWithValue(value)
            } else {
                BranchNode.create()
            }

            val extOrBranchNode = if(matchingLength > 0) {
                ExtensionNode.createFromNibbles(node.path.copyOfRange(0, matchingLength), branchNode)
            } else {
                branchNode
            }

            if(matchingLength < node.path.size) {
                branchNode.setBranch(
                    node.path[matchingLength],
                    LeafNode.createFromNibbles(
                        node.path.copyOfRange(matchingLength + 1, node.path.size),
                        node.value
                    )
                )
            }

            if(matchingLength < nibblesKey.size) {
                branchNode.setBranch(
                    nibblesKey[matchingLength],
                    LeafNode.createFromNibbles(
                        nibblesKey.copyOfRange(matchingLength + 1, node.path.size),
                        value
                    )
                )
            }

            return extOrBranchNode
        }

        if(node is BranchNode) {
            if (nibblesKey.isNotEmpty()) {
                val branch = nibblesKey[0].toInt()
                node.branches[branch] = put(
                    node.branches[branch],
                    nibblesKey.copyOfRange(1, nibblesKey.size),
                    value
                )
            } else {
                node.setValue(value)
            }
            return node
        }

        if(node is ExtensionNode) {
            val matchingLength = node.path.prefixMatchingLength(nibblesKey)
            if(matchingLength < node.path.size) {
                val extNibbles = node.path.copyOfRange(0, matchingLength)
                val branchNibble = node.path[matchingLength]
                val extRemainingNibbles = node.path.copyOfRange(matchingLength + 1, node.path.size)

            }

            /*
                        matched := PrefixMatchedLen(ext.Path, nibbles)
                        if matched < len(ext.Path) {
                            // E 01020304
                            // + 010203 good
                            extNibbles, branchNibble, extRemainingnibbles := ext.Path[:matched], ext.Path[matched], ext.Path[matched+1:]
                            branch := NewBranchNode()
                            if len(extRemainingnibbles) == 0 {
                                // E 0102030
                                // + 010203 good
                                branch.SetBranch(branchNibble, ext.Next)
                            } else {
                                // E 01020304
                                // + 010203 good
                                newExt := NewExtensionNode(extRemainingnibbles, ext.Next)
                                branch.SetBranch(branchNibble, newExt)
                            }

                            if matched < len(nibbles) {
                                nodeBranchNibble, nodeLeafNibbles := nibbles[matched], nibbles[matched+1:]
                                remainingLeaf := NewLeafNodeFromNibbles(nodeLeafNibbles, value)
                                branch.SetBranch(nodeBranchNibble, remainingLeaf)
                            } else if matched == len(nibbles) {
                                branch.SetValue(value)
                            } else {
                                panic(fmt.Sprintf("too many matched (%v > %v)", matched, len(nibbles)))
                            }

                            // if there is no shared extension nibbles any more, then we don't need the extension node
                            // any more
                            // E 01020304
                            // + 1234 good
                            if len(extNibbles) == 0 {
                                *node = branch
                            } else {
                                // otherwise create a new extension node
                                *node = NewExtensionNode(extNibbles, branch)
                            }
                            return
            * */
        }
    }
}

abstract class Node
{
    abstract val encoded: ByteArray

    open val hash: ByteArray
        get() = sha3(encoded)
}

class EmptyNode : Node()
{
    override val encoded: ByteArray
        get() = RlpEncoder.encode(RlpString.create(ByteArray(0)))

}

class LeafNode private constructor(
    val path: ByteArray, // NOTE: this is stored as a nibbles array
    val value: ByteArray
): Node() {
    override val encoded: ByteArray
        get() {
            return RlpEncoder.encode(
                RlpList(
                    RlpString.create(prefixedNibbles(path).fromPrefixedNibblesToBytes()),
                    RlpString.create(value)
                )
            )
        }

    private fun prefixedNibbles(nibbles: ByteArray): ByteArray {
        return (if (nibbles.size % 2 > 0) byteArrayOf(3) else byteArrayOf(2, 0)).plus(nibbles)
    }

    companion object {
        fun createFromBytes(key: ByteArray, value: ByteArray): LeafNode {
            require(key.isNotEmpty()) { "Invalid empty key" }
            require(value.isNotEmpty()) { "Invalid empty value" }

            return LeafNode(key.toNibbles(), value)
        }

        fun createFromNibbles(key: ByteArray, value: ByteArray): LeafNode {
            require(key.isNotEmpty()) { "Invalid empty key" }
            require(value.isNotEmpty()) { "Invalid empty value" }

            return LeafNode(key, value)
        }
    }
}

class BranchNode private constructor(
    val branches: Array<Node>,
    var value: ByteArray
): Node() {
    override val encoded: ByteArray
        get() {
            return RlpEncoder.encode(RlpList(branches.map { node ->
                val encodedNode = node.encoded
                when {
                    node is EmptyNode -> RlpString.create(ByteArray(0))
                    // NOTE: in the next line, if node can be a HashNode then we need to call node.hash
                    // otherwise we can optimize and call Hash.sha3(encodedNode)
                    encodedNode.size >= 32 -> RlpString.create(node.hash)
                    else -> RlpString.create(encodedNode)
                }
            }.plus(RlpString.create(value))))
        }

    fun setBranch(nibbleKey: Byte, node: Node) {
        branches[nibbleKey.toInt()] = node
    }

    fun setValue(value: ByteArray) {
        this.value = value
    }

    companion object {
        @JvmStatic
        private val emptyNode = EmptyNode()

        fun create(): BranchNode {
            return BranchNode(Array(16){ emptyNode }, ByteArray(0))
        }

        fun createWithValue(value: ByteArray): BranchNode {
            return BranchNode(Array(16){ emptyNode }, value)
        }
    }
}

class ExtensionNode private constructor(
    val path: ByteArray,
    val innerNode: Node
): Node() {
    override val encoded: ByteArray
        get() {
            val encodedInnerNode = innerNode.encoded
            return RlpEncoder.encode(
                RlpList(
                    RlpString.create(prefixedNibbles(path).fromPrefixedNibblesToBytes()),
                    if(encodedInnerNode.size >= 32) {
                        RlpString.create(innerNode.hash)
                    } else {
                        RlpDecoder.decode(encodedInnerNode) // TODO: review
                    }
                )
            )
        }

    private fun prefixedNibbles(nibbles: ByteArray): ByteArray {
        return (if (nibbles.size % 2 > 0) byteArrayOf(1) else byteArrayOf(0, 0)).plus(nibbles)
    }

    companion object {
        fun createFromBytes(key: ByteArray, node: Node): ExtensionNode {
            require(key.isNotEmpty()) { "Invalid empty key" }

            return ExtensionNode(key.toNibbles(), node)
        }

        fun createFromNibbles(key: ByteArray, node: Node): ExtensionNode {
            require(key.isNotEmpty()) { "Invalid empty key" }

            return ExtensionNode(key, node)
        }
    }
}

fun ByteArray.prefixMatchingLength(other: ByteArray): Int {
    return this.zip(other).takeWhile { (n1, n2) -> n1 == n2 }.count()
}

fun ByteArray.toNibbles(): ByteArray {
    val result = ByteArray(this.size * 2)
    for (i in this.indices) {
        result[i * 2] = ((this[i].toInt() shr 4) and 0x0F).toByte()
        result[i * 2 + 1] = (this[i].toInt() and 0x0F).toByte()
    }
    return result
}

fun ByteArray.fromPrefixedNibblesToBytes(): ByteArray {
    val result = ByteArray(this.size / 2)
    for (i in this.indices step 2) {
        result[i / 2] = ((this[i].toInt() shl 4) or (this[i + 1].toInt())).toByte()
    }
    return result
}

