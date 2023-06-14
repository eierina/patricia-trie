package com.r3.corda.evmbridge.evm.datasource

interface BatchSource<K, V> : Source<K, V> {
    fun updateBatch(rows: Map<K, V>?)
}