package com.r3.corda.evmbridge.evm.trie

import com.r3.corda.evmbridge.evm.datasource.Source
import org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY
import org.web3j.crypto.Hash

class SecureTrie : TrieImpl {
    constructor(root: ByteArray?) : super(root)
    constructor(cache: Source<ByteArray?, ByteArray>) : super(cache, null)
    constructor(cache: Source<ByteArray?, ByteArray>, root: ByteArray?) : super(cache, root)

    override operator fun get(key: ByteArray?): ByteArray? {
        return super.get(Hash.sha3(key))
    }

    override fun put(key: ByteArray?, value: ByteArray?) {
        super.put(Hash.sha3(key), value)
    }

    override fun delete(key: ByteArray?) {
        put(key, EMPTY_BYTE_ARRAY)
    }
}
