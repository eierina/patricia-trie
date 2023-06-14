package com.r3.corda.evmbridge.evm.rlp

class RLPItem(private val rlpData: ByteArray) : RLPElement {
    override val rLPData: ByteArray?
        get() = if (rlpData.isEmpty()) null else rlpData
}
