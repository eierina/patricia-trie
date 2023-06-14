package com.r3.corda.evmbridge.evm.trie

import org.web3j.utils.Numeric

class TrieKey(private val key: ByteArray, private val off: Int, val isTerminal: Boolean) {

    private constructor(key: ByteArray) : this(key, 0, true)

    fun toPacked(): ByteArray {
        val flags = (if (off and 1 != 0) ODD_OFFSET_FLAG else 0) or if (isTerminal) TERMINATOR_FLAG else 0
        val ret = ByteArray(length / 2 + 1)
        val toCopy = if (flags and ODD_OFFSET_FLAG != 0) ret.size else ret.size - 1
        System.arraycopy(key, key.size - toCopy, ret, ret.size - toCopy, toCopy)
        ret[0] = (ret[0].toInt() and 0x0F).toByte()
        ret[0] = (ret[0].toInt() or (flags shl 4)).toByte()
        return ret
    }

    fun toNormal(): ByteArray {
        if (off and 1 != 0) throw RuntimeException("Can't convert a key with odd number of hexes to normal: $this")
        val arrLen = key.size - off / 2
        val ret = ByteArray(arrLen)
        System.arraycopy(key, key.size - arrLen, ret, 0, arrLen)
        return ret
    }

    val isEmpty: Boolean
        get() = length == 0

    fun shift(hexCnt: Int): TrieKey {
        return TrieKey(key, off + hexCnt, isTerminal)
    }

    fun getCommonPrefix(k: TrieKey?): TrieKey {
        // TODO can be optimized
        var prefixLen = 0
        val thisLength = length
        val kLength = k!!.length
        while (prefixLen < thisLength && prefixLen < kLength && getHex(prefixLen) == k.getHex(prefixLen)) prefixLen++
        val prefixKey = ByteArray(prefixLen + 1 shr 1)
        val ret = TrieKey(
            prefixKey, if (prefixLen and 1 == 0) 0 else 1,
            prefixLen == length && prefixLen == k.length && isTerminal && k.isTerminal
        )
        for (i in 0 until prefixLen) {
            ret.setHex(i, k.getHex(i))
        }
        return ret
    }

    fun matchAndShift(k: TrieKey?): TrieKey? {
        val len = length
        val kLen = k!!.length
        if (len < kLen) return null
        if (off and 1 == k.off and 1) {
            // optimization to compare whole keys bytes
            if (off and 1 == 1) {
                if (getHex(0) != k.getHex(0)) return null
            }
            var idx1 = off + 1 shr 1
            var idx2 = k.off + 1 shr 1
            val l = kLen shr 1
            var i = 0
            while (i < l) {
                if (key[idx1] != k.key[idx2]) return null
                i++
                idx1++
                idx2++
            }
        } else {
            for (i in 0 until kLen) {
                if (getHex(i) != k.getHex(i)) return null
            }
        }
        return shift(kLen)
    }

    val length: Int
        get() = (key.size shl 1) - off

    private fun setHex(idx: Int, hex: Int) {
        val byteIdx = off + idx shr 1
        if (off + idx and 1 == 0) {
            key[byteIdx] = (key[byteIdx].toInt() and 0x0F).toByte()
            key[byteIdx] = (key[byteIdx].toInt() or (hex shl 4)).toByte()
        } else {
            key[byteIdx] = (key[byteIdx].toInt() and 0xF0).toByte()
            key[byteIdx] = (key[byteIdx].toInt() or hex).toByte()
        }
    }

    fun getHex(idx: Int): Int {
        val b = key[(off + idx) shr 1].toInt()
        return (if (((off + idx) and 1) == 0) (b shr 4) else b) and 0xF
    }

    fun concat(k: TrieKey?): TrieKey {
        if (isTerminal) throw RuntimeException("Can' append to terminal key: $this + $k")
        val len = length
        val kLen = k!!.length
        val newLen = len + kLen
        val newKeyBytes = ByteArray(newLen + 1 shr 1)
        val ret = TrieKey(newKeyBytes, newLen and 1, k.isTerminal)
        for (i in 0 until len) {
            ret.setHex(i, getHex(i))
        }
        for (i in 0 until kLen) {
            ret.setHex(len + i, k.getHex(i))
        }
        return ret
    }

    override fun equals(obj: Any?): Boolean {
        val k = obj as TrieKey?
        val len = length
        if (len != k!!.length) return false
        // TODO can be optimized
        for (i in 0 until len) {
            if (getHex(i) != k.getHex(i)) return false
        }
        return isTerminal == k.isTerminal
    }

    override fun toString(): String {
        //return toHexString(key).substring(off) + if (isTerminal) "T" else ""
        return Numeric.toHexStringNoPrefix(key).substring(off) + if (isTerminal) "T" else ""
    }

    companion object {
        const val ODD_OFFSET_FLAG = 0x1
        const val TERMINATOR_FLAG = 0x2
        fun fromNormal(key: ByteArray): TrieKey {
            return TrieKey(key)
        }

        fun fromPacked(key: ByteArray): TrieKey {
            return TrieKey(
                key,
                if (key[0].toInt() shr 4 and ODD_OFFSET_FLAG != 0) 1 else 2,
                key[0].toInt() shr 4 and TERMINATOR_FLAG != 0
            )
        }

        fun empty(terminal: Boolean): TrieKey {
            //return TrieKey(EMPTY_BYTE_ARRAY, 0, terminal)
            return TrieKey(ByteArray(0), 0, terminal)
        }

        fun singleHex(hex: Int): TrieKey {
            val ret = TrieKey(ByteArray(1), 1, false)
            ret.setHex(0, hex)
            return ret
        }
    }
}
