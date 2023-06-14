package com.r3.corda.evmbridge.evm.utils

import CompactEncoder
import org.ethereum.util.ByteUtil
import com.r3.corda.evmbridge.evm.rlp.RLP
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import java.math.BigInteger
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Class to encapsulate an object and provide utilities for conversion
 */
class Value {
    private var value: Any? = null
    private var rlp: ByteArray? = null
    private var sha3: ByteArray? = null
    private var decoded = false

    companion object {
        @JvmStatic
        fun fromRlpEncoded(data: ByteArray?): Value? {
            return if (data != null && data.isNotEmpty()) {
                val v = Value()
                v.init(data)
                v
            } else {
                null
            }
        }
    }

    constructor()

    fun init(rlp: ByteArray?) {//=> fun init(rlp: ByteArray) {
        this.rlp = rlp
    }

    constructor(obj: Any?) {
        decoded = true
        if (obj == null) return
        if (obj is Value) {
            value = obj.asObj()
        } else {
            value = obj
        }
    }

    fun withHash(hash: ByteArray?): Value {
        sha3 = hash
        return this
    }

    /* *****************
     *      Convert
     * *****************/
    fun asObj(): Any? {
        decode()
        return value
    }

    //    fun asList(): List<Any?> {
//        decode()
//        val valueArray = value as Array<Any>?
//        return Arrays.asList(*valueArray)
//    }
    fun asList(): List<Any?> {
        decode()
        val valueArray = value as Array<*>
        return valueArray.asList()
    }

    fun asInt(): Int {
        decode()
        if (isInt) {
            return value as Int
        } else if (isBytes) {
            return BigInteger(1, asBytes()).toInt()
        }
        return 0
    }

    fun asLong(): Long {
        decode()
        if (isLong) {
            return value as Long
        } else if (isBytes) {
            return BigInteger(1, asBytes()).toLong()
        }
        return 0
    }

    fun asBigInt(): BigInteger? {
        decode()
        return value as BigInteger?
    }

    fun asString(): String {
        decode()
        if (isBytes) {
            return String((value as ByteArray?)!!)
        } else if (isString) {
            return value as String
        }
        return ""
    }

    fun asBytes(): ByteArray? {
        decode()
        if (isBytes) {
            return value as ByteArray?
        } else if (isString) {
            return asString().toByteArray()
        }
        return ByteUtil.EMPTY_BYTE_ARRAY
    }

    val hex: String
        get() = Numeric.toHexStringNoPrefix(encode())
    val data: ByteArray
        get() = encode()

    fun asSlice(): IntArray? {
        return value as IntArray?
    }

    operator fun get(index: Int): Value {
        if (isList) {
            // Guard for OutOfBounds
            if (asList().size <= index) {
                return Value(null)
            }
            if (index < 0) {
                throw RuntimeException("Negative index not allowed")
            }
            return Value(asList()[index])
        }
        // If this wasn't a slice you probably shouldn't be using this function
        return Value(null)
    }

    /* *****************
     *      Utility
     * *****************/
    fun decode() {
        if (!decoded) {
            //value = RLP.decode(rlp, 0).getDecoded()
            value = RLP.decode(rlp, 0)?.getDecoded() // TODO: nullable?
            decoded = true
        }
    }

    fun encode(): ByteArray {
        if (rlp == null) rlp = RLP.encode(value)
        return rlp!!
    }

    fun hash(): ByteArray {
        if (sha3 == null) sha3 = Hash.sha3(encode())
        return sha3!!
    }

    fun cmp(o: Value?): Boolean {
        return deepEquals(this, o)
    }

    /**/
    fun deepEquals(a: Any?, b: Any?): Boolean {
        if (a === b) return true
        if (a == null || b == null) return false
        if (a.javaClass != b.javaClass) return false

        val aProperties = a.javaClass.kotlin.memberProperties
        val bProperties = b.javaClass.kotlin.memberProperties

        for (aProperty in aProperties) {
            val bProperty = bProperties.find { it.name == aProperty.name } ?: return false
            aProperty.isAccessible = true
            bProperty.isAccessible = true
            val aValue = aProperty.get(a)
            val bValue = bProperty.get(b)

            when {
                aValue is Collection<*> && bValue is Collection<*> -> {
                    if (aValue.size != bValue.size) return false
                    aValue.forEachIndexed { index, element ->
                        if (!deepEquals(element, bValue.elementAt(index))) return false
                    }
                }

                aValue is Map<*, *> && bValue is Map<*, *> -> {
                    if (aValue.size != bValue.size) return false
                    aValue.forEach { (key, value) ->
                        if (!deepEquals(value, bValue[key])) return false
                    }
                }

                aValue is Array<*> && bValue is Array<*> -> {
                    if (aValue.size != bValue.size) return false
                    aValue.forEachIndexed { index, element ->
                        if (!deepEquals(element, bValue[index])) return false
                    }
                }

                else -> {
                    if (!deepEquals(aValue, bValue)) return false
                }
            }
        }
        return true
    }
    /**/

    /* *****************
     *      Checks
     * *****************/

    val isList: Boolean
        get() {
            decode()
            return value != null &&
                    value?.javaClass?.isArray == true &&
                    value?.javaClass?.componentType?.isPrimitive == false
        }
    val isString: Boolean
        get() {
            decode()
            return value is String
        }
    val isInt: Boolean
        get() {
            decode()
            return value is Int
        }
    val isLong: Boolean
        get() {
            decode()
            return value is Long
        }
    val isBigInt: Boolean
        get() {
            decode()
            return value is BigInteger
        }
    val isBytes: Boolean
        get() {
            decode()
            return value is ByteArray
        }
    val isReadableString: Boolean
        // it's only if the isBytes() = true;
        get() {
            decode()
            var readableChars = 0
            val data = value as ByteArray?
            if (data!!.size == 1 && data[0] > 31 && data[0] < 126) {
                return true
            }
            for (aData in data) {
                if (aData > 32 && aData < 126) ++readableChars
            }
            return readableChars.toDouble() / data.size.toDouble() > 0.55
        }
    val isHexString: Boolean
        // it's only if the isBytes() = true;
        get() {
            decode()
            var hexChars = 0
            val data = value as ByteArray?
            for (aData in data!!) {
                if (aData >= 48 && aData <= 57 || aData >= 97 && aData <= 102) ++hexChars
            }
            return hexChars.toDouble() / data.size.toDouble() > 0.9
        }
    val isHashCode: Boolean
        get() {
            decode()
            return asBytes()!!.size == 32
        }
    val isNull: Boolean
        get() {
            decode()
            return value == null
        }
    val isEmpty: Boolean
        get() {
            decode()
            if (isNull) return true
            if (isBytes && asBytes()!!.size == 0) return true
            if (isList && asList().isEmpty()) return true
            return isString && asString().isEmpty()
        }

    fun length(): Int {
        decode()
        if (isList) {
            return asList().size
        } else if (isBytes) {
            return asBytes()!!.size
        } else if (isString) {
            return asString().length
        }
        return 0
    }

    override fun toString(): String {
        decode()
        val stringBuilder = StringBuilder()
        if (isList) {
            val list = value as Array<Any>?

            // special case - key/value node
            if (list!!.size == 2) {
                stringBuilder.append("[ ")
                val key = Value(list[0])
                val keyNibbles: ByteArray = CompactEncoder.binToNibblesNoTerminator(key.asBytes()!!)
                val keyString: String = ByteUtil.nibblesToPrettyString(keyNibbles)
                stringBuilder.append(keyString)
                stringBuilder.append(",")
                val `val` = Value(list[1])
                stringBuilder.append(`val`.toString())
                stringBuilder.append(" ]")
                return stringBuilder.toString()
            }
            stringBuilder.append(" [")
            for (i in list.indices) {
                val `val` = Value(list[i])
                if (`val`.isString || `val`.isEmpty) {
                    stringBuilder.append("'").append(`val`.toString()).append("'")
                } else {
                    stringBuilder.append(`val`.toString())
                }
                if (i < list.size - 1) stringBuilder.append(", ")
            }
            stringBuilder.append("] ")
            return stringBuilder.toString()
        } else if (isEmpty) {
            return ""
        } else if (isBytes) {
            val output = StringBuilder()
            if (isHashCode) {
                output.append(Numeric.toHexStringNoPrefix(asBytes()))
            } else if (isReadableString) {
                output.append("'")
                for (oneByte in asBytes()!!) {
                    if (oneByte < 16) {
                        //output.append("\\x").append(ByteUtil.oneByteToHexString(oneByte))
                        output.append("\\x").append(Numeric.toHexStringNoPrefix(byteArrayOf(oneByte)))
                    } else {
                        output.append(Character.valueOf(oneByte.toChar()))
                    }
                }
                output.append("'")
                return output.toString()
            }
            return Numeric.toHexStringNoPrefix(asBytes())
        } else if (isString) {
            return asString()
        }
        return "Unexpected type"
    }

    fun countBranchNodes(): Int {
        decode()
        if (isList) {
            val objList = this.asList()
            var i = 0
            for (obj in objList) {
                i += Value(obj).countBranchNodes()
            }
            return i
        } else if (isBytes) {
            asBytes()
        }
        return 0
    }
}