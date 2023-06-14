package com.r3.corda.evmbridge.evm.utils

import org.ethereum.db.ByteArrayWrapper
import org.ethereum.util.ByteArraySet
import java.util.*

class ByteArrayMap<V> : MutableMap<ByteArray, V> {
    private val delegate: MutableMap<ByteArrayWrapper, V>

    constructor() : this(HashMap())

    constructor(delegate: MutableMap<ByteArrayWrapper, V>) {
        this.delegate = delegate
    }

    override val size: Int
        get() = delegate.size

    override fun isEmpty(): Boolean = delegate.isEmpty()

    override fun containsKey(key: ByteArray): Boolean =
        delegate.containsKey(ByteArrayWrapper(key))

    override fun containsValue(value: V): Boolean = delegate.containsValue(value)

    override operator fun get(key: ByteArray): V? =
        delegate[ByteArrayWrapper(key)]

    override fun put(key: ByteArray, value: V): V? =
        delegate.put(ByteArrayWrapper(key), value)

    override fun remove(key: ByteArray): V? =
        delegate.remove(ByteArrayWrapper(key))

    override fun putAll(from: Map<out ByteArray, out V>) =
        from.forEach { (k, v) -> delegate[ByteArrayWrapper(k)] = v }

    override fun clear() = delegate.clear()

    override val keys: MutableSet<ByteArray>
        get() = ByteArraySet(delegate.keys)

    override val values: MutableCollection<V>
        get() = delegate.values

    override val entries: MutableSet<MutableMap.MutableEntry<ByteArray, V>>
        get() = MapEntrySet(delegate.entries)

    override fun equals(other: Any?): Boolean = delegate == other

    override fun hashCode(): Int = delegate.hashCode()

    override fun toString(): String = delegate.toString()

    private inner class MapEntrySet(
        private val delegate: Set<Map.Entry<ByteArrayWrapper, V>>
    ) : MutableSet<MutableMap.MutableEntry<ByteArray, V>> {

        override val size: Int
            get() = delegate.size

        override fun isEmpty(): Boolean = delegate.isEmpty()

        override fun contains(element: MutableMap.MutableEntry<ByteArray, V>): Boolean =
            throw RuntimeException("Not implemented")

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<ByteArray, V>> {
            val it = delegate.iterator()
            return object : MutableIterator<MutableMap.MutableEntry<ByteArray, V>> {
                override fun hasNext(): Boolean = it.hasNext()

                override fun next(): MutableMap.MutableEntry<ByteArray, V> {
                    val next = it.next()
                    return object : MutableMap.MutableEntry<ByteArray, V> {
                        override val key: ByteArray
                            get() = next.key.data

                        override val value: V
                            get() = next.value

                        override fun setValue(newValue: V): V =
                            throw UnsupportedOperationException()
                    }
                }

                override fun remove(): Unit {
                    throw UnsupportedOperationException()
                }
                //override fun remove() = it.remove()
            }
        }

//        override fun toArray(): Array<Any?> = throw RuntimeException("Not implemented")
//
//        override fun <T : Any?> toArray(a: Array<T>): Array<T> =
//            throw RuntimeException("Not implemented")

        override fun add(element: MutableMap.MutableEntry<ByteArray, V>): Boolean =
            throw RuntimeException("Not implemented")

        override fun remove(element: MutableMap.MutableEntry<ByteArray, V>): Boolean =
            throw RuntimeException("Not implemented")

        override fun containsAll(elements: Collection<MutableMap.MutableEntry<ByteArray, V>>): Boolean =
            throw RuntimeException("Not implemented")

        override fun addAll(elements: Collection<MutableMap.MutableEntry<ByteArray, V>>): Boolean =
            throw RuntimeException("Not implemented")

        override fun retainAll(elements: Collection<MutableMap.MutableEntry<ByteArray, V>>): Boolean =
            throw RuntimeException("Not implemented")

        override fun removeAll(elements: Collection<MutableMap.MutableEntry<ByteArray, V>>): Boolean =
            throw RuntimeException("Not implemented")

        override fun clear() = throw RuntimeException("Not implemented")
    }
}


//package org.ethereum.util
//
//import org.ethereum.db.ByteArrayWrapper
//import java.util.*
//import java.util.AbstractMap.SimpleImmutableEntry
//
//class ByteArrayMap<V> @JvmOverloads constructor(private val delegate: MutableMap<ByteArrayWrapper?, V> = HashMap()) :
//    MutableMap<ByteArray?, V> {
//    override fun size(): Int {
//        return delegate.size
//    }
//
//    override fun isEmpty(): Boolean {
//        return delegate.isEmpty()
//    }
//
//    override fun containsKey(key: Any): Boolean {
//        return delegate.containsKey(ByteArrayWrapper(key as ByteArray))
//    }
//
//    override fun containsValue(value: Any): Boolean {
//        return delegate.containsValue(value)
//    }
//
//    override operator fun get(key: Any): V {
//        return delegate[ByteArrayWrapper(key as ByteArray)]
//    }
//
//    override fun put(key: ByteArray, value: V): V {
//        return delegate.put(ByteArrayWrapper(key), value)
//    }
//
//    override fun remove(key: Any): V {
//        return delegate.remove(ByteArrayWrapper(key as ByteArray))
//    }
//
//    override fun putAll(m: Map<out ByteArray, V>) {
//        for ((key, value) in m) {
//            delegate[ByteArrayWrapper(key)] = value
//        }
//    }
//
//    override fun clear() {
//        delegate.clear()
//    }
//
//    override fun keySet(): Set<ByteArray> {
//        return ByteArraySet(SetAdapter<E?>(delegate))
//    }
//
//    override fun values(): Collection<V> {
//        return delegate.values
//    }
//
//    override fun entrySet(): Set<Map.Entry<ByteArray, V>> {
//        return MapEntrySet(delegate.entries)
//    }
//
//    override fun equals(o: Any?): Boolean {
//        return delegate == o
//    }
//
//    override fun hashCode(): Int {
//        return delegate.hashCode()
//    }
//
//    override fun toString(): String {
//        return delegate.toString()
//    }
//
//    private inner class MapEntrySet private constructor(private val delegate: MutableSet<Map.Entry<ByteArrayWrapper, V>>) :
//        MutableSet<Map.Entry<ByteArray?, V>?> {
//        override fun size(): Int {
//            return delegate.size
//        }
//
//        override fun isEmpty(): Boolean {
//            return delegate.isEmpty()
//        }
//
//        override operator fun contains(o: Any): Boolean {
//            throw RuntimeException("Not implemented")
//        }
//
//        override fun iterator(): MutableIterator<Map.Entry<ByteArray, V>> {
//            val it = delegate.iterator()
//            return object : MutableIterator<Map.Entry<ByteArray?, V>?> {
//                override fun hasNext(): Boolean {
//                    return it.hasNext()
//                }
//
//                override fun next(): Map.Entry<ByteArray?, V>? {
//                    val (key, value) = it.next()
//                    return SimpleImmutableEntry<Any?, Any?>(key.data, value)
//                }
//
//                override fun remove() {
//                    it.remove()
//                }
//            }
//        }
//
//        override fun toArray(): Array<Any> {
//            throw RuntimeException("Not implemented")
//        }
//
//        override fun <T> toArray(a: Array<T>): Array<T> {
//            throw RuntimeException("Not implemented")
//        }
//
//        override fun add(vEntry: Map.Entry<ByteArray?, V>): Boolean {
//            throw RuntimeException("Not implemented")
//        }
//
//        override fun remove(o: Any): Boolean {
//            throw RuntimeException("Not implemented")
//        }
//
//        override fun containsAll(c: Collection<*>?): Boolean {
//            throw RuntimeException("Not implemented")
//        }
//
//        override fun addAll(c: Collection<Map.Entry<ByteArray?, V>>): Boolean {
//            throw RuntimeException("Not implemented")
//        }
//
//        override fun retainAll(c: Collection<*>?): Boolean {
//            throw RuntimeException("Not implemented")
//        }
//
//        override fun removeAll(c: Collection<*>?): Boolean {
//            throw RuntimeException("Not implemented")
//        }
//
//        override fun clear() {
//            throw RuntimeException("Not implemented")
//        }
//    }
//}