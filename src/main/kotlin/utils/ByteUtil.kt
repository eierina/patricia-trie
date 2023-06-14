/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ethereum.util

//import org.ethereum.db.ByteArrayWrapper
import org.ethereum.db.ByteArrayWrapper
import org.web3j.utils.Numeric
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.HashSet

object ByteUtil {
    val EMPTY_BYTE_ARRAY = ByteArray(0)
    val ZERO_BYTE_ARRAY = byteArrayOf(0)

    /**
     * Creates a copy of bytes and appends b to the end of it
     */
    fun appendByte(bytes: ByteArray, b: Byte): ByteArray {
        val result = Arrays.copyOf(bytes, bytes.size + 1)
        result[result.size - 1] = b
        return result
    }

    /**
     * The regular [java.math.BigInteger.toByteArray] method isn't quite what we often need:
     * it appends a leading zero to indicate that the number is positive and may need padding.
     *
     * @param b the integer to format into a byte array
     * @param numBytes the desired size of the resulting byte array
     * @return numBytes byte long array.
     */
    fun bigIntegerToBytes(b: BigInteger?, numBytes: Int): ByteArray? {
        if (b == null) return null
        val bytes = ByteArray(numBytes)
        val biBytes = b.toByteArray()
        val start = if (biBytes.size == numBytes + 1) 1 else 0
        val length = Math.min(biBytes.size, numBytes)
        System.arraycopy(biBytes, start, bytes, numBytes - length, length)
        return bytes
    }

    fun bigIntegerToBytesSigned(b: BigInteger?, numBytes: Int): ByteArray? {
        if (b == null) return null
        val bytes = ByteArray(numBytes)
        Arrays.fill(bytes, if (b.signum() < 0) 0xFF.toByte() else 0x00)
        val biBytes = b.toByteArray()
        val start = if (biBytes.size == numBytes + 1) 1 else 0
        val length = Math.min(biBytes.size, numBytes)
        System.arraycopy(biBytes, start, bytes, numBytes - length, length)
        return bytes
    }

    /**
     * Omitting sign indication byte.
     * <br></br><br></br>
     * Instead of [org.spongycastle.util.BigIntegers.asUnsignedByteArray]
     * <br></br>we use this custom method to avoid an empty array in case of BigInteger.ZERO
     *
     * @param value - any big integer number. A `null`-value will return `null`
     * @return A byte array without a leading zero byte if present in the signed encoding.
     * BigInteger.ZERO will return an array with length 1 and byte-value 0.
     */
    fun bigIntegerToBytes(value: BigInteger?): ByteArray? {
        if (value == null) return null
        var data = value.toByteArray()
        if (data.size != 1 && data[0].toInt() == 0) {
            val tmp = ByteArray(data.size - 1)
            System.arraycopy(data, 1, tmp, 0, tmp.size)
            data = tmp
        }
        return data
    }

    /**
     * Cast hex encoded value from byte[] to BigInteger
     * null is parsed like byte[0]
     *
     * @param bb byte array contains the values
     * @return unsigned positive BigInteger value.
     */
    fun bytesToBigInteger(bb: ByteArray?): BigInteger {
        return if (bb == null || bb.size == 0) BigInteger.ZERO else BigInteger(1, bb)
    }

    /**
     * Returns the amount of nibbles that match each other from 0 ...
     * amount will never be larger than smallest input
     *
     * @param a - first input
     * @param b - second input
     * @return Number of bytes that match
     */
    fun matchingNibbleLength(a: ByteArray, b: ByteArray): Int {
        var i = 0
        val length = if (a.size < b.size) a.size else b.size
        while (i < length) {
            if (a[i] != b[i]) return i
            i++
        }
        return i
    }

    /**
     * Converts a long value into a byte array.
     *
     * @param val - long value to convert
     * @return `byte[]` of length 8, representing the long value
     */
    fun longToBytes(`val`: Long): ByteArray {
        return ByteBuffer.allocate(java.lang.Long.BYTES).putLong(`val`).array()
    }

    /**
     * Converts a long value into a byte array.
     *
     * @param val - long value to convert
     * @return decimal value with leading byte that are zeroes striped
     */
    fun longToBytesNoLeadZeroes(`val`: Long): ByteArray? {

        // todo: improve performance by while strip numbers until (long >> 8 == 0)
        if (`val` == 0L) return EMPTY_BYTE_ARRAY
        val data = ByteBuffer.allocate(java.lang.Long.BYTES).putLong(`val`).array()
        return stripLeadingZeroes(data)
    }

    /**
     * Converts int value into a byte array.
     *
     * @param val - int value to convert
     * @return `byte[]` of length 4, representing the int value
     */
    fun intToBytes(`val`: Int): ByteArray {
        return ByteBuffer.allocate(Integer.BYTES).putInt(`val`).array()
    }

    /**
     * Converts a int value into a byte array.
     *
     * @param val - int value to convert
     * @return value with leading byte that are zeroes striped
     */
    fun intToBytesNoLeadZeroes(`val`: Int): ByteArray {
        var `val` = `val`
        if (`val` == 0) return EMPTY_BYTE_ARRAY
        var lenght = 0
        var tmpVal = `val`
        while (tmpVal != 0) {
            tmpVal = tmpVal ushr 8
            ++lenght
        }
        val result = ByteArray(lenght)
        var index = result.size - 1
        while (`val` != 0) {
            result[index] = (`val` and 0xFF).toByte()
            `val` = `val` ushr 8
            index -= 1
        }
        return result
    }

    /**
     * Convert a byte-array into a hex String.<br></br>
     * Works similar to [Hex.toHexString]
     * but allows for `null`
     *
     * @param data - byte-array to convert to a hex-string
     * @return hex representation of the data.<br></br>
     * Returns an empty String if the input is `null`
     *
     * @see Hex.toHexString
     */
    fun toHexString(data: ByteArray?): String {
        return if (data == null) "" else Numeric.toHexStringNoPrefix(data)
    }

    /**
     * Calculate packet length
     *
     * @param msg byte[]
     * @return byte-array with 4 elements
     */
    fun calcPacketLength(msg: ByteArray): ByteArray {
        val msgLen = msg.size
        return byteArrayOf(
            (msgLen shr 24 and 0xFF).toByte(),
            (msgLen shr 16 and 0xFF).toByte(),
            (msgLen shr 8 and 0xFF).toByte(),
            (msgLen and 0xFF).toByte()
        )
    }

    /**
     * Cast hex encoded value from byte[] to int
     * null is parsed like byte[0]
     *
     * Limited to Integer.MAX_VALUE: 2^32-1 (4 bytes)
     *
     * @param b array contains the values
     * @return unsigned positive int value.
     */
    fun byteArrayToInt(b: ByteArray?): Int {
        return if (b == null || b.size == 0) 0 else BigInteger(1, b).toInt()
    }

    /**
     * Cast hex encoded value from byte[] to long
     * null is parsed like byte[0]
     *
     * Limited to Long.MAX_VALUE: 2<sup>63</sup>-1 (8 bytes)
     *
     * @param b array contains the values
     * @return unsigned positive long value.
     */
    fun byteArrayToLong(b: ByteArray?): Long {
        return if (b == null || b.size == 0) 0 else BigInteger(1, b).toLong()
    }

    /**
     * Turn nibbles to a pretty looking output string
     *
     * Example. [ 1, 2, 3, 4, 5 ] becomes '\x11\x23\x45'
     *
     * @param nibbles - getting byte of data [ 04 ] and turning
     * it to a '\x04' representation
     * @return pretty string of nibbles
     */
    fun nibblesToPrettyString(nibbles: ByteArray): String {
        val builder = StringBuilder()
        for (nibble in nibbles) {
            val nibbleString = oneByteToHexString(nibble)
            builder.append("\\x").append(nibbleString)
        }
        return builder.toString()
    }

    fun oneByteToHexString(value: Byte): String {
        var retVal = Integer.toString(value.toInt() and 0xFF, 16)
        if (retVal.length == 1) retVal = "0$retVal"
        return retVal
    }

    /**
     * Calculate the number of bytes need
     * to encode the number
     *
     * @param val - number
     * @return number of min bytes used to encode the number
     */
    fun numBytes(`val`: String?): Int {
        var bInt = BigInteger(`val`)
        var bytes = 0
        while (bInt != BigInteger.ZERO) {
            bInt = bInt.shiftRight(8)
            ++bytes
        }
        if (bytes == 0) ++bytes
        return bytes
    }

    /**
     * @param arg - not more that 32 bits
     * @return - bytes of the value pad with complete to 32 zeroes
     */
    fun encodeValFor32Bits(arg: Any): ByteArray {
        val data: ByteArray

        // check if the string is numeric
        data = if (arg.toString().trim { it <= ' ' }.matches("-?\\d+(\\.\\d+)?".toRegex())) BigInteger(
            arg.toString().trim { it <= ' ' }).toByteArray() else if (arg.toString().trim { it <= ' ' }
                .matches("0[xX][0-9a-fA-F]+".toRegex())) BigInteger(arg.toString().trim { it <= ' ' }
            .substring(2), 16).toByteArray() else arg.toString().trim { it <= ' ' }
            .toByteArray()
        if (data.size > 32) throw RuntimeException("values can't be more than 32 byte")
        val `val` = ByteArray(32)
        var j = 0
        for (i in data.size downTo 1) {
            `val`[31 - j] = data[i - 1]
            ++j
        }
        return `val`
    }

    /**
     * encode the values and concatenate together
     *
     * @param args Object
     * @return byte[]
     */
    fun encodeDataList(vararg args: Any): ByteArray {
        val baos = ByteArrayOutputStream()
        for (arg in args) {
            val `val` = encodeValFor32Bits(arg)
            try {
                baos.write(`val`)
            } catch (e: IOException) {
                throw Error("Happen something that should never happen ", e)
            }
        }
        return baos.toByteArray()
    }

    fun firstNonZeroByte(data: ByteArray): Int {
        for (i in data.indices) {
            if (data[i].toInt() != 0) {
                return i
            }
        }
        return -1
    }

    fun stripLeadingZeroes(data: ByteArray?): ByteArray? {
        if (data == null) return null
        val firstNonZero = firstNonZeroByte(data)
        return when (firstNonZero) {
            -1 -> ZERO_BYTE_ARRAY
            0 -> data
            else -> {
                val result = ByteArray(data.size - firstNonZero)
                System.arraycopy(data, firstNonZero, result, 0, data.size - firstNonZero)
                result
            }
        }
    }

    /**
     * increment byte array as a number until max is reached
     *
     * @param bytes byte[]
     * @return boolean
     */
    fun increment(bytes: ByteArray): Boolean {
        val startIndex = 0
        var i: Int
        i = bytes.size - 1
        while (i >= startIndex) {
            bytes[i]++
            if (bytes[i].toInt() != 0) break
            i--
        }
        // we return false when all bytes are 0 again
        return i >= startIndex || bytes[startIndex].toInt() != 0
    }

    /**
     * Utility function to copy a byte array into a new byte array with given size.
     * If the src length is smaller than the given size, the result will be left-padded
     * with zeros.
     *
     * @param value - a BigInteger with a maximum value of 2^256-1
     * @return Byte array of given size with a copy of the `src`
     */
    fun copyToArray(value: BigInteger?): ByteArray {
        val src = bigIntegerToBytes(value)
        val dest = ByteBuffer.allocate(32).array()
        System.arraycopy(src, 0, dest, dest.size - src!!.size, src.size)
        return dest
    }

    fun wrap(data: ByteArray?): ByteArrayWrapper {
        return ByteArrayWrapper(data)
    }

    fun setBit(data: ByteArray, pos: Int, `val`: Int): ByteArray {
        if (data.size * 8 - 1 < pos) throw Error("outside byte array limit, pos: $pos")
        val posByte = data.size - 1 - pos / 8
        val posBit = pos % 8
        val setter = (1 shl posBit).toByte()
        val toBeSet = data[posByte]
        val result =
            if (`val` == 1) (toBeSet.toInt() or setter.toInt()).toByte()
            else (toBeSet.toInt() and setter.toInt().inv()).toByte()
        data[posByte] = result
        return data
    }

    fun getBit(data: ByteArray, pos: Int): Int {
        if (data.size * 8 - 1 < pos) throw Error("outside byte array limit, pos: $pos")
        val posByte = data.size - 1 - pos / 8
        val posBit = pos % 8
        val dataByte = data[posByte]
        return Math.min(1, dataByte.toInt() and (1 shl posBit))
    }

    fun and(b1: ByteArray, b2: ByteArray): ByteArray {
        if (b1.size != b2.size) throw RuntimeException("Array sizes differ")
        val ret = ByteArray(b1.size)
        for (i in ret.indices) {
            ret[i] = (b1[i].toInt() and b2[i].toInt()).toByte()
        }
        return ret
    }

    fun or(b1: ByteArray, b2: ByteArray): ByteArray {
        if (b1.size != b2.size) throw RuntimeException("Array sizes differ")
        val ret = ByteArray(b1.size)
        for (i in ret.indices) {
            ret[i] = (b1[i].toInt() or b2[i].toInt()).toByte()
        }
        return ret
    }

    fun xor(b1: ByteArray, b2: ByteArray): ByteArray {
        if (b1.size != b2.size) throw RuntimeException("Array sizes differ")
        val ret = ByteArray(b1.size)
        for (i in ret.indices) {
            ret[i] = (b1[i].toInt() xor b2[i].toInt()).toByte()
        }
        return ret
    }

    /**
     * XORs byte arrays of different lengths by aligning length of the shortest via adding zeros at beginning
     */
    fun xorAlignRight(b1: ByteArray, b2: ByteArray): ByteArray {
        var b1 = b1
        var b2 = b2
        if (b1.size > b2.size) {
            val b2_ = ByteArray(b1.size)
            System.arraycopy(b2, 0, b2_, b1.size - b2.size, b2.size)
            b2 = b2_
        } else if (b2.size > b1.size) {
            val b1_ = ByteArray(b2.size)
            System.arraycopy(b1, 0, b1_, b2.size - b1.size, b1.size)
            b1 = b1_
        }
        return xor(b1, b2)
    }

    /**
     * @param arrays - arrays to merge
     * @return - merged array
     */
    fun merge(vararg arrays: ByteArray): ByteArray {
        var count = 0
        for (array in arrays) {
            count += array.size
        }

        // Create new array and copy all array contents
        val mergedArray = ByteArray(count)
        var start = 0
        for (array in arrays) {
            System.arraycopy(array, 0, mergedArray, start, array.size)
            start += array.size
        }
        return mergedArray
    }

    fun isNullOrZeroArray(array: ByteArray?): Boolean {
        return array == null || array.size == 0
    }

    fun isSingleZero(array: ByteArray): Boolean {
        return array.size == 1 && array[0].toInt() == 0
    }

    fun difference(setA: Set<ByteArray>, setB: Set<ByteArray?>): Set<ByteArray> {
        val result: MutableSet<ByteArray> = HashSet()
        for (elementA in setA) {
            var found = false
            for (elementB in setB) {
                if (Arrays.equals(elementA, elementB)) {
                    found = true
                    break
                }
            }
            if (!found) result.add(elementA)
        }
        return result
    }

    fun length(vararg bytes: ByteArray?): Int {
        var result = 0
        for (array in bytes) {
            result += array?.size ?: 0
        }
        return result
    }

    fun intsToBytes(arr: IntArray, bigEndian: Boolean): ByteArray {
        val ret = ByteArray(arr.size * 4)
        intsToBytes(arr, ret, bigEndian)
        return ret
    }

    fun bytesToInts(arr: ByteArray, bigEndian: Boolean): IntArray {
        val ret = IntArray(arr.size / 4)
        bytesToInts(arr, ret, bigEndian)
        return ret
    }

    fun bytesToInts(b: ByteArray, arr: IntArray, bigEndian: Boolean) {
        if (!bigEndian) {
            var off = 0
            for (i in arr.indices) {
                var ii = b[off++].toInt() and 0x000000FF
                ii = ii or (b[off++].toInt() shl 8 and 0x0000FF00)
                ii = ii or (b[off++].toInt() shl 16 and 0x00FF0000)
                ii = ii or (b[off++].toInt() shl 24)
                arr[i] = ii
            }
        } else {
            var off = 0
            for (i in arr.indices) {
                var ii = b[off++].toInt() shl 24
                ii = ii or (b[off++].toInt() shl 16 and 0x00FF0000)
                ii = ii or (b[off++].toInt() shl 8 and 0x0000FF00)
                ii = ii or (b[off++].toInt() and 0x000000FF)
                arr[i] = ii
            }
        }
    }

    fun intsToBytes(arr: IntArray, b: ByteArray, bigEndian: Boolean) {
        if (!bigEndian) {
            var off = 0
            for (i in arr.indices) {
                val ii = arr[i]
                b[off++] = (ii and 0xFF).toByte()
                b[off++] = (ii shr 8 and 0xFF).toByte()
                b[off++] = (ii shr 16 and 0xFF).toByte()
                b[off++] = (ii shr 24 and 0xFF).toByte()
            }
        } else {
            var off = 0
            for (i in arr.indices) {
                val ii = arr[i]
                b[off++] = (ii shr 24 and 0xFF).toByte()
                b[off++] = (ii shr 16 and 0xFF).toByte()
                b[off++] = (ii shr 8 and 0xFF).toByte()
                b[off++] = (ii and 0xFF).toByte()
            }
        }
    }

    @JvmOverloads
    fun bigEndianToShort(bs: ByteArray, off: Int = 0): Short {
        var off = off
        var n = bs[off].toInt() shl 8
        ++off
        n = n or (bs[off].toInt() and 0xFF)
        return n.toShort()
    }

    fun shortToBytes(n: Short): ByteArray {
        return ByteBuffer.allocate(2).putShort(n).array()
    }

    /**
     * Converts string hex representation to data bytes
     * Accepts following hex:
     * - with or without 0x prefix
     * - with no leading 0, like 0xabc -> 0x0abc
     * @param data  String like '0xa5e..' or just 'a5e..'
     * @return  decoded bytes array
     */
    fun hexStringToBytes(data: String?): ByteArray {
//        var data = data ?: return EMPTY_BYTE_ARRAY
//        if (data.startsWith("0x")) data = data.substring(2)
//        if (data.length % 2 == 1) data = "0$data"
        //return Hex.decode(data)
        return Numeric.hexStringToByteArray(data)
    }

    /**
     * Converts string representation of host/ip to 4-bytes byte[] IPv4
     */
//    fun hostToBytes(ip: String?): ByteArray {
//        val bytesIp: ByteArray
//        bytesIp = try {
//            InetAddress.getByName(ip).getAddress()
//        } catch (e: UnknownHostException) {
//            ByteArray(4) // fall back to invalid 0.0.0.0 address
//        }
//        return bytesIp
//    }

    /**
     * Converts 4 bytes IPv4 IP to String representation
     */
    fun bytesToIp(bytesIp: ByteArray): String {
        val sb = StringBuilder()
        sb.append(bytesIp[0].toInt() and 0xFF)
        sb.append(".")
        sb.append(bytesIp[1].toInt() and 0xFF)
        sb.append(".")
        sb.append(bytesIp[2].toInt() and 0xFF)
        sb.append(".")
        sb.append(bytesIp[3].toInt() and 0xFF)
        return sb.toString()
    }

    /**
     * Returns a number of zero bits preceding the highest-order ("leftmost") one-bit
     * interpreting input array as a big-endian integer value
     */
    fun numberOfLeadingZeros(bytes: ByteArray): Int {
        val i = firstNonZeroByte(bytes)
        return if (i == -1) {
            bytes.size * 8
        } else {
            val byteLeadingZeros = Integer.numberOfLeadingZeros(bytes[i].toInt() and 0xff) - 24
            i * 8 + byteLeadingZeros
        }
    }

    /**
     * Parses fixed number of bytes starting from `offset` in `input` array.
     * If `input` has not enough bytes return array will be right padded with zero bytes.
     * I.e. if `offset` is higher than `input.length` then zero byte array of length `len` will be returned
     */
    fun parseBytes(input: ByteArray, offset: Int, len: Int): ByteArray {
        if (offset >= input.size || len == 0) return EMPTY_BYTE_ARRAY
        val bytes = ByteArray(len)
        System.arraycopy(input, offset, bytes, 0, Math.min(input.size - offset, len))
        return bytes
    }

    /**
     * Parses 32-bytes word from given input.
     * Uses [.parseBytes] method,
     * thus, result will be right-padded with zero bytes if there is not enough bytes in `input`
     *
     * @param idx an index of the word starting from `0`
     */
    fun parseWord(input: ByteArray, idx: Int): ByteArray {
        return parseBytes(input, 32 * idx, 32)
    }

    /**
     * Parses 32-bytes word from given input.
     * Uses [.parseBytes] method,
     * thus, result will be right-padded with zero bytes if there is not enough bytes in `input`
     *
     * @param idx an index of the word starting from `0`
     * @param offset an offset in `input` array to start parsing from
     */
    fun parseWord(input: ByteArray, offset: Int, idx: Int): ByteArray {
        return parseBytes(input, offset + 32 * idx, 32)
    }
}