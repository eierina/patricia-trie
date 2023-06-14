import org.web3j.utils.Numeric
import java.io.ByteArrayOutputStream
import java.util.*

object CompactEncoder {
    private const val TERMINATOR: Byte = 16
    private val hexMap: MutableMap<Char, Byte> = HashMap()

    init {
        for (i in 0..15) {
            hexMap[i.toString(16).single()] = i.toByte()
        }
    }

    private fun concatenate(array1: ByteArray, array2: ByteArray) = array1.plus(array2)

    fun packNibbles(nibbles: ByteArray): ByteArray {
        var tempNibbles = nibbles
        var terminator = 0

        if (tempNibbles[tempNibbles.size - 1] == TERMINATOR) {
            terminator = 1
            tempNibbles = tempNibbles.copyOf(tempNibbles.size - 1)
        }
        val oddlen = tempNibbles.size % 2
        val flag = 2 * terminator + oddlen
        tempNibbles = if (oddlen != 0) {
            concatenate(byteArrayOf(flag.toByte()), tempNibbles)
        } else {
            concatenate(byteArrayOf(flag.toByte(), 0), tempNibbles)
        }
        val buffer = ByteArrayOutputStream()
        for (i in tempNibbles.indices step 2) {
            buffer.write(16 * tempNibbles[i] + tempNibbles[i + 1])
        }
        return buffer.toByteArray()
    }

    fun hasTerminator(packedKey: ByteArray): Boolean {
        return (packedKey[0].toInt() shr 4 and 2) != 0
    }

    fun unpackToNibbles(str: ByteArray): ByteArray {
        var base = binToNibbles(str)
        base = base.copyOf(base.size - 1)
        if (base[0] >= 2) {
            base = base.plus(TERMINATOR)
        }
        base = if (base[0] % 2 == 1) {
            base.copyOfRange(1, base.size)
        } else {
            base.copyOfRange(2, base.size)
        }
        return base
    }

    fun binToNibbles(str: ByteArray): ByteArray {
        val hexEncoded = Numeric.toHexStringNoPrefix(str).toByteArray()
        val hexEncodedTerminated = hexEncoded.copyOf(hexEncoded.size + 1)

        for (i in hexEncoded.indices) {
            val b = hexEncodedTerminated[i]
            hexEncodedTerminated[i] = hexMap[b.toChar()]!!
        }

        hexEncodedTerminated[hexEncodedTerminated.size - 1] = TERMINATOR
        return hexEncodedTerminated
    }

    fun binToNibblesNoTerminator(str: ByteArray): ByteArray {
        val hexEncoded = Numeric.toHexStringNoPrefix(str).toByteArray()

        for (i in hexEncoded.indices) {
            val b = hexEncoded[i]
            hexEncoded[i] = hexMap[b.toChar()]!!
        }

        return hexEncoded
    }
}


///*
// * Copyright (c) [2016] [ <ether.camp> ]
// * This file is part of the ethereumJ library.
// *
// * The ethereumJ library is free software: you can redistribute it and/or modify
// * it under the terms of the GNU Lesser General Public License as published by
// * the Free Software Foundation, either version 3 of the License, or
// * (at your option) any later version.
// *
// * The ethereumJ library is distributed in the hope that it will be useful,
// * but WITHOUT ANY WARRANTY; without even the implied warranty of
// * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// * GNU Lesser General Public License for more details.
// *
// * You should have received a copy of the GNU Lesser General Public License
// * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
// */
//package org.ethereum.util
//
//import java.io.ByteArrayOutputStream
//import java.util.*
//
////import static org.ethereum.util.ByteUtil.appendByte;
////import static org.spongycastle.util.Arrays.concatenate;
////import static org.spongycastle.util.encoders.Hex.encode;
///**
// * Compact encoding of hex sequence with optional terminator
// *
// * The traditional compact way of encoding a hex string is to convert it into binary
// * - that is, a string like 0f1248 would become three bytes 15, 18, 72. However,
// * this approach has one slight problem: what if the length of the hex string is odd?
// * In that case, there is no way to distinguish between, say, 0f1248 and f1248.
// *
// * Additionally, our application in the Merkle Patricia tree requires the additional feature
// * that a hex string can also have a special "terminator symbol" at the end (denoted by the 'T').
// * A terminator symbol can occur only once, and only at the end.
// *
// * An alternative way of thinking about this to not think of there being a terminator symbol,
// * but instead treat bit specifying the existence of the terminator symbol as a bit specifying
// * that the given node encodes a final node, where the value is an actual value, rather than
// * the hash of yet another node.
// *
// * To solve both of these issues, we force the first nibble of the final byte-stream to encode
// * two flags, specifying oddness of length (ignoring the 'T' symbol) and terminator status;
// * these are placed, respectively, into the two lowest significant bits of the first nibble.
// * In the case of an even-length hex string, we must introduce a second nibble (of value zero)
// * to ensure the hex-string is even in length and thus is representable by a whole number of bytes.
// *
// * Examples:
// * &gt; [ 1, 2, 3, 4, 5 ]
// * '\x11\x23\x45'
// * &gt; [ 0, 1, 2, 3, 4, 5 ]
// * '\x00\x01\x23\x45'
// * &gt; [ 0, 15, 1, 12, 11, 8, T ]
// * '\x20\x0f\x1c\xb8'
// * &gt; [ 15, 1, 12, 11, 8, T ]
// * '\x3f\x1c\xb8'
// */
//object CompactEncoder {
//    private const val TERMINATOR: Byte = 16
//    private val hexMap: MutableMap<Char, Byte> = HashMap()
//
//    init {
//        hexMap['0'] = 0x0.toByte()
//        hexMap['1'] = 0x1.toByte()
//        hexMap['2'] = 0x2.toByte()
//        hexMap['3'] = 0x3.toByte()
//        hexMap['4'] = 0x4.toByte()
//        hexMap['5'] = 0x5.toByte()
//        hexMap['6'] = 0x6.toByte()
//        hexMap['7'] = 0x7.toByte()
//        hexMap['8'] = 0x8.toByte()
//        hexMap['9'] = 0x9.toByte()
//        hexMap['a'] = 0xa.toByte()
//        hexMap['b'] = 0xb.toByte()
//        hexMap['c'] = 0xc.toByte()
//        hexMap['d'] = 0xd.toByte()
//        hexMap['e'] = 0xe.toByte()
//        hexMap['f'] = 0xf.toByte()
//    }
//
//    /**
//     * Pack nibbles to binary
//     *
//     * @param nibbles sequence. may have a terminator
//     * @return hex-encoded byte array
//     */
//    fun packNibbles(nibbles: ByteArray): ByteArray {
//        var nibbles = nibbles
//        var terminator = 0
//        if (nibbles[nibbles.size - 1] == TERMINATOR) {
//            terminator = 1
//            nibbles = Arrays.copyOf(nibbles, nibbles.size - 1)
//        }
//
//        val oddlen = nibbles.size % 2
//        val flag = 2 * terminator + oddlen
//        if (oddlen != 0) {
//            val flags = byteArrayOf(flag.toByte())
//            nibbles = concatenate(flags, nibbles)
//        } else {
//            val flags = byteArrayOf(flag.toByte(), 0)
//            nibbles = concatenate(flags, nibbles)
//        }
//        val buffer = ByteArrayOutputStream()
//        var i = 0
//        while (i < nibbles.size) {
//            buffer.write(16 * nibbles[i] + nibbles[i + 1])
//            i += 2
//        }
//        return buffer.toByteArray()
//    }
//
//    fun hasTerminator(packedKey: ByteArray): Boolean {
//        return packedKey[0].toInt() shr 4 and 2 != 0
//    }
//
//    /**
//     * Unpack a binary string to its nibbles equivalent
//     *
//     * @param str of binary data
//     * @return array of nibbles in byte-format
//     */
//    fun unpackToNibbles(str: ByteArray?): ByteArray {
//        var base = binToNibbles(str)
//        base = Arrays.copyOf(base, base.size - 1)
//        if (base[0] >= 2) {
//            base = appendByte(base, TERMINATOR)
//        }
//        base = if (base[0] % 2 == 1) {
//            Arrays.copyOfRange(base, 1, base.size)
//        } else {
//            Arrays.copyOfRange(base, 2, base.size)
//        }
//        return base
//    }
//
//    /**
//     * Transforms a binary array to hexadecimal format + terminator
//     *
//     * @param str byte[]
//     * @return array with each individual nibble adding a terminator at the end
//     */
//    fun binToNibbles(str: ByteArray?): ByteArray {
//        val hexEncoded: ByteArray = encode(str)
//        val hexEncodedTerminated = Arrays.copyOf(hexEncoded, hexEncoded.size + 1)
//        for (i in hexEncoded.indices) {
//            val b = hexEncodedTerminated[i]
//            hexEncodedTerminated[i] = hexMap[b.toChar()]!!
//        }
//        hexEncodedTerminated[hexEncodedTerminated.size - 1] = TERMINATOR
//        return hexEncodedTerminated
//    }
//
//    fun binToNibblesNoTerminator(str: ByteArray?): ByteArray {
//        val hexEncoded: ByteArray = encode(str)
//        for (i in hexEncoded.indices) {
//            val b = hexEncoded[i]
//            hexEncoded[i] = hexMap[b.toChar()]!!
//        }
//        return hexEncoded
//    }
//
//    fun concatenate(array1: ByteArray, array2: ByteArray) = array1.plus(array2)
//}
