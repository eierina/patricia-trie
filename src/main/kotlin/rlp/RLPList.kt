package com.r3.corda.evmbridge.evm.rlp

import org.ethereum.util.ByteUtil.toHexString
import java.util.*

class RLPList : ArrayList<RLPElement?>(), RLPElement {
    override lateinit var rLPData: ByteArray

    companion object {
        fun recursivePrint(element: RLPElement?) {
            if (element == null) throw RuntimeException("RLPElement object can't be null")
            if (element is RLPList) {
                print("[")
                for (singleElement in element) recursivePrint(singleElement)
                print("]")
            } else {
                val hex = toHexString(element.rLPData)
                print("$hex, ")
            }
        }
    }
}
