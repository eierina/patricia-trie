package com.r3.corda.evmbridge.evm.trie

import com.r3.corda.evmbridge.evm.datasource.Source

interface Trie<V> : Source<ByteArray?, V> {
    val rootHash: ByteArray?
    fun setRoot(root: ByteArray?)

    /**
     * Recursively delete all nodes from root
     */
    fun clear()
}
