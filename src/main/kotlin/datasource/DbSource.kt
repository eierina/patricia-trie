package com.r3.corda.evmbridge.evm.datasource

/**
 * Interface represents DB source which is normally the final Source in the chain
 */
interface DbSource<V> : BatchSource<ByteArray?, V> {
    /**
     * @return DB name
     */
    /**
     * Sets the DB name.
     * This could be the underlying DB table/dir name
     */
    var name: String?

    /**
     * Initializes DB (open table, connection, etc)
     * with default [DbSettings.DEFAULT]
     */
    fun init()

    /**
     * Initializes DB (open table, connection, etc)
     * @param settings  DB settings
     */
    fun init(settings: DbSettings?)

    /**
     * @return true if DB connection is alive
     */
    val isAlive: Boolean

    /**
     * Closes the DB table/connection
     */
    fun close()

    /**
     * @return DB keys if this option is available
     * @throws RuntimeException if the method is not supported
     */
    //TODO: @kotlin.Throws(RuntimeException::class)
    fun keys(): Set<ByteArray?>?

    /**
     * Closes database, destroys its data and finally runs init()
     */
    fun reset()

    /**
     * If supported, retrieves a value using a key prefix.
     * Prefix extraction is meant to be done on the implementing side.<br></br>
     *
     * @param key a key for the lookup
     * @param prefixBytes prefix length in bytes
     * @return first value picked by prefix lookup over DB or null if there is no match
     * @throws RuntimeException if operation is not supported
     */
    fun prefixLookup(key: ByteArray?, prefixBytes: Int): V?
}