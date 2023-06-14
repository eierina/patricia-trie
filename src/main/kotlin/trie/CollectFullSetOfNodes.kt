package com.r3.corda.evmbridge.evm.trie

import org.ethereum.db.ByteArrayWrapper

class CollectFullSetOfNodes : TrieImpl.ScanAction {
    var nodes: MutableSet<ByteArrayWrapper> = HashSet<ByteArrayWrapper>()
    override fun doOnNode(hash: ByteArray?, node: TrieImpl.Node?) {
        nodes.add(ByteArrayWrapper(hash!!))
    }

    override fun doOnValue(nodeHash: ByteArray?, node: TrieImpl.Node?, key: ByteArray?, value: ByteArray?) {}
    val collectedHashes: Set<Any>
        get() = nodes
}
