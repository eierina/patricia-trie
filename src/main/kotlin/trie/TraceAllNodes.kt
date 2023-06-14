package com.r3.corda.evmbridge.evm.trie

import org.web3j.utils.Numeric

class TraceAllNodes : TrieImpl.ScanAction {
    var output = StringBuilder()

    override fun doOnNode(hash: ByteArray?, node: TrieImpl.Node?) {
        output.append(Numeric.toHexStringNoPrefix(hash)).append(" ==> ").append(node.toString()).append("\n")
    }

    override fun doOnValue(nodeHash: ByteArray?, node: TrieImpl.Node?, key: ByteArray?, value: ByteArray?) {}
    fun getOutput(): String {
        return output.toString()
    }
}
