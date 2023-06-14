package org.ethereum.util

/**
 * Utility code to do optimized byte-array comparison.
 * This is borrowed and slightly modified from Guava's [UnsignedBytes]
 * class to be able to compare arrays that start at non-zero offsets.
 */
object FastByteComparisons {
    fun equal(b1: ByteArray, b2: ByteArray): Boolean {
        return b1.size == b2.size && compareTo(b1, 0, b1.size, b2, 0, b2.size) == 0
    }

    /**
     * Lexicographically compare two byte arrays.
     *
     * @param b1 buffer1
     * @param s1 offset1
     * @param l1 length1
     * @param b2 buffer2
     * @param s2 offset2
     * @param l2 length2
     * @return int
     */
    fun compareTo(b1: ByteArray, s1: Int, l1: Int, b2: ByteArray, s2: Int, l2: Int): Int {
        return LexicographicalComparerHolder.BEST_COMPARER.compareTo(b1, s1, l1, b2, s2, l2)
    }

    private interface Comparer<T> {
        fun compareTo(buffer1: T, offset1: Int, length1: Int, buffer2: T, offset2: Int, length2: Int): Int
    }

    private fun lexicographicalComparerJavaImpl(): Comparer<ByteArray> {
        return LexicographicalComparerHolder.PureJavaComparer.INSTANCE
    }

    /**
     * <p>Uses reflection to gracefully fall back to the Java implementation if
     * {@code Unsafe} isn't available.
     */
    private object LexicographicalComparerHolder {
        const val UNSAFE_COMPARER_NAME =
            "org.ethereum.util.FastByteComparisons\$LexicographicalComparerHolder\$UnsafeComparer"

        val BEST_COMPARER: Comparer<ByteArray> = getBestComparer()

        /**
         * Returns the Unsafe-using Comparer, or falls back to the pure-Java
         * implementation if unable to do so.
         */
        private fun getBestComparer(): Comparer<ByteArray> {
            return try {
                val theClass = Class.forName(UNSAFE_COMPARER_NAME)

                // yes, UnsafeComparer does implement Comparer<byte[]>
                theClass.enumConstants?.first() as Comparer<ByteArray>
            } catch (t: Throwable) { // ensure we really catch *everything*
                lexicographicalComparerJavaImpl()
            }
        }

        enum class PureJavaComparer : Comparer<ByteArray> {
            INSTANCE;

            override fun compareTo(
                buffer1: ByteArray, offset1: Int, length1: Int,
                buffer2: ByteArray, offset2: Int, length2: Int
            ): Int {
                // Short circuit equal case
                if (buffer1 === buffer2 && offset1 == offset2 && length1 == length2) {
                    return 0
                }
                val end1 = offset1 + length1
                val end2 = offset2 + length2
                for (i in offset1 until end1.coerceAtMost(end2)) {
                    val a = (buffer1[i].toInt() and 0xff)
                    val b = (buffer2[i].toInt() and 0xff)
                    if (a != b) {
                        return a - b
                    }
                }
                return length1 - length2
            }
        }
    }
}
