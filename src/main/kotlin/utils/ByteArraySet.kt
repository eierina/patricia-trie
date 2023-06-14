package org.ethereum.util

import org.ethereum.db.ByteArrayWrapper

//class ByteArraySet(delegate: MutableSet<ByteArrayWrapper> = HashSet()) : MutableSet<ByteArray> {
class ByteArraySet(delegate: MutableSet<ByteArrayWrapper> = HashSet()) : MutableSet<ByteArray> {

    private val delegate = delegate

    override val size: Int
        get() = delegate.size

    override fun isEmpty() = delegate.isEmpty()

    override fun contains(element: ByteArray) = delegate.contains(ByteArrayWrapper(element))

    override fun iterator(): MutableIterator<ByteArray> = object : MutableIterator<ByteArray> {

        val it = delegate.iterator()

        override fun hasNext() = it.hasNext()

        override fun next() = it.next().data

        override fun remove() = it.remove()
    }

    override fun add(element: ByteArray) = delegate.add(ByteArrayWrapper(element))

    override fun remove(element: ByteArray) = delegate.remove(ByteArrayWrapper(element))

    override fun containsAll(elements: Collection<ByteArray>) = elements.all { contains(it) }

    override fun addAll(elements: Collection<ByteArray>): Boolean {
        var changed = false
        elements.forEach { changed = changed or add(it) }
        return changed
    }

    override fun removeAll(elements: Collection<ByteArray>): Boolean {
        var changed = false
        elements.forEach { changed = changed or remove(it) }
        return changed
    }

    override fun retainAll(elements: Collection<ByteArray>) =
        delegate.retainAll(elements.map { ByteArrayWrapper(it) })

    override fun clear() = delegate.clear()

    override fun equals(other: Any?) = throw UnsupportedOperationException()

    override fun hashCode() = throw UnsupportedOperationException()
}

//package org.ethereum.util
//
//import org.ethereum.db.ByteArrayWrapper
//import java.util.*
//
//class ByteArraySet internal constructor(var delegate: MutableSet<ByteArrayWrapper>) : MutableSet<ByteArray?> {
//    constructor() : this(HashSet<ByteArrayWrapper>())
//
//    override fun size(): Int {
//        return delegate.size
//    }
//
//    override fun isEmpty(): Boolean {
//        return delegate.isEmpty()
//    }
//
//    override operator fun contains(o: ByteArray?): Boolean {
//        return delegate.contains(ByteArrayWrapper(o as ByteArray))
//    }
//
//    override fun iterator(): MutableIterator<ByteArray> {
//        return object : MutableIterator<ByteArray?> {
//            var it = delegate.iterator()
//            override fun hasNext(): Boolean {
//                return it.hasNext()
//            }
//
//            override fun next(): ByteArray {
//                return it.next().data
//            }
//
//            override fun remove() {
//                it.remove()
//            }
//        }
//    }
//
//    override fun toArray(): Array<Any> {
//        val ret = arrayOfNulls<ByteArray>(size)
//        val arr = delegate.toTypedArray<ByteArrayWrapper>()
//        for (i in arr.indices) {
//            ret[i] = arr[i].data
//        }
//        return ret
//    }
//
//    override fun <T> toArray(a: Array<T>): Array<T> {
//        return kotlin.collections.toTypedArray() as Array<T>
//    }
//
//    override fun add(bytes: ByteArray): Boolean {
//        return delegate.add(ByteArrayWrapper(bytes))
//    }
//
//    override fun remove(o: Any): Boolean {
//        return delegate.remove(ByteArrayWrapper(o as ByteArray))
//    }
//
//    override fun containsAll(c: Collection<*>?): Boolean {
//        throw RuntimeException("Not implemented")
//    }
//
//    override fun addAll(c: Collection<ByteArray>): Boolean {
//        var ret = false
//        for (bytes in c) {
//            ret = ret or add(bytes)
//        }
//        return ret
//    }
//
//    override fun retainAll(c: Collection<*>?): Boolean {
//        throw RuntimeException("Not implemented")
//    }
//
//    override fun removeAll(c: Collection<*>): Boolean {
//        var changed = false
//        for (el in c) {
//            changed = changed or remove(el)
//        }
//        return changed
//    }
//
//    override fun clear() {
//        delegate.clear()
//    }
//
//    override fun equals(o: Any?): Boolean {
//        throw RuntimeException("Not implemented")
//    }
//
//    override fun hashCode(): Int {
//        throw RuntimeException("Not implemented")
//    }
//}