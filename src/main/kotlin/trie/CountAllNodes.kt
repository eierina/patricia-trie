package com.r3.corda.evmbridge.evm.trie

class CountAllNodes : TrieImpl.ScanAction {
    var counted = 0
    override fun doOnNode(hash: ByteArray?, node: TrieImpl.Node?) {
        ++counted
    }

    override fun doOnValue(nodeHash: ByteArray?, node: TrieImpl.Node?, key: ByteArray?, value: ByteArray?) {}
}
