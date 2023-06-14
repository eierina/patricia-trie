package com.r3.corda.evmbridge.evm.datasource

interface Serializer<T, S> {
    fun serialize(`object`: T): S
    fun deserialize(stream: S): T
}

