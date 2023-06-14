package com.r3.corda.evmbridge.evm.datasource

object Serializers {

    class Identity<T> : Serializer<T, T> {
        override fun serialize(`object`: T): T = `object`
        override fun deserialize(stream: T): T = stream
    }

//    val AccountStateSerializer: Serializer<AccountState, ByteArray> = object : Serializer<AccountState, ByteArray> {
//        override fun serialize(`object`: AccountState): ByteArray = `object`.encoded
//        override fun deserialize(stream: ByteArray?): AccountState? = if (stream == null || stream.isEmpty()) null else AccountState(stream)
//    }
//
//    val StorageKeySerializer: Serializer<DataWord, ByteArray> = object : Serializer<DataWord, ByteArray> {
//        override fun serialize(`object`: DataWord): ByteArray = `object`.data
//        override fun deserialize(stream: ByteArray?): DataWord? = DataWord.of(stream)
//    }
//
//    val StorageValueSerializer: Serializer<DataWord, ByteArray> = object : Serializer<DataWord, ByteArray> {
//        override fun serialize(`object`: DataWord): ByteArray = RLP.encodeElement(`object`.noLeadZeroesData)
//        override fun deserialize(stream: ByteArray?): DataWord? {
//            if (stream == null || stream.isEmpty()) return null
//            val dataDecoded = RLP.decode2(stream)[0].rlpData
//            return DataWord.of(dataDecoded)
//        }
//    }
//
//    val TrieNodeSerializer: Serializer<Value, ByteArray> = object : Serializer<Value, ByteArray> {
//        override fun serialize(`object`: Value): ByteArray = `object`.asBytes()
//        override fun deserialize(stream: ByteArray?): Value = Value(stream)
//    }
//
//    val BlockHeaderSerializer: Serializer<BlockHeader, ByteArray> = object : Serializer<BlockHeader, ByteArray> {
//        override fun serialize(`object`: BlockHeader?): ByteArray? = `object`?.encoded
//        override fun deserialize(stream: ByteArray?): BlockHeader? = if (stream == null) null else BlockHeader(stream)
//    }
//
//    val AsIsSerializer: Serializer<ByteArray, ByteArray> = object : Serializer<ByteArray, ByteArray> {
//        override fun serialize(`object`: ByteArray): ByteArray = `object`
//        override fun deserialize(stream: ByteArray?): ByteArray? = stream
//    }
}