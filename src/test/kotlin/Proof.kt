import com.r3.corda.evmbridge.evm.rlp.RLP
import new.*
import org.junit.Assert.*
import org.junit.Test
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric

class ProofTests {

    @Test
    fun testPatriciaTrieStaticProof() {

        // Source TX data: https://etherscan.io/block/10593417

        val p1 = Pair(Numeric.toHexString(RLP.encodeInt(0)), "0xf8ab81a5852e90edd00083012bc294a3bed4e1c75d00fa6f4e5e6922db7261b5e9acd280b844a9059cbb0000000000000000000000008bda8b9823b8490e8cf220dc7b91d97da1c54e250000000000000000000000000000000000000000000000056bc75e2d6310000026a06c89b57113cf7da8aed7911310e03d49be5e40de0bd73af4c9c54726c478691ba056223f039fab98d47c71f84190cf285ce8fc7d9181d6769387e5efd0a970e2e9")
        val p2 = Pair(Numeric.toHexString(RLP.encodeInt(1)), "0xf8ab81a6852e90edd00083012bc294a3bed4e1c75d00fa6f4e5e6922db7261b5e9acd280b844a9059cbb0000000000000000000000008bda8b9823b8490e8cf220dc7b91d97da1c54e250000000000000000000000000000000000000000000000056bc75e2d6310000026a0d77c66153a661ecc986611dffda129e14528435ed3fd244c3afb0d434e9fd1c1a05ab202908bf6cbc9f57c595e6ef3229bce80a15cdf67487873e57cc7f5ad7c8a")
        val p3 = Pair(Numeric.toHexString(RLP.encodeInt(2)), "0xf86d8229f185199c82cc008252089488e9a2d38e66057e18545ce03b3ae9ce4fc360538702ce7de1537c008025a096e7a1d9683b205f697b4073a3e2f0d0ad42e708f03e899c61ed6a894a7f916aa05da238fbb96d41a4b5ec0338c86cfcb627d0aa8e556f21528e62f31c32f7e672")
        val p4 = Pair(Numeric.toHexString(RLP.encodeInt(3)), "0xf86f826b2585199c82cc0083015f9094e955ede0a3dbf651e2891356ecd0509c1edb8d9c8801051fdc4efdc0008025a02190f26e70a82d7f66354a13cda79b6af1aa808db768a787aeb348d425d7d0b3a06a82bd0518bc9b69dc551e20d772a1b06222edfc5d39b6973e4f4dc46ed8b196")

        assertEquals("0xb0c43213c86c2cacce8ceef965b881529d31e5be93ad6cefcef2f319a20ef1b5", Hash.sha3(p1.second))
        assertEquals("0x5bbbf64bd0f08465acbe30adb2be807488c3847c94a7dfabaffa3e25ab3a604a", Hash.sha3(p2.second))
        assertEquals("0x7d965a103dbb8e2027682e45bd371cf92bb9e15b84d5b2fa0dfa45333879ed12", Hash.sha3(p3.second))
        assertEquals("0x0b41fc4c1d8518cdeda9812269477256bdc415eb39c4531885ff9728d6ad096b", Hash.sha3(p4.second))

        val trie = PatriciaTrie()
        trie.put(Numeric.hexStringToByteArray(p1.first), Numeric.hexStringToByteArray(p1.second))
        trie.put(Numeric.hexStringToByteArray(p2.first), Numeric.hexStringToByteArray(p2.second))
        trie.put(Numeric.hexStringToByteArray(p3.first), Numeric.hexStringToByteArray(p3.second))
        trie.put(Numeric.hexStringToByteArray(p4.first), Numeric.hexStringToByteArray(p4.second))

        val proof = trie.generateMerkleProof(Numeric.hexStringToByteArray(p4.first))
        assert(!proof.isEmpty())

        val valid = PatriciaTrie.verifyMerkleProof(
            rootHash = trie.root.hash,
            key = Numeric.hexStringToByteArray(p4.first),
            expectedValue = Numeric.hexStringToByteArray(p4.second),
            proof = proof
        )

        assertTrue(valid)
    }

    @Test
    fun `test trie with one leaf node and a second shorter leaf node proofs`() {
        val pairs = listOf<Pair<ByteArray,ByteArray>>(
            Pair(byteArrayOf(1, 2, 3, 4), "hello".toByteArray()),
            Pair(byteArrayOf(1, 2, 3), "world".toByteArray())
        )

        val trie = PatriciaTrie()
        pairs.forEach{
            trie.put(it.first, it.second)
        }

        pairs.forEach {

            val proof = trie.generateMerkleProof(it.first)

            val verified = PatriciaTrie.verifyMerkleProof(
                rootHash = trie.root.hash,
                key = it.first,
                expectedValue = it.second,
                proof = proof
            )

            assertTrue(verified)
        }
    }

    @Test
    fun `test trie with one leaf node and a second longer leaf node proofs`() {
        val pairs = listOf<Pair<ByteArray,ByteArray>>(
            Pair(byteArrayOf(1, 2, 3, 4), "hello".toByteArray()),
            Pair(byteArrayOf(1, 2, 3, 4, 5, 6), "world".toByteArray())
        )

        val trie = PatriciaTrie()
        pairs.forEach{
            trie.put(it.first, it.second)
        }

        pairs.forEach {
            val proof = trie.generateMerkleProof(it.first)

            val verified = PatriciaTrie.verifyMerkleProof(
                rootHash = trie.root.hash,
                key = it.first,
                expectedValue = it.second,
                proof = proof
            )

            assertTrue(verified)
        }
    }


//
//    @Test
//    fun `test trie with one leaf node and a second same length leaf node`() {
//        val trie = PatriciaTrie()
//        trie.put(byteArrayOf(1, 2, 3, 4), "hello".toByteArray())
//        trie.put(byteArrayOf(1, 2, 3, 4), "world".toByteArray())
//
//        val leaf = LeafNode.createFromNibbles(
//            nibblesKey = byteArrayOf(0, 1, 0, 2, 0, 3, 0, 4),
//            value = "world".toByteArray()
//        )
//
//        assertArrayEquals(leaf.hash, trie.root.hash)
//    }
//
//    @Test
//    fun `test trie with prefix matching leaf nodes - first two same length, third matching-length long`() {
//        val trie = PatriciaTrie()
//        trie.put(byteArrayOf(1, 2, 3, 4), "hello1".toByteArray())
//        trie.put(byteArrayOf(1, 2, 3, 5), "hello2".toByteArray())
//        trie.put(byteArrayOf(1, 2, 3), "world".toByteArray())
//
//        val node = ExtensionNode.createFromNibbles(
//            nibblesKey = byteArrayOf(0, 1, 0, 2, 0, 3),
//            node = BranchNode.createWithBranch(
//                nibbleKey = 0,
//                node = BranchNode.createWithBranches(
//                    Pair(4, LeafNode.createFromNibbles(byteArrayOf(), "hello1".toByteArray())),
//                    Pair(5, LeafNode.createFromNibbles(byteArrayOf(), "hello2".toByteArray()))
//                ),
//                value = "world".toByteArray()
//            )
//        )
//
//        assertArrayEquals(node.hash, trie.root.hash)
//    }
//
//    @Test
//    fun `test trie with prefix matching leaf nodes - first two same length, third shorter and partially matching`() {
//        val trie = PatriciaTrie()
//        trie.put(byteArrayOf(1, 2, 3, 4), "hello1".toByteArray())
//        trie.put(byteArrayOf(1, 2, 3, 5), "hello2".toByteArray())
//        trie.put(byteArrayOf(1, 2, 5), "world".toByteArray())
//
//        val node = ExtensionNode.createFromNibbles(
//            nibblesKey = byteArrayOf(0, 1, 0, 2, 0),
//            node = BranchNode.createWithBranches(
//                Pair(
//                    3, ExtensionNode.createFromNibbles(
//                        byteArrayOf(0),
//                        BranchNode.createWithBranches(
//                            Pair(4, LeafNode.createFromNibbles(byteArrayOf(), "hello1".toByteArray())),
//                            Pair(5, LeafNode.createFromNibbles(byteArrayOf(), "hello2".toByteArray()))
//                        )
//                    )
//                ),
//                Pair(
//                    5, LeafNode.createFromNibbles(
//                        nibblesKey = byteArrayOf(),
//                        value = "world".toByteArray())
//                )
//            )
//        )
//
//        assertArrayEquals(node.hash, trie.root.hash)
//    }
//
//    @Test
//    fun `test trie with leaf nodes - first two same length partially matching, third shorter and no matching`() {
//        val trie = PatriciaTrie()
//        trie.put(byteArrayOf(1, 2, 3, 4), "hello1".toByteArray())
//        trie.put(byteArrayOf(1, 2, 3, 5), "hello2".toByteArray())
//        trie.put(byteArrayOf(16, 2, 5), "world".toByteArray())
//
//        val node = BranchNode.createWithBranches(
//            Pair(
//                0,
//                ExtensionNode.createFromNibbles(
//                    byteArrayOf(1, 0, 2, 0, 3, 0),
//                    BranchNode.createWithBranches(
//                        Pair(
//                            4,
//                            LeafNode.createFromNibbles(byteArrayOf(), "hello1".toByteArray())
//                        ),
//                        Pair(
//                            5,
//                            LeafNode.createFromNibbles(byteArrayOf(), "hello2".toByteArray())
//                        )
//                    )
//                )
//            ),
//            Pair(
//                1,
//                LeafNode.createFromNibbles(byteArrayOf(0, 0, 2, 0, 5), "world".toByteArray())
//            )
//        )
//
//        assertArrayEquals(node.hash, trie.root.hash)
//    }
//
//    @Test
//    fun testPutExtensionFullyMatching() {
//        val trie = PatriciaTrie()
//        trie.put(byteArrayOf(1, 2, 3, 4), "hello1".toByteArray())
//        trie.put(byteArrayOf(1, 2, 3, 80), "hello2".toByteArray())
//        trie.put(byteArrayOf(1, 2, 3), "world".toByteArray())
//
//        val node = ExtensionNode.createFromNibbles(
//            nibblesKey = byteArrayOf(0, 1, 0, 2, 0, 3),
//            node = BranchNode.createWithBranches(
//                Pair(0, LeafNode.createFromNibbles(
//                    nibblesKey = byteArrayOf(4),
//                    value = "hello1".toByteArray())
//                ),
//                Pair(5, LeafNode.createFromNibbles(
//                    nibblesKey = byteArrayOf(0),
//                    value = "hello2".toByteArray())
//                ),
//                value = "world".toByteArray())
//        )
//
//        assertArrayEquals(node.hash, trie.root.hash)
//    }
}
