package com.r3.corda.evmbridge.evm.rlp

import java.io.Serializable

interface RLPElement : Serializable {

    val rLPData: ByteArray?
}
