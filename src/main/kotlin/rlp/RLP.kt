package com.r3.corda.evmbridge.evm.rlp

import com.r3.corda.evmbridge.evm.utils.Value
import org.bouncycastle.util.BigIntegers
import org.ethereum.db.ByteArrayWrapper
import org.ethereum.util.ByteUtil
import org.ethereum.util.ByteUtil.byteArrayToInt
import org.ethereum.util.ByteUtil.intToBytesNoLeadZeroes
import org.ethereum.util.ByteUtil.isNullOrZeroArray
import org.ethereum.util.ByteUtil.isSingleZero
import org.slf4j.LoggerFactory
import org.web3j.utils.Numeric
import java.io.Serializable
import java.math.BigInteger
import java.util.*
import kotlin.collections.HashSet

/**
 * Recursive Length Prefix (RLP) encoding.
 *
 *
 * The purpose of RLP is to encode arbitrarily nested arrays of binary data, and
 * RLP is the main encoding method used to serialize objects in Ethereum. The
 * only purpose of RLP is to encode structure; encoding specific atomic data
 * types (eg. strings, integers, floats) is left up to higher-order protocols; in
 * Ethereum the standard is that integers are represented in big endian binary
 * form. If one wishes to use RLP to encode a dictionary, the two suggested
 * canonical forms are to either use [[k1,v1],[k2,v2]...] with keys in
 * lexicographic order or to use the higher-level Patricia Tree encoding as
 * Ethereum does.
 *
 *
 * The RLP encoding function takes in an item. An item is defined as follows:
 *
 *
 * - A string (ie. byte array) is an item - A list of items is an item
 *
 *
 * For example, an empty string is an item, as is the string containing the word
 * "cat", a list containing any number of strings, as well as more complex data
 * structures like ["cat",["puppy","cow"],"horse",[[]],"pig",[""],"sheep"]. Note
 * that in the context of the rest of this article, "string" will be used as a
 * synonym for "a certain number of bytes of binary data"; no special encodings
 * are used and no knowledge about the content of the strings is implied.
 *
 *
 * See: https://github.com/ethereum/wiki/wiki/%5BEnglish%5D-RLP
 *
 * @author Roman Mandeleil
 * @since 01.04.2014
 */
object RLP {
    private val logger = LoggerFactory.getLogger("rlp")
    val EMPTY_ELEMENT_RLP = encodeElement(ByteArray(0))
    private const val MAX_DEPTH = 16

    /**
     * Allow for content up to size of 2^64 bytes *
     */
    private val MAX_ITEM_LENGTH = Math.pow(256.0, 8.0)

    /**
     * Reason for threshold according to Vitalik Buterin:
     * - 56 bytes maximizes the benefit of both options
     * - if we went with 60 then we would have only had 4 slots for long strings
     * so RLP would not have been able to store objects above 4gb
     * - if we went with 48 then RLP would be fine for 2^128 space, but that's way too much
     * - so 56 and 2^64 space seems like the right place to put the cutoff
     * - also, that's where Bitcoin's varint does the cutof
     */
    private const val SIZE_THRESHOLD = 56
    /** RLP encoding rules are defined as follows:  */ /*
     * For a single byte whose value is in the [0x00, 0x7f] range, that byte is
     * its own RLP encoding.
     */
    /**
     * [0x80]
     * If a string is 0-55 bytes long, the RLP encoding consists of a single
     * byte with value 0x80 plus the length of the string followed by the
     * string. The range of the first byte is thus [0x80, 0xb7].
     */
    private const val OFFSET_SHORT_ITEM = 0x80

    /**
     * [0xb7]
     * If a string is more than 55 bytes long, the RLP encoding consists of a
     * single byte with value 0xb7 plus the length of the length of the string
     * in binary form, followed by the length of the string, followed by the
     * string. For example, a length-1024 string would be encoded as
     * \xb9\x04\x00 followed by the string. The range of the first byte is thus
     * [0xb8, 0xbf].
     */
    private const val OFFSET_LONG_ITEM = 0xb7

    /**
     * [0xc0]
     * If the total payload of a list (i.e. the combined length of all its
     * items) is 0-55 bytes long, the RLP encoding consists of a single byte
     * with value 0xc0 plus the length of the list followed by the concatenation
     * of the RLP encodings of the items. The range of the first byte is thus
     * [0xc0, 0xf7].
     */
    private const val OFFSET_SHORT_LIST = 0xc0

    /**
     * [0xf7]
     * If the total payload of a list is more than 55 bytes long, the RLP
     * encoding consists of a single byte with value 0xf7 plus the length of the
     * length of the list in binary form, followed by the length of the list,
     * followed by the concatenation of the RLP encodings of the items. The
     * range of the first byte is thus [0xf8, 0xff].
     */
    private const val OFFSET_LONG_LIST = 0xf7

    /* ******************************************************
     *                      DECODING                        *
     * ******************************************************/
    private fun decodeOneByteItem(data: ByteArray, index: Int): Byte {
        // null item
        if (data[index].toInt() and 0xFF == OFFSET_SHORT_ITEM) {
            return (data[index] - OFFSET_SHORT_ITEM).toByte()
        }
        // single byte item
        if (data[index].toInt() and 0xFF < OFFSET_SHORT_ITEM) {
            return data[index]
        }
        // single byte item
        return if (data[index].toInt() and 0xFF == OFFSET_SHORT_ITEM + 1) {
            data[index + 1]
        } else 0
    }

    fun decodeInt(data: ByteArray, index: Int): Int {
        var value = 0
        // NOTE: From RLP doc:
        // Ethereum integers must be represented in big endian binary form
        // with no leading zeroes (thus making the integer value zero be
        // equivalent to the empty byte array)
        if (data[index].toInt() == 0x00) {
            throw RuntimeException("not a number")
        } else if (data[index].toInt() and 0xFF < OFFSET_SHORT_ITEM) {
            return data[index].toInt()
        } else if (data[index].toInt() and 0xFF <= OFFSET_SHORT_ITEM + Integer.BYTES) {
            val length = (data[index] - OFFSET_SHORT_ITEM).toByte()
            var pow = (length - 1).toByte()
            for (i in 1..length) {
                // << (8 * pow) == bit shift to 0 (*1), 8 (*256) , 16 (*65..)..
                value += data[index + i].toInt() and 0xFF shl 8 * pow
                pow--
            }
        } else {

            // If there are more than 4 bytes, it is not going
            // to decode properly into an int.
            throw RuntimeException("wrong decode attempt")
        }
        return value
    }

    //    fun decodeShort(data: ByteArray, index: Int): Short {
//        var value: Short = 0
//        if (data[index].toInt() == 0x00) {
//            throw RuntimeException("not a number")
//        } else if (data[index].toInt() and 0xFF < OFFSET_SHORT_ITEM) {
//            return data[index].toShort()
//        } else if (data[index].toInt() and 0xFF <= OFFSET_SHORT_ITEM + java.lang.Short.BYTES) {
//            val length = (data[index] - OFFSET_SHORT_ITEM).toByte()
//            var pow = (length - 1).toByte()
//            for (i in 1..length) {
//                // << (8 * pow) == bit shift to 0 (*1), 8 (*256) , 16 (*65..)
//                (value += (data[index + i].toInt() and 0xFF shl 8 * pow).toShort()).toShort()
//                pow--
//            }
//        } else {
//
//            // If there are more than 2 bytes, it is not going
//            // to decode properly into a short.
//            throw RuntimeException("wrong decode attempt")
//        }
//        return value
//    }
    fun decodeShort(data: ByteArray, index: Int): Short {
        var value: Short = 0

        if (data[index] == 0x00.toByte()) {
            throw RuntimeException("not a number")
        } else if ((data[index].toInt() and 0xFF) < OFFSET_SHORT_ITEM) {
            return data[index].toShort()
        } else if ((data[index].toInt() and 0xFF) <= OFFSET_SHORT_ITEM + java.lang.Short.BYTES) {
            val length = (data[index].toInt() - OFFSET_SHORT_ITEM).toByte()
            var pow = (length - 1).toByte()
            for (i in 1..length) {
                // << (8 * pow) == bit shift to 0 (*1), 8 (*256) , 16 (*65..)
                //value += (data[index + i].toInt() and 0xFF shl (8 * pow)).toShort()
                value = (value + ((data[index + i].toInt() and 0xFF) shl (8 * pow))).toShort()
                pow--
            }
        } else {
            // If there are more than 2 bytes, it is not going to decode properly into a short.
            throw RuntimeException("wrong decode attempt")
        }
        return value
    }


    fun decodeLong(data: ByteArray, index: Int): Long {
        var value: Long = 0
        if (data[index].toInt() == 0x00) {
            throw RuntimeException("not a number")
        } else if (data[index].toInt() and 0xFF < OFFSET_SHORT_ITEM) {
            return data[index].toLong()
        } else if (data[index].toInt() and 0xFF <= OFFSET_SHORT_ITEM + java.lang.Long.BYTES) {
            val length = (data[index] - OFFSET_SHORT_ITEM).toByte()
            var pow = (length - 1).toByte()
            for (i in 1..length) {
                // << (8 * pow) == bit shift to 0 (*1), 8 (*256) , 16 (*65..)..
                value += (data[index + i].toInt() and 0xFF).toLong() shl 8 * pow
                pow--
            }
        } else {

            // If there are more than 8 bytes, it is not going
            // to decode properly into a long.
            throw RuntimeException("wrong decode attempt")
        }
        return value
    }

    private fun decodeStringItem(data: ByteArray, index: Int): String {
        val valueBytes = decodeItemBytes(data, index)
        return if (valueBytes.size == 0) {
            // shortcut
            ""
        } else {
            String(valueBytes)
        }
    }

    fun decodeBigInteger(data: ByteArray, index: Int): BigInteger {
        val valueBytes = decodeItemBytes(data, index)
        return if (valueBytes.size == 0) {
            // shortcut
            BigInteger.ZERO
        } else {
            BigInteger(1, valueBytes)
        }
    }

    private fun decodeByteArray(data: ByteArray, index: Int): ByteArray {
        return decodeItemBytes(data, index)
    }

    private fun nextItemLength(data: ByteArray, index: Int): Int {
        if (index >= data.size) return -1
        // [0xf8, 0xff]
        if (data[index].toInt() and 0xFF > OFFSET_LONG_LIST) {
            val lengthOfLength = (data[index] - OFFSET_LONG_LIST).toByte()
            return calcLength(lengthOfLength.toInt(), data, index)
        }
        // [0xc0, 0xf7]
        if (data[index].toInt() and 0xFF >= OFFSET_SHORT_LIST
            && data[index].toInt() and 0xFF <= OFFSET_LONG_LIST
        ) {
            return ((data[index].toInt() and 0xFF) - OFFSET_SHORT_LIST).toByte().toInt()
        }
        // [0xb8, 0xbf]
        if (data[index].toInt() and 0xFF > OFFSET_LONG_ITEM
            && data[index].toInt() and 0xFF < OFFSET_SHORT_LIST
        ) {
            val lengthOfLength = (data[index] - OFFSET_LONG_ITEM).toByte()
            return calcLength(lengthOfLength.toInt(), data, index)
        }
        // [0x81, 0xb7]
        if (data[index].toInt() and 0xFF > OFFSET_SHORT_ITEM
            && data[index].toInt() and 0xFF <= OFFSET_LONG_ITEM
        ) {
            return ((data[index].toInt() and 0xFF) - OFFSET_SHORT_ITEM).toByte().toInt()
        }
        // [0x00, 0x80]
        return if (data[index].toInt() and 0xFF <= OFFSET_SHORT_ITEM) {
            1
        } else -1
    }

    fun decodeIP4Bytes(data: ByteArray, index: Int): ByteArray {
        var offset = 1
        val result = ByteArray(4)
        for (i in 0..3) {
            result[i] = decodeOneByteItem(data, index + offset)
            offset += if (data[index + offset].toInt() and 0xFF > OFFSET_SHORT_ITEM) 2 else 1
        }

        // return IP address
        return result
    }

    fun getFirstListElement(payload: ByteArray, pos: Int): Int {
        if (pos >= payload.size) return -1

        // [0xf8, 0xff]
        if (payload[pos].toInt() and 0xFF > OFFSET_LONG_LIST) {
            val lengthOfLength = (payload[pos] - OFFSET_LONG_LIST).toByte()
            return pos + lengthOfLength + 1
        }
        // [0xc0, 0xf7]
        if (payload[pos].toInt() and 0xFF >= OFFSET_SHORT_LIST
            && payload[pos].toInt() and 0xFF <= OFFSET_LONG_LIST
        ) {
            return pos + 1
        }
        // [0xb8, 0xbf]
        if (payload[pos].toInt() and 0xFF > OFFSET_LONG_ITEM
            && payload[pos].toInt() and 0xFF < OFFSET_SHORT_LIST
        ) {
            val lengthOfLength = (payload[pos] - OFFSET_LONG_ITEM).toByte()
            return pos + lengthOfLength + 1
        }
        return -1
    }

    fun getNextElementIndex(payload: ByteArray, pos: Int): Int {
        if (pos >= payload.size) return -1

        // [0xf8, 0xff]
        if (payload[pos].toInt() and 0xFF > OFFSET_LONG_LIST) {
            val lengthOfLength = (payload[pos] - OFFSET_LONG_LIST).toByte()
            val length = calcLength(lengthOfLength.toInt(), payload, pos)
            return pos + lengthOfLength + length + 1
        }
        // [0xc0, 0xf7]
        if (payload[pos].toInt() and 0xFF >= OFFSET_SHORT_LIST
            && payload[pos].toInt() and 0xFF <= OFFSET_LONG_LIST
        ) {
            val length = ((payload[pos].toInt() and 0xFF) - OFFSET_SHORT_LIST).toByte()
            return pos + 1 + length
        }
        // [0xb8, 0xbf]
        if (payload[pos].toInt() and 0xFF > OFFSET_LONG_ITEM
            && payload[pos].toInt() and 0xFF < OFFSET_SHORT_LIST
        ) {
            val lengthOfLength = (payload[pos] - OFFSET_LONG_ITEM).toByte()
            val length = calcLength(lengthOfLength.toInt(), payload, pos)
            return pos + lengthOfLength + length + 1
        }
        // [0x81, 0xb7]
        if (payload[pos].toInt() and 0xFF > OFFSET_SHORT_ITEM
            && payload[pos].toInt() and 0xFF <= OFFSET_LONG_ITEM
        ) {
            val length = ((payload[pos].toInt() and 0xFF) - OFFSET_SHORT_ITEM).toByte()
            return pos + 1 + length
        }
        // []0x80]
        if (payload[pos].toInt() and 0xFF == OFFSET_SHORT_ITEM) {
            return pos + 1
        }
        // [0x00, 0x7f]
        return if (payload[pos].toInt() and 0xFF < OFFSET_SHORT_ITEM) {
            pos + 1
        } else -1
    }

    /**
     * Parse length of long item or list.
     * RLP supports lengths with up to 8 bytes long,
     * but due to java limitation it returns either encoded length
     * or [Integer.MAX_VALUE] in case if encoded length is greater
     *
     * @param lengthOfLength length of length in bytes
     * @param msgData message
     * @param pos position to parse from
     *
     * @return calculated length
     */
    private fun calcLength(lengthOfLength: Int, msgData: ByteArray, pos: Int): Int {
        var pow = (lengthOfLength - 1).toByte()
        var length = 0
        for (i in 1..lengthOfLength) {
            val bt = msgData[pos + i].toInt() and 0xFF
            val shift = 8 * pow

            // no leading zeros are acceptable
            if (bt == 0 && length == 0) {
                throw RuntimeException("RLP length contains leading zeros")
            }

            // return MAX_VALUE if index of highest bit is more than 31
            if (32 - Integer.numberOfLeadingZeros(bt) + shift > 31) {
                return Int.MAX_VALUE
            }
            length += bt shl shift
            pow--
        }

        // check that length is in payload bounds
        verifyLength(length, msgData.size - pos - lengthOfLength)
        return length
    }

    fun getCommandCode(data: ByteArray): Byte {
        val index = getFirstListElement(data, 0)
        val command = data[index]
        return if (command.toInt() and 0xFF == OFFSET_SHORT_ITEM) 0 else command
    }

    /**
     * Parse wire byte[] message into RLP elements
     *
     * @param msgData - raw RLP data
     * @param depthLimit - limits depth of decoding
     * @return rlpList
     * - outcome of recursive RLP structure
     */
    fun decode2(msgData: ByteArray, depthLimit: Int): RLPList {
        if (depthLimit < 1) {
            throw RuntimeException("Depth limit should be 1 or higher")
        }
        val rlpList = RLPList()
        fullTraverse(msgData, 0, 0, msgData.size, rlpList, depthLimit)
        return rlpList
    }

    /**
     * Parse wire byte[] message into RLP elements
     *
     * @param msgData - raw RLP data
     * @return rlpList
     * - outcome of recursive RLP structure
     */
    fun decode2(msgData: ByteArray): RLPList {
        val rlpList = RLPList()
        fullTraverse(msgData, 0, 0, msgData.size, rlpList, Int.MAX_VALUE)
        return rlpList
    }

    /**
     * Decodes RLP with list without going deep after 1st level list
     * (actually, 2nd as 1st level is wrap only)
     *
     * So assuming you've packed several byte[] with [.encodeList],
     * you could use this method to unpack them,
     * getting RLPList with RLPItem's holding byte[] inside
     * @param msgData rlp data
     * @return list of RLPItems
     */
    fun unwrapList(msgData: ByteArray): RLPList? {
        return decode2(msgData, 2)[0] as RLPList?
    }

    fun decode2OneItem(msgData: ByteArray?, startPos: Int): RLPElement? {
        val rlpList = RLPList()
        fullTraverse(msgData, 0, startPos, startPos + 1, rlpList, Int.MAX_VALUE)
        return rlpList[0]
    }

    /**
     * Get exactly one message payload
     */
    fun fullTraverse(
        msgData: ByteArray?, level: Int, startPos: Int,
        endPos: Int, rlpList: RLPList, depth: Int
    ) {
        if (level > MAX_DEPTH) {
            throw RuntimeException(String.format("Error: Traversing over max RLP depth (%s)", MAX_DEPTH))
        }
        try {
            if (msgData == null || msgData.isEmpty()) return
            var pos = startPos
            while (pos < endPos) {
                logger.debug("fullTraverse: level: $level startPos: $pos endPos: $endPos")


                // It's a list with a payload more than 55 bytes
                // data[0] - 0xF7 = how many next bytes allocated
                // for the length of the list
                if (msgData[pos].toInt() and 0xFF > OFFSET_LONG_LIST) {
                    val lengthOfLength = (msgData[pos] - OFFSET_LONG_LIST).toByte()
                    val length = calcLength(lengthOfLength.toInt(), msgData, pos)
                    if (length < SIZE_THRESHOLD) {
                        throw RuntimeException("Short list has been encoded as long list")
                    }

                    // check that length is in payload bounds
                    verifyLength(length, msgData.size - pos - lengthOfLength)
                    val rlpData = ByteArray(lengthOfLength + length + 1)
                    System.arraycopy(
                        msgData, pos, rlpData, 0, lengthOfLength
                                + length + 1
                    )
                    if (level + 1 < depth) {
                        val newLevelList = RLPList()
                        newLevelList.rLPData = rlpData
                        fullTraverse(
                            msgData, level + 1, pos + lengthOfLength + 1,
                            pos + lengthOfLength + length + 1, newLevelList, depth
                        )
                        rlpList.add(newLevelList)
                    } else {
                        rlpList.add(RLPItem(rlpData))
                    }
                    pos += lengthOfLength + length + 1
                    continue
                }
                // It's a list with a payload less than 55 bytes
                if (msgData[pos].toInt() and 0xFF in OFFSET_SHORT_LIST..OFFSET_LONG_LIST
                ) {
                    val length = ((msgData[pos].toInt() and 0xFF) - OFFSET_SHORT_LIST).toByte()
                    val rlpData = ByteArray(length + 1)
                    System.arraycopy(msgData, pos, rlpData, 0, length + 1)
                    if (level + 1 < depth) {
                        val newLevelList = RLPList()
                        newLevelList.rLPData = rlpData
                        if (length > 0) fullTraverse(msgData, level + 1, pos + 1, pos + length + 1, newLevelList, depth)
                        rlpList.add(newLevelList)
                    } else {
                        rlpList.add(RLPItem(rlpData))
                    }
                    pos += 1 + length
                    continue
                }
                // It's an item with a payload more than 55 bytes
                // data[0] - 0xB7 = how much next bytes allocated for
                // the length of the string
                if (msgData[pos].toInt() and 0xFF in (OFFSET_LONG_ITEM + 1) until OFFSET_SHORT_LIST
                ) {
                    val lengthOfLength = (msgData[pos] - OFFSET_LONG_ITEM).toByte()
                    val length = calcLength(lengthOfLength.toInt(), msgData, pos)
                    if (length < SIZE_THRESHOLD) {
                        throw RuntimeException("Short item has been encoded as long item")
                    }

                    // check that length is in payload bounds
                    verifyLength(length, msgData.size - pos - lengthOfLength)

                    // now we can parse an item for data[1]..data[length]
                    val item = ByteArray(length)
                    System.arraycopy(
                        msgData, pos + lengthOfLength + 1, item,
                        0, length
                    )
                    val rlpItem = RLPItem(item)
                    rlpList.add(rlpItem)
                    pos += lengthOfLength + length + 1
                    continue
                }
                // It's an item less than 55 bytes long,
                // data[0] - 0x80 == length of the item
                if (msgData[pos].toInt() and 0xFF > OFFSET_SHORT_ITEM
                    && msgData[pos].toInt() and 0xFF <= OFFSET_LONG_ITEM
                ) {
                    val length = ((msgData[pos].toInt() and 0xFF) - OFFSET_SHORT_ITEM).toByte()
                    val item = ByteArray(length.toInt())
                    System.arraycopy(msgData, pos + 1, item, 0, length.toInt())
                    if (length.toInt() == 1 && item[0].toInt() and 0xFF < OFFSET_SHORT_ITEM) {
                        throw RuntimeException("Single byte has been encoded as byte string")
                    }
                    val rlpItem = RLPItem(item)
                    rlpList.add(rlpItem)
                    pos += 1 + length
                    continue
                }
                // null item
                if (msgData[pos].toInt() and 0xFF == OFFSET_SHORT_ITEM) {
                    val item = ByteUtil.EMPTY_BYTE_ARRAY
                    val rlpItem = RLPItem(item)
                    rlpList.add(rlpItem)
                    pos += 1
                    continue
                }
                // single byte item
                if (msgData[pos].toInt() and 0xFF < OFFSET_SHORT_ITEM) {
                    val item = byteArrayOf((msgData[pos].toInt() and 0xFF).toByte())
                    val rlpItem = RLPItem(item)
                    rlpList.add(rlpItem)
                    pos += 1
                }
            }
        } catch (e: Exception) {
            throw RuntimeException(
                "RLP wrong encoding (" + Numeric.toHexString(msgData, startPos, endPos - startPos, false) + ")",
                e
            )
        } catch (e: OutOfMemoryError) {
            throw RuntimeException(
                "Invalid RLP (excessive mem allocation while parsing) (" + Numeric.toHexString(
                    msgData,
                    startPos,
                    endPos - startPos,
                    false
                ) + ")", e
            )
        }
    }

    /**
     * Compares supplied length information with maximum possible
     * @param suppliedLength    Length info from header
     * @param availableLength   Length of remaining object
     * @throws RuntimeException if supplied length is bigger than available
     */
    private fun verifyLength(suppliedLength: Int, availableLength: Int) {
        if (suppliedLength > availableLength) {
            throw RuntimeException(
                String.format(
                    "Length parsed from RLP (%s bytes) is greater " +
                            "than possible size of data (%s bytes)", suppliedLength, availableLength
                )
            )
        }
    }

    /**
     * Reads any RLP encoded byte-array and returns all objects as byte-array or list of byte-arrays
     *
     * @param data RLP encoded byte-array
     * @param pos  position in the array to start reading
     * @return DecodeResult encapsulates the decoded items as a single Object and the final read position
     */
    fun decode(data: ByteArray?, pos: Int): DecodeResult? {
        var pos = pos
        if (data == null || data.size < 1) {
            return null
        }
        val prefix = data[pos].toInt() and 0xFF
        return if (prefix == OFFSET_SHORT_ITEM) {  // 0x80
            DecodeResult(pos + 1, "") // means no length or 0
        } else if (prefix < OFFSET_SHORT_ITEM) {  // [0x00, 0x7f]
            DecodeResult(pos + 1, byteArrayOf(data[pos])) // byte is its own RLP encoding
        } else if (prefix <= OFFSET_LONG_ITEM) {  // [0x81, 0xb7]
            val len = prefix - OFFSET_SHORT_ITEM // length of the encoded bytes
            DecodeResult(pos + 1 + len, Arrays.copyOfRange(data, pos + 1, pos + 1 + len))
        } else if (prefix < OFFSET_SHORT_LIST) {  // [0xb8, 0xbf]
            val lenlen = prefix - OFFSET_LONG_ITEM // length of length the encoded bytes
            val lenbytes: Int =
                byteArrayToInt(Arrays.copyOfRange(data, pos + 1, pos + 1 + lenlen)) // length of encoded bytes
            // check that length is in payload bounds
            verifyLength(lenbytes, data.size - pos - 1 - lenlen)
            DecodeResult(
                pos + 1 + lenlen + lenbytes, Arrays.copyOfRange(
                    data, pos + 1 + lenlen, pos + 1 + lenlen
                            + lenbytes
                )
            )
        } else if (prefix <= OFFSET_LONG_LIST) {  // [0xc0, 0xf7]
            val len = prefix - OFFSET_SHORT_LIST // length of the encoded list
            val prevPos = pos
            pos++
            decodeList(data, pos, prevPos, len)
        } else if (prefix <= 0xFF) {  // [0xf8, 0xff]
            val lenlen = prefix - OFFSET_LONG_LIST // length of length the encoded list
            val lenlist: Int =
                byteArrayToInt(Arrays.copyOfRange(data, pos + 1, pos + 1 + lenlen)) // length of encoded bytes
            pos = pos + lenlen + 1 // start at position of first element in list
            decodeList(data, pos, lenlist, lenlist)
        } else {
            throw RuntimeException("Only byte values between 0x00 and 0xFF are supported, but got: $prefix")
        }
    }

    fun decodeLazyList(data: ByteArray): LList? {
        return decodeLazyList(data, 0, data.size)!!.getList(0)
    }

    fun decodeLazyList(data: ByteArray?, pos: Int, length: Int): LList? {
        var pos = pos
        if (data == null || data.size < 1) {
            return null
        }
        val ret = LList(data)
        val end = pos + length
        while (pos < end) {
            val prefix = data[pos].toInt() and 0xFF
            if (prefix == OFFSET_SHORT_ITEM) {  // 0x80
                ret.add(pos, 0, false) // means no length or 0
                pos++
            } else if (prefix < OFFSET_SHORT_ITEM) {  // [0x00, 0x7f]
                ret.add(pos, 1, false) // means no length or 0
                pos++
            } else if (prefix <= OFFSET_LONG_ITEM) {  // [0x81, 0xb7]
                val len = prefix - OFFSET_SHORT_ITEM // length of the encoded bytes
                ret.add(pos + 1, len, false)
                pos += len + 1
            } else if (prefix < OFFSET_SHORT_LIST) {  // [0xb8, 0xbf]
                val lenlen = prefix - OFFSET_LONG_ITEM // length of length the encoded bytes
                val lenbytes: Int =
                    byteArrayToInt(Arrays.copyOfRange(data, pos + 1, pos + 1 + lenlen)) // length of encoded bytes
                // check that length is in payload bounds
                verifyLength(lenbytes, data.size - pos - 1 - lenlen)
                ret.add(pos + 1 + lenlen, lenbytes, false)
                pos += 1 + lenlen + lenbytes
            } else if (prefix <= OFFSET_LONG_LIST) {  // [0xc0, 0xf7]
                val len = prefix - OFFSET_SHORT_LIST // length of the encoded list
                ret.add(pos + 1, len, true)
                pos += 1 + len
            } else if (prefix <= 0xFF) {  // [0xf8, 0xff]
                val lenlen = prefix - OFFSET_LONG_LIST // length of length the encoded list
                val lenlist: Int =
                    byteArrayToInt(Arrays.copyOfRange(data, pos + 1, pos + 1 + lenlen)) // length of encoded bytes
                // check that length is in payload bounds
                verifyLength(lenlist, data.size - pos - 1 - lenlen)
                ret.add(pos + 1 + lenlen, lenlist, true)
                pos += 1 + lenlen + lenlist // start at position of first element in list
            } else {
                throw RuntimeException("Only byte values between 0x00 and 0xFF are supported, but got: $prefix")
            }
        }
        return ret
    }

    private fun decodeList(data: ByteArray, pos: Int, prevPos: Int, len: Int): DecodeResult {
        // check that length is in payload bounds
        var pos = pos
        var prevPos = prevPos
        verifyLength(len, data.size - pos)
        val slice: MutableList<Any> = ArrayList()
        var i = 0
        while (i < len) {

            // Get the next item in the data list and append it
            // TODO: val result: DecodeResult? = decode(data, pos)
            val result: DecodeResult = decode(data, pos)!!
            slice.add(result.getDecoded())
            // Increment pos by the amount bytes in the previous read
            prevPos = result.getPos()
            i += prevPos - pos
            pos = prevPos
        }
        return DecodeResult(pos, slice.toTypedArray())
    }
    /* ******************************************************
     *                      ENCODING                        *
     * ******************************************************/
    /**
     * Turn Object into its RLP encoded equivalent of a byte-array
     * Support for String, Integer, BigInteger and Lists of any of these types.
     *
     * @param input as object or List of objects
     * @return byte[] RLP encoded
     */
    fun encode(input: Any?): ByteArray {
        val `val` = Value(input)
        return if (`val`.isList) {
            val inputArray = `val`.asList()
            if (inputArray.isEmpty()) {
                return encodeLength(inputArray.size, OFFSET_SHORT_LIST)
            }
            var output = ByteUtil.EMPTY_BYTE_ARRAY
            for (`object` in inputArray) {
                //output = concatenate(output, encode(`object`))
                output = output.plus(encode(`object`))
            }
            val prefix = encodeLength(output.size, OFFSET_SHORT_LIST)
            //concatenate(prefix, output)
            prefix.plus(output)
        } else {
            val inputAsBytes = toBytes(input)
            if (inputAsBytes.size == 1 && inputAsBytes[0].toInt() and 0xff <= 0x80) {
                inputAsBytes
            } else {
                val firstByte = encodeLength(inputAsBytes.size, OFFSET_SHORT_ITEM)
                //concatenate(firstByte, inputAsBytes)
                firstByte.plus(inputAsBytes)
            }
        }
    }

    /**
     * Integer limitation goes up to 2^31-1 so length can never be bigger than MAX_ITEM_LENGTH
     */
    fun encodeLength(length: Int, offset: Int): ByteArray {
        return if (length < SIZE_THRESHOLD) {
            val firstByte = (length + offset).toByte()
            byteArrayOf(firstByte)
        } else if (length < MAX_ITEM_LENGTH) {
            val binaryLength: ByteArray
            if (length > 0xFF) binaryLength = intToBytesNoLeadZeroes(length) else binaryLength =
                byteArrayOf(length.toByte())
            val firstByte = (binaryLength.size + offset + SIZE_THRESHOLD - 1).toByte()
            //concatenate(byteArrayOf(firstByte), binaryLength)
            byteArrayOf(firstByte).plus(binaryLength)
        } else {
            throw RuntimeException("Input too long")
        }
    }

    fun encodeByte(singleByte: Byte): ByteArray {
        return if (singleByte.toInt() and 0xFF == 0) {
            byteArrayOf(OFFSET_SHORT_ITEM.toByte())
        } else if (singleByte.toInt() and 0xFF <= 0x7F) {
            byteArrayOf(singleByte)
        } else {
            byteArrayOf((OFFSET_SHORT_ITEM + 1).toByte(), singleByte)
        }
    }

    fun encodeShort(singleShort: Short): ByteArray {
        return if (singleShort.toInt() and 0xFF == singleShort.toInt()) encodeByte(singleShort.toByte()) else {
            byteArrayOf(
                (OFFSET_SHORT_ITEM + 2).toByte(),
                (singleShort.toInt() shr 8 and 0xFF).toByte(),
                (singleShort.toInt() shr 0 and 0xFF).toByte()
            )
        }
    }

    fun encodeInt(singleInt: Int): ByteArray {
        return if (singleInt and 0xFF == singleInt) encodeByte(singleInt.toByte()) else if (singleInt and 0xFFFF == singleInt) encodeShort(
            singleInt.toShort()
        ) else if (singleInt and 0xFFFFFF == singleInt) byteArrayOf(
            (OFFSET_SHORT_ITEM + 3).toByte(),
            (singleInt ushr 16).toByte(),
            (singleInt ushr 8).toByte(),
            singleInt.toByte()
        ) else {
            byteArrayOf(
                (OFFSET_SHORT_ITEM + 4).toByte(),
                (singleInt ushr 24).toByte(),
                (singleInt ushr 16).toByte(),
                (singleInt ushr 8).toByte(),
                singleInt.toByte()
            )
        }
    }

    fun encodeString(srcString: String): ByteArray {
        return encodeElement(srcString.toByteArray())
    }

    fun encodeBigInteger(srcBigInteger: BigInteger): ByteArray {
        if (srcBigInteger.compareTo(BigInteger.ZERO) < 0) throw RuntimeException("negative numbers are not allowed")
        return if (srcBigInteger == BigInteger.ZERO) encodeByte(0.toByte()) else encodeElement(
            BigIntegers.asUnsignedByteArray(srcBigInteger)
        )
    }

    fun encodeElement(srcData: ByteArray): ByteArray {

        // [0x80]
        return if (isNullOrZeroArray(srcData)) {
            byteArrayOf(OFFSET_SHORT_ITEM.toByte())

            // [0x00]
        } else if (isSingleZero(srcData)) {
            srcData

            // [0x01, 0x7f] - single byte, that byte is its own RLP encoding
        } else if (srcData.size == 1 && srcData[0].toInt() and 0xFF < 0x80) {
            srcData

            // [0x80, 0xb7], 0 - 55 bytes
        } else if (srcData.size < SIZE_THRESHOLD) {
            // length = 8X
            val length = (OFFSET_SHORT_ITEM + srcData.size).toByte()
            val data = Arrays.copyOf(srcData, srcData.size + 1)
            System.arraycopy(data, 0, data, 1, srcData.size)
            data[0] = length
            data
            // [0xb8, 0xbf], 56+ bytes
        } else {
            // length of length = BX
            // prefix = [BX, [length]]
            var tmpLength = srcData.size
            var lengthOfLength: Byte = 0
            while (tmpLength != 0) {
                ++lengthOfLength
                tmpLength = tmpLength shr 8
            }

            // set length Of length at first byte
            val data = ByteArray(1 + lengthOfLength + srcData.size)
            data[0] = (OFFSET_LONG_ITEM + lengthOfLength).toByte()

            // copy length after first byte
            tmpLength = srcData.size
            for (i in lengthOfLength downTo 1) {
                data[i] = (tmpLength and 0xFF).toByte()
                tmpLength = tmpLength shr 8
            }

            // at last copy the number bytes after its length
            System.arraycopy(srcData, 0, data, 1 + lengthOfLength, srcData.size)
            data
        }
    }

    fun calcElementPrefixSize(srcData: ByteArray): Int {
        return if (isNullOrZeroArray(srcData)) 0 else if (isSingleZero(srcData)) 0 else if (srcData.size == 1 && srcData[0].toInt() and 0xFF < 0x80) {
            0
        } else if (srcData.size < SIZE_THRESHOLD) {
            1
        } else {
            // length of length = BX
            // prefix = [BX, [length]]
            var tmpLength = srcData.size
            var byteNum: Byte = 0
            while (tmpLength != 0) {
                ++byteNum
                tmpLength = tmpLength shr 8
            }
            1 + byteNum
        }
    }

    fun encodeListHeader(size: Int): ByteArray {
        if (size == 0) {
            return byteArrayOf(OFFSET_SHORT_LIST.toByte())
        }
        val header: ByteArray
        if (size < SIZE_THRESHOLD) {
            header = ByteArray(1)
            header[0] = (OFFSET_SHORT_LIST + size).toByte()
        } else {
            // length of length = BX
            // prefix = [BX, [length]]
            var tmpLength = size
            var byteNum: Byte = 0
            while (tmpLength != 0) {
                ++byteNum
                tmpLength = tmpLength shr 8
            }
            tmpLength = size
            val lenBytes = ByteArray(byteNum.toInt())
            for (i in 0 until byteNum) {
                lenBytes[byteNum - 1 - i] = (tmpLength shr 8 * i and 0xFF).toByte()
            }
            // first byte = F7 + bytes.length
            header = ByteArray(1 + lenBytes.size)
            header[0] = (OFFSET_LONG_LIST + byteNum).toByte()
            System.arraycopy(lenBytes, 0, header, 1, lenBytes.size)
        }
        return header
    }

    fun encodeLongElementHeader(length: Int): ByteArray {
        return if (length < SIZE_THRESHOLD) {
            if (length == 0) byteArrayOf(0x80.toByte()) else byteArrayOf((0x80 + length).toByte())
        } else {

            // length of length = BX
            // prefix = [BX, [length]]
            var tmpLength = length
            var byteNum: Byte = 0
            while (tmpLength != 0) {
                ++byteNum
                tmpLength = tmpLength shr 8
            }
            val lenBytes = ByteArray(byteNum.toInt())
            for (i in 0 until byteNum) {
                lenBytes[byteNum - 1 - i] = (length shr 8 * i and 0xFF).toByte()
            }

            // first byte = F7 + bytes.length
            val header = ByteArray(1 + lenBytes.size)
            header[0] = (OFFSET_LONG_ITEM + byteNum).toByte()
            System.arraycopy(lenBytes, 0, header, 1, lenBytes.size)
            header
        }
    }

    fun encodeSet(data: Set<ByteArrayWrapper>): ByteArray {
        var dataLength = 0
        val encodedElements: MutableSet<ByteArray> = HashSet()
        for (element in data) {
            val encodedElement = encodeElement(element.data)
            dataLength += encodedElement.size
            encodedElements.add(encodedElement)
        }
        val listHeader = encodeListHeader(dataLength)
        val output = ByteArray(listHeader.size + dataLength)
        System.arraycopy(listHeader, 0, output, 0, listHeader.size)
        var cummStart = listHeader.size
        for (element in encodedElements) {
            System.arraycopy(element, 0, output, cummStart, element.size)
            cummStart += element.size
        }
        return output
    }

    /**
     * A handy shortcut for [.encodeElement] + [.encodeList]
     *
     *
     * Encodes each data element and wraps them all into a list.
     */
    fun wrapList(vararg data: ByteArray): ByteArray {
        val elements = Array(data.size) { encodeElement(data[it]) }
        return encodeList(*elements)
    }

    fun encodeList(vararg elements: ByteArray): ByteArray {
        if (elements == null) {
            return byteArrayOf(OFFSET_SHORT_LIST.toByte())
        }
        var totalLength = 0
        for (element1 in elements) {
            totalLength += element1.size
        }
        val data: ByteArray
        var copyPos: Int
        if (totalLength < SIZE_THRESHOLD) {
            data = ByteArray(1 + totalLength)
            data[0] = (OFFSET_SHORT_LIST + totalLength).toByte()
            copyPos = 1
        } else {
            // length of length = BX
            // prefix = [BX, [length]]
            var tmpLength = totalLength
            var byteNum: Byte = 0
            while (tmpLength != 0) {
                ++byteNum
                tmpLength = tmpLength shr 8
            }
            tmpLength = totalLength
            val lenBytes = ByteArray(byteNum.toInt())
            for (i in 0 until byteNum) {
                lenBytes[byteNum - 1 - i] = (tmpLength shr 8 * i and 0xFF).toByte()
            }
            // first byte = F7 + bytes.length
            data = ByteArray(1 + lenBytes.size + totalLength)
            data[0] = (OFFSET_LONG_LIST + byteNum).toByte()
            System.arraycopy(lenBytes, 0, data, 1, lenBytes.size)
            copyPos = lenBytes.size + 1
        }
        for (element in elements) {
            System.arraycopy(element, 0, data, copyPos, element.size)
            copyPos += element.size
        }
        return data
    }

    /*
     *  Utility function to convert Objects into byte arrays
     */
    private fun toBytes(input: Any?): ByteArray {
        if (input is ByteArray) {
            return input
        } else if (input is String) {
            return input.toByteArray()
        } else if (input is Long) {
            val inputLong = input
            return if (inputLong == 0L) ByteUtil.EMPTY_BYTE_ARRAY else BigIntegers.asUnsignedByteArray(
                BigInteger.valueOf(
                    inputLong
                )
            )
        } else if (input is Int) {
            val inputInt = input
            return if (inputInt == 0) ByteUtil.EMPTY_BYTE_ARRAY else BigIntegers.asUnsignedByteArray(
                BigInteger.valueOf(
                    inputInt.toLong()
                )
            )
        } else if (input is BigInteger) {
            val inputBigInt = input
            return if (inputBigInt == BigInteger.ZERO) ByteUtil.EMPTY_BYTE_ARRAY else BigIntegers.asUnsignedByteArray(
                inputBigInt
            )
        } else if (input is Value) {
            return toBytes(input.asObj())
        }
        throw RuntimeException("Unsupported type: Only accepting String, Integer and BigInteger for now")
    }

    private fun decodeItemBytes(data: ByteArray, index: Int): ByteArray {
        val length = calculateItemLength(data, index)
        // [0x80]
        return if (length == 0) {
            ByteArray(0)

            // [0x00, 0x7f] - single byte with item
        } else if (data[index].toInt() and 0xFF < OFFSET_SHORT_ITEM) {
            val valueBytes = ByteArray(1)
            System.arraycopy(data, index, valueBytes, 0, 1)
            valueBytes

            // [0x01, 0xb7] - 1-55 bytes item
        } else if (data[index].toInt() and 0xFF <= OFFSET_LONG_ITEM) {
            val valueBytes = ByteArray(length)
            System.arraycopy(data, index + 1, valueBytes, 0, length)
            valueBytes

            // [0xb8, 0xbf] - 56+ bytes item
        } else if (data[index].toInt() and 0xFF > OFFSET_LONG_ITEM
            && data[index].toInt() and 0xFF < OFFSET_SHORT_LIST
        ) {
            val lengthOfLength = (data[index] - OFFSET_LONG_ITEM).toByte()
            val valueBytes = ByteArray(length)
            System.arraycopy(data, index + 1 + lengthOfLength, valueBytes, 0, length)
            valueBytes
        } else {
            throw RuntimeException("wrong decode attempt")
        }
    }

    private fun calculateItemLength(data: ByteArray, index: Int): Int {

        // [0xb8, 0xbf] - 56+ bytes item
        return if (data[index].toInt() and 0xFF > OFFSET_LONG_ITEM
            && data[index].toInt() and 0xFF < OFFSET_SHORT_LIST
        ) {
            val lengthOfLength = (data[index] - OFFSET_LONG_ITEM).toByte()
            calcLength(lengthOfLength.toInt(), data, index)

            // [0x81, 0xb7] - 0-55 bytes item
        } else if (data[index].toInt() and 0xFF > OFFSET_SHORT_ITEM
            && data[index].toInt() and 0xFF <= OFFSET_LONG_ITEM
        ) {
            (data[index] - OFFSET_SHORT_ITEM).toByte().toInt()

            // [0x80] - item = 0 itself
        } else if (data[index].toInt() and 0xFF == OFFSET_SHORT_ITEM) {
            0.toByte().toInt()

            // [0x00, 0x7f] - 1 byte item, no separate length representation
        } else if (data[index].toInt() and 0xFF < OFFSET_SHORT_ITEM) {
            1.toByte().toInt()
        } else {
            throw RuntimeException("wrong decode attempt")
        }
    }

    class LList(private val rlp: ByteArray) {
        private val offsets = IntArray(32)
        private val lens = IntArray(32)
        private var cnt = 0
//        val encoded: ByteArray
//            get() {
//                val encoded = arrayOfNulls<ByteArray>(cnt)
//                for (i in 0 until cnt) {
//                    encoded[i] = encodeElement(getBytes(i))
//                }
//                return encodeList(*encoded)
//            }

        val encoded: ByteArray
            get() {
                val encoded = Array(cnt) { encodeElement(getBytes(it)) }
                return encodeList(*encoded)
            }

        fun add(off: Int, len: Int, isList: Boolean) {
            offsets[cnt] = off
            lens[cnt] = if (isList) -1 - len else len
            cnt++
        }

        fun getBytes(idx: Int): ByteArray {
            var len = lens[idx]
            len = if (len < 0) -len - 1 else len
            val ret = ByteArray(len)
            System.arraycopy(rlp, offsets[idx], ret, 0, len)
            return ret
        }

        fun getList(idx: Int): LList? {
            return decodeLazyList(rlp, offsets[idx], -lens[idx] - 1)
        }

        fun isList(idx: Int): Boolean {
            return lens[idx] < 0
        }

        fun size(): Int {
            return cnt
        }
    }
}

class DecodeResult(private val pos: Int, private val decoded: Any) : Serializable {

    fun getPos(): Int {
        return pos
    }

    fun getDecoded(): Any {
        return decoded
    }

    override fun toString(): String {
        return asString(decoded)
    }

    private fun asString(decoded: Any): String {
        return when (decoded) {
            is String -> decoded
            is ByteArray -> Numeric.toHexStringNoPrefix(decoded)
            is Array<*> -> {
                val result = StringBuilder()
                for (item in decoded) {
                    result.append(asString(item!!))
                }
                result.toString()
            }

            else -> throw RuntimeException("Not a valid type. Should not occur")
        }
    }
}

