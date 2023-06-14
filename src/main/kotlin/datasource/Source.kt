package com.r3.corda.evmbridge.evm.datasource

/**
 * Base interface for all data source classes
 *
 */
interface Source<K, V> {
    /**
     * Puts key-value pair into source
     */
    fun put(key: K, `val`: V)

    /**
     * Gets a value by its key
     * @return value or <null></null> if no such key in the source
     */
    operator fun get(key: K): V

    /**
     * Deletes the key-value pair from the source
     */
    fun delete(key: K)

    /**
     * If this source has underlying level source then all
     * changes collected in this source are flushed into the
     * underlying source.
     * The implementation may do 'cascading' flush, i.e. call
     * flush() on the underlying Source
     * @return true if any changes we flushed, false if the underlying
     * Source didn't change
     */
    fun flush(): Boolean
}

