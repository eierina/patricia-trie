package trie

interface KeyValueStore {
    fun get(key: ByteArray): ByteArray?

    fun isEmpty(): Boolean
}