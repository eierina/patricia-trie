package com.r3.corda.evmbridge.evm.crypto

import org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY
import org.web3j.crypto.Hash
import org.web3j.rlp.RlpEncoder
import org.web3j.rlp.RlpString

object HashUtil {
    val EMPTY_TRIE_HASH = Hash.sha3(RlpEncoder.encode(RlpString.create(EMPTY_BYTE_ARRAY)))
}