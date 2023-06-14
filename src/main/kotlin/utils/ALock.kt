package com.r3.corda.evmbridge.evm.utils

import java.util.concurrent.locks.Lock

class ALock(private val lock: Lock) : AutoCloseable {
    fun lock(): ALock {
        lock.lock()
        return this
    }

    override fun close() {
        lock.unlock()
    }
}
