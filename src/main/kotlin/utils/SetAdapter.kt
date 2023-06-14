package org.ethereum.util

class SetAdapter<E>(private val delegate: MutableMap<E, Any?>) : MutableSet<E> {

    companion object {
        private val dummyValue = Any()
    }

    override val size: Int
        get() = delegate.size

    override fun isEmpty() = delegate.isEmpty()

    override fun contains(element: E) = delegate.containsKey(element)

    override fun iterator() = delegate.keys.iterator()

    //fun toArray() = delegate.keys.toTypedArray()

    //fun <T : Any?> toArray(array: Array<T>): Array<T> = delegate.keys.toTypedArray(array)

    override fun add(element: E) = delegate.put(element, dummyValue) == null

    override fun remove(element: E) = delegate.remove(element) != null

    override fun containsAll(elements: Collection<E>) = delegate.keys.containsAll(elements)

    override fun addAll(elements: Collection<E>): Boolean {
        var result = false
        for (element in elements) {
            result = add(element) || result
        }
        return result
    }

    override fun retainAll(elements: Collection<E>) = unsupportedOperation()

    override fun removeAll(elements: Collection<E>): Boolean {
        var result = false
        for (element in elements) {
            result = remove(element) || result
        }
        return result
    }

    override fun clear() = delegate.clear()

    private fun unsupportedOperation(): Nothing {
        throw UnsupportedOperationException("Not implemented")
    }
}