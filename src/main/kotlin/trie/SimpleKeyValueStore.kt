package trie

import org.web3j.utils.Numeric
import java.util.HashMap

class SimpleKeyValueStore : KeyValueStore {

    data class ArrayKey(val data: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ArrayKey
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            return data.contentHashCode()
        }

        override fun toString(): String = Numeric.toHexString(data)
    }

    private val store: HashMap<ArrayKey, ByteArray> = HashMap()

    override fun get(key: ByteArray): ByteArray? {
        return store[ArrayKey(key)]
    }

    fun put(key: ByteArray, value: ByteArray) {
        //println("Key: ${Numeric.toHexString(key)}\nValue: ${Numeric.toHexString(value)}\n\n")
        store[ArrayKey(key)] = value
    }

    override fun isEmpty() = store.isEmpty()

    /**
     * Just some helper while debugging
     */
    override fun toString(): String {
        return store.entries.joinToString("\n") { (key, value) -> "(Key: ${key}, Value: ${Numeric.toHexString(value)}" }
    }
}