package com.r3.corda.evmbridge.evm.datasource.inmem

import com.r3.corda.evmbridge.evm.datasource.DbSource
import com.r3.corda.evmbridge.evm.utils.ALock
import com.r3.corda.evmbridge.evm.datasource.DbSettings
import com.r3.corda.evmbridge.evm.utils.ByteArrayMap
import org.ethereum.util.FastByteComparisons.compareTo
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

class HashMapDB<V> @JvmOverloads constructor(storage: ByteArrayMap<V> = ByteArrayMap<V>()) : DbSource<V> {
    private val storage: MutableMap<ByteArray, V>
    private var rwLock: ReadWriteLock = ReentrantReadWriteLock()
    private var readLock: ALock = ALock(rwLock.readLock())
    private var writeLock: ALock = ALock(rwLock.writeLock())

    init {
        this.storage = storage
    }

    override fun put(key: ByteArray?, `val`: V) {
        if (`val` == null) {
            delete(key)
        } else {
            writeLock.lock().use({ l -> storage.put(key!!, `val`) }) // TODO: nullable key
        }
    }

    override operator fun get(key: ByteArray?): V {
        readLock.lock().use({ l -> return storage[key]!! }) // TODO: nullable storage[key]
    }

    override fun delete(key: ByteArray?) {
        writeLock.lock().use({ l -> storage.remove(key) })
    }

    override fun flush(): Boolean {
        return true
    }

    override var name: String?
        get() = "in-memory"
        set(name) {}

    override fun init() {}
    override fun init(settings: DbSettings?) {}
    override val isAlive: Boolean
        get() = true

    override fun close() {}
    override fun keys(): Set<ByteArray> {
        readLock.lock().use({ l -> return getStorage().keys })
    }

    override fun reset() {
        writeLock.lock().use({ l -> storage.clear() })
    }

    override fun prefixLookup(key: ByteArray?, prefixBytes: Int): V? {
        readLock.lock().use({ l ->
            for ((key1, value) in storage) if (compareTo(
                    key!!, 0, prefixBytes, key1, 0, prefixBytes
                ) == 0
            ) {
                return value
            }
            return null
        })
    }

    override fun updateBatch(rows: Map<ByteArray?, V>?) {
        writeLock.lock().use({ l ->
            for ((key, value) in rows!!) { // TODO: nullable rows
                put(key, value)
            }
        })
    }

    fun getStorage(): Map<ByteArray, V> {
        return storage
    }
}

