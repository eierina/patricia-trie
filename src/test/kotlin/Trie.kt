import new.*
import org.junit.Assert.*
import org.junit.Test
import org.web3j.crypto.Hash
import org.web3j.rlp.RlpEncoder
import org.web3j.rlp.RlpString
import org.web3j.utils.Numeric

class TrieTests {

    @Test
    fun testEmptyNodeHashCalculation() {
        val emptyNode = EmptyNode()
        val expectedHash = "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"

        assertEquals(expectedHash, Numeric.toHexString(emptyNode.hash))
    }

    @Test
    fun testEmptyLeafNodeHashCalculation() {
        val leaf = LeafNode.createFromBytes(byteArrayOf(), byteArrayOf())
        val expectedHash = "0xf9be828fd675253c2e3ecdff4379debab459f376b7554fac193747c676f10f0a"

        assertEquals(expectedHash, Numeric.toHexString(leaf.hash))
    }

    @Test
    fun testDummyLeafNodeHashCalculation() {
        val leaf = LeafNode.createFromBytes(byteArrayOf(1, 2, 3, 4), "leaf".toByteArray())
        val expectedHash = "0x2fc0c91eb10b756afb03c8ceafc121c9c2f4eb47b6ef974ba808f8b46067a6d0"

        assertEquals(expectedHash, Numeric.toHexString(leaf.hash))
    }

    @Test
    fun testEmptyBranchNodeHashCalculation() {
        val branch = BranchNode.create()
        val expectedHash = "0xbe0f4440e293a47160b9b148d49212d0616ec5b0a70c99de9bf36515d52e0901"

        assertEquals(expectedHash, Numeric.toHexString(branch.hash))
    }

    @Test
    fun testEmptyExtensionNodeHashCalculation() {
        val branch = ExtensionNode.createFromNibbles(byteArrayOf(), EmptyNode())
        val expectedHash = "0x9650f4d88ca5e8628148a9c9ced5c6063f7419b93a0bf854258890ddea8f233d"

        assertEquals(expectedHash, Numeric.toHexString(branch.hash))
    }

    @Test
    fun testEmptyTrieRootNode() {
        val trie = PatriciaTrie()
        val expectedHash = "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"

        assert(trie.root is EmptyNode)
        assertEquals(expectedHash, Numeric.toHexString(trie.root.hash))
    }

    @Test
    fun testTrieWithSingleLeafNode() {
        val trie = PatriciaTrie()
        val key = byteArrayOf(1, 2, 3, 4)
        val value = "leaf".toByteArray()
        val leaf = LeafNode.createFromBytes(key, value)
        val expectedHash = "0x2fc0c91eb10b756afb03c8ceafc121c9c2f4eb47b6ef974ba808f8b46067a6d0"

        trie.put(key, value)

        assertTrue(trie.root is LeafNode)
        assertArrayEquals(leaf.hash, trie.root.hash)
        assertEquals(expectedHash, Numeric.toHexString(trie.root.hash))
    }

    @Test
    fun testTrieWithTwoLeafNodesSecondShorter() {
        val trie = PatriciaTrie()
        trie.put(byteArrayOf(1, 2, 3, 4), "hello".toByteArray())
        trie.put(byteArrayOf(1, 2, 3), "world".toByteArray())

        val node = ExtensionNode.createFromNibbles(
            nibblesKey = byteArrayOf(0, 1, 0, 2, 0, 3),
            node = BranchNode.createWithBranch(
                nibbleKey = 0,
                node = LeafNode.createFromNibbles(byteArrayOf(4), "hello".toByteArray()),
                value = "world".toByteArray()
            )
        )

        assertArrayEquals(node.hash, trie.root.hash)
    }

    @Test
    fun testTrieWithTwoLeafNodesSecondLonger() {
        val trie = PatriciaTrie()
        trie.put(byteArrayOf(1, 2, 3, 4), "hello".toByteArray())
        trie.put(byteArrayOf(1, 2, 3, 4, 5, 6), "world".toByteArray())

        val node = ExtensionNode.createFromNibbles(
            nibblesKey = byteArrayOf(0, 1, 0, 2, 0, 3, 0, 4),
            node = BranchNode.createWithBranch(
                nibbleKey = 0,
                node = LeafNode.createFromNibbles(byteArrayOf(5, 0, 6), "world".toByteArray()),
                value = "hello".toByteArray()
            )
        )

        assertArrayEquals(node.hash, trie.root.hash)
    }

    @Test
    fun testTrieWithTwoLeafNodesSameLength() {
        val trie = PatriciaTrie()
        trie.put(byteArrayOf(1, 2, 3, 4), "hello".toByteArray())
        trie.put(byteArrayOf(1, 2, 3, 4), "world".toByteArray())

        val leaf = LeafNode.createFromNibbles(
            nibblesKey = byteArrayOf(0, 1, 0, 2, 0, 3, 0, 4),
            value = "world".toByteArray()
        )

        assertArrayEquals(leaf.hash, trie.root.hash)
    }

    @Test
    fun testTrieWithPrefixMatchingLeafNodesThirdMatchingLengthLong() {
        val trie = PatriciaTrie()
        trie.put(byteArrayOf(1, 2, 3, 4), "hello1".toByteArray())
        trie.put(byteArrayOf(1, 2, 3, 5), "hello2".toByteArray())
        trie.put(byteArrayOf(1, 2, 3), "world".toByteArray())

        val node = ExtensionNode.createFromNibbles(
            nibblesKey = byteArrayOf(0, 1, 0, 2, 0, 3),
            node = BranchNode.createWithBranch(
                nibbleKey = 0,
                node = BranchNode.createWithBranches(
                    Pair(4, LeafNode.createFromNibbles(byteArrayOf(), "hello1".toByteArray())),
                    Pair(5, LeafNode.createFromNibbles(byteArrayOf(), "hello2".toByteArray()))
                ),
                value = "world".toByteArray()
            )
        )

        assertArrayEquals(node.hash, trie.root.hash)
    }

    @Test
    fun testTrieWithPrefixMatchingLeafNodesThirdShorterPartiallyMatching() {
        val trie = PatriciaTrie()
        trie.put(byteArrayOf(1, 2, 3, 4), "hello1".toByteArray())
        trie.put(byteArrayOf(1, 2, 3, 5), "hello2".toByteArray())
        trie.put(byteArrayOf(1, 2, 5), "world".toByteArray())

        val node = ExtensionNode.createFromNibbles(
            nibblesKey = byteArrayOf(0, 1, 0, 2, 0),
            node = BranchNode.createWithBranches(
                Pair(3, ExtensionNode.createFromNibbles(
                        nibblesKey = byteArrayOf(0),
                        node = BranchNode.createWithBranches(
                            Pair(4, LeafNode.createFromNibbles(byteArrayOf(), "hello1".toByteArray())),
                            Pair(5, LeafNode.createFromNibbles(byteArrayOf(), "hello2".toByteArray()))
                        )
                    )
                ),
                Pair(5, LeafNode.createFromNibbles(
                        nibblesKey = byteArrayOf(),
                        value = "world".toByteArray()
                    )
                )
            )
        )

        assertArrayEquals(node.hash, trie.root.hash)
    }

    @Test
    fun testTrieWithPartiallyMatchingLeafNodesThirdNoMatch() {
        val trie = PatriciaTrie()
        trie.put(byteArrayOf(1, 2, 3, 4), "hello1".toByteArray())
        trie.put(byteArrayOf(1, 2, 3, 5), "hello2".toByteArray())
        trie.put(byteArrayOf(16, 2, 5), "world".toByteArray())

        val node = BranchNode.createWithBranches(
            Pair(0, ExtensionNode.createFromNibbles(
                    nibblesKey = byteArrayOf(1, 0, 2, 0, 3, 0),
                    node = BranchNode.createWithBranches(
                        Pair(4, LeafNode.createFromNibbles(
                                nibblesKey = byteArrayOf(),
                                value = "hello1".toByteArray()
                            )
                        ),
                        Pair(5, LeafNode.createFromNibbles(
                                nibblesKey = byteArrayOf(),
                                value = "hello2".toByteArray()
                            )
                        )
                    )
                )
            ),
            Pair(1, LeafNode.createFromNibbles(
                    nibblesKey = byteArrayOf(0, 0, 2, 0, 5),
                    value = "world".toByteArray()
                )
            )
        )

        assertArrayEquals(node.hash, trie.root.hash)
    }

    @Test
    fun testPatriciaTrieWithStaticTransactionData() {
        val trie = PatriciaTrie()
        trie.put(byteArrayOf(1, 2, 3, 4), "hello1".toByteArray())
        trie.put(byteArrayOf(1, 2, 3, 80), "hello2".toByteArray())
        trie.put(byteArrayOf(1, 2, 3), "world".toByteArray())

        val node = ExtensionNode.createFromNibbles(
            nibblesKey = byteArrayOf(0, 1, 0, 2, 0, 3),
            node = BranchNode.createWithBranches(
                Pair(0, LeafNode.createFromNibbles(
                        nibblesKey = byteArrayOf(4),
                        value = "hello1".toByteArray()
                    )
                ),
                Pair(5, LeafNode.createFromNibbles(
                        nibblesKey = byteArrayOf(0),
                        value = "hello2".toByteArray()
                    )
                ),
                value = "world".toByteArray()
            )
        )

        assertArrayEquals(node.hash, trie.root.hash)
    }

    @Test
    fun `should handle patricia trie with static data from existing transaction correctly`() {

        // Source TX data: https://etherscan.io/block/10593417

        val p1 = Pair(
            Numeric.toHexString(RlpEncoder.encode(RlpString.create(0L))),
            "0xf8ab81a5852e90edd00083012bc294a3bed4e1c75d00fa6f4e5e6922db7261b5e9acd280b844a9059cbb0000000000000000000000008bda8b9823b8490e8cf220dc7b91d97da1c54e250000000000000000000000000000000000000000000000056bc75e2d6310000026a06c89b57113cf7da8aed7911310e03d49be5e40de0bd73af4c9c54726c478691ba056223f039fab98d47c71f84190cf285ce8fc7d9181d6769387e5efd0a970e2e9"
        )
        val p2 = Pair(
            Numeric.toHexString(RlpEncoder.encode(RlpString.create(1L))),
            "0xf8ab81a6852e90edd00083012bc294a3bed4e1c75d00fa6f4e5e6922db7261b5e9acd280b844a9059cbb0000000000000000000000008bda8b9823b8490e8cf220dc7b91d97da1c54e250000000000000000000000000000000000000000000000056bc75e2d6310000026a0d77c66153a661ecc986611dffda129e14528435ed3fd244c3afb0d434e9fd1c1a05ab202908bf6cbc9f57c595e6ef3229bce80a15cdf67487873e57cc7f5ad7c8a"
        )
        val p3 = Pair(
            Numeric.toHexString(RlpEncoder.encode(RlpString.create(2L))),
            "0xf86d8229f185199c82cc008252089488e9a2d38e66057e18545ce03b3ae9ce4fc360538702ce7de1537c008025a096e7a1d9683b205f697b4073a3e2f0d0ad42e708f03e899c61ed6a894a7f916aa05da238fbb96d41a4b5ec0338c86cfcb627d0aa8e556f21528e62f31c32f7e672"
        )
        val p4 = Pair(
            Numeric.toHexString(RlpEncoder.encode(RlpString.create(3L))),
            "0xf86f826b2585199c82cc0083015f9094e955ede0a3dbf651e2891356ecd0509c1edb8d9c8801051fdc4efdc0008025a02190f26e70a82d7f66354a13cda79b6af1aa808db768a787aeb348d425d7d0b3a06a82bd0518bc9b69dc551e20d772a1b06222edfc5d39b6973e4f4dc46ed8b196"
        )

        assertEquals("0xb0c43213c86c2cacce8ceef965b881529d31e5be93ad6cefcef2f319a20ef1b5", Hash.sha3(p1.second))
        assertEquals("0x5bbbf64bd0f08465acbe30adb2be807488c3847c94a7dfabaffa3e25ab3a604a", Hash.sha3(p2.second))
        assertEquals("0x7d965a103dbb8e2027682e45bd371cf92bb9e15b84d5b2fa0dfa45333879ed12", Hash.sha3(p3.second))
        assertEquals("0x0b41fc4c1d8518cdeda9812269477256bdc415eb39c4531885ff9728d6ad096b", Hash.sha3(p4.second))

        val trie = PatriciaTrie()
        trie.put(Numeric.hexStringToByteArray(p1.first), Numeric.hexStringToByteArray(p1.second))
        trie.put(Numeric.hexStringToByteArray(p2.first), Numeric.hexStringToByteArray(p2.second))
        trie.put(Numeric.hexStringToByteArray(p3.first), Numeric.hexStringToByteArray(p3.second))
        trie.put(Numeric.hexStringToByteArray(p4.first), Numeric.hexStringToByteArray(p4.second))

        val treeRoot = Numeric.toHexString(trie.root.hash)
        assertEquals("0xab41f886be23cd786d8a69a72b0f988ea72e0b2e03970d0798f5e03763a442cc", treeRoot)
    }
}
