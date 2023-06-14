package com.r3.corda.evmbridge.evm.datasource

open class SourceCodec<Key, Value, SourceKey, SourceValue>(
    src: Source<SourceKey, SourceValue>,
    private val keySerializer: Serializer<Key, SourceKey>,
    private val valSerializer: Serializer<Value, SourceValue>
) : AbstractChainedSource<Key, Value, SourceKey, SourceValue>(src) {

    init {
        setFlushSource(true)
    }

    override fun put(key: Key, value: Value) {
        getSource()!!.put(keySerializer.serialize(key), valSerializer.serialize(value)) // TODO: getSource() nullable
    }

    override fun get(key: Key): Value {
        return valSerializer.deserialize(getSource()!!.get(keySerializer.serialize(key))) // TODO: getSource() nullable
    }

    override fun delete(key: Key) {
        getSource()!!.delete(keySerializer.serialize(key)) // TODO: getSource() nullable
    }

    override fun flushImpl(): Boolean {
        return false
    }

    open class ValueOnly<Key, Value, SourceValue>(
        src: Source<Key, SourceValue>,
        valSerializer: Serializer<Value, SourceValue>
    ) : SourceCodec<Key, Value, Key, SourceValue>(src, Serializers.Identity<Key>(), valSerializer)

    class KeyOnly<Key, Value, SourceKey>(
        src: Source<SourceKey, Value>,
        keySerializer: Serializer<Key, SourceKey>
    ) : SourceCodec<Key, Value, SourceKey, Value>(src, keySerializer, Serializers.Identity<Value>())

    class BytesKey<Value, SourceValue>(
        src: Source<ByteArray, SourceValue>,
        valSerializer: Serializer<Value, SourceValue>
    ) : ValueOnly<ByteArray, Value, SourceValue>(src, valSerializer)
}