package org.ethereum.db

import org.ethereum.util.FastByteComparisons
import org.web3j.utils.Numeric
import java.io.Serializable
import java.util.*

class ByteArrayWrapper(data: ByteArray?) : Comparable<ByteArrayWrapper?>, Serializable {
    val data: ByteArray
    private var hashCode = 0

    init {
        if (data == null) throw NullPointerException("Data must not be null")
        this.data = data
        hashCode = Arrays.hashCode(data)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ByteArrayWrapper) return false
        val otherData = other.data
        return data.contentEquals(otherData)
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override operator fun compareTo(o: ByteArrayWrapper?): Int {
        return FastByteComparisons.compareTo(
            data, 0, data.size,
            //o.data, 0, o.data.size
            o!!.data, 0, o.data.size // TODO: nullable
        )

//        val minLength = minOf(data.size, o!!.data.size)
//
//        for (i in 0 until minLength) {
//            val comparison = data[i].compareTo(o.data[i])
//            if (comparison != 0) {
//                return comparison
//            }
//        }
//
//        return data.size.compareTo(o.data.size)
    }

    override fun toString(): String {
        return Numeric.toHexStringNoPrefix(data)
    }
}