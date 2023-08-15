/*
 * Copyright 2023 Edoardo Ierina
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package trie

import org.web3j.rlp.RlpDecoder
import org.web3j.rlp.RlpEncoder
import org.web3j.rlp.RlpList
import org.web3j.rlp.RlpString

/**
 * Represents an ExtensionNode in a Patricia Trie.
 *
 * An ExtensionNode consists of a path and an inner node.
 * The encoded form is an RLP (Recursive Length Prefix) encoded list of the path and the inner node.
 *
 * @property path The path of the ExtensionNode, stored as a nibbles array.
 * @property innerNode The inner Node that the ExtensionNode points to.
 */
class ExtensionNode private constructor(
    val path: ByteArray,
    var innerNode: Node
) : Node() {

    /**
     * The RLP-encoded form of the ExtensionNode, which is an RLP-encoded list of the path and the inner node.
     */
    override val encoded: ByteArray
        get() {
            val encodedInnerNode = innerNode.encoded
            return RlpEncoder.encode(
                RlpList(
                    RlpString.create(prefixedNibbles(path).fromPrefixedNibblesToBytes()),
                    if (encodedInnerNode.size >= 32) {
                        RlpString.create(innerNode.hash)
                    } else {
                        RlpDecoder.decode(encodedInnerNode) // TODO: review
                    }
                )
            )
        }

    /**
     * Add prefix to the nibbles array depending on its size.
     *
     * @param nibbles The nibbles array to be prefixed.
     * @return The prefixed nibbles array.
     */
    private fun prefixedNibbles(nibbles: ByteArray): ByteArray {
        return (if (nibbles.size % 2 > 0) byteArrayOf(1) else byteArrayOf(0, 0)).plus(nibbles)
    }

    companion object {
        /**
         * Factory function to create an ExtensionNode from bytes.
         *
         * @param key The key to be used for the path of the ExtensionNode.
         * @param node The Node to be used as the inner node of the ExtensionNode.
         * @return The created ExtensionNode.
         */
        fun createFromBytes(key: ByteArray, node: Node): ExtensionNode {
            return ExtensionNode(key.toNibbles(), node)
        }

        /**
         * Factory function to create an ExtensionNode from nibbles.
         *
         * @param nibblesKey The key to be used for the path of the ExtensionNode, in the form of a nibbles array.
         * @param node The Node to be used as the inner node of the ExtensionNode.
         * @return The created ExtensionNode.
         */
        fun createFromNibbles(nibblesKey: ByteArray, node: Node): ExtensionNode {
            return ExtensionNode(nibblesKey, node)
        }
    }
}
