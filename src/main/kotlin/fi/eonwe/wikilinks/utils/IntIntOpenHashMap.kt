package fi.eonwe.wikilinks.utils

import kotlin.math.ceil
import kotlin.math.max

class IntIntOpenHashMap(
    initialCapacity: Int = 16,
    private val missingValue: Int = Int.MIN_VALUE,
    private val loadFactor: Double = 0.65
) {
    private var keys: IntArray
    private var values: IntArray
    private var occupied: BooleanArray
    private var threshold: Int

    var size: Int = 0
        private set

    init {
        require(loadFactor in 0.1..0.95) { "loadFactor must be within [0.1, 0.95]" }
        val capacity = tableSizeFor(max(4, ceil(initialCapacity / loadFactor).toInt()))
        keys = IntArray(capacity)
        values = IntArray(capacity)
        occupied = BooleanArray(capacity)
        threshold = (capacity * loadFactor).toInt()
    }

    fun getOrDefault(key: Int, defaultValue: Int): Int {
        val index = findIndex(key)
        return if (index >= 0) values[index] else defaultValue
    }

    fun containsKey(key: Int): Boolean {
        return findIndex(key) >= 0
    }

    fun put(key: Int, value: Int): Int {
        ensureCapacityForInsert()
        return putInternal(key, value, onlyIfAbsent = false)
    }

    fun putIfAbsent(key: Int, value: Int): Int {
        ensureCapacityForInsert()
        return putInternal(key, value, onlyIfAbsent = true)
    }

    fun addValue(key: Int, delta: Int, initialValue: Int): Int {
        ensureCapacityForInsert()
        val mask = keys.size - 1
        var index = mix(key) and mask
        while (occupied[index]) {
            if (keys[index] == key) {
                values[index] += delta
                return values[index]
            }
            index = (index + 1) and mask
        }
        occupied[index] = true
        keys[index] = key
        values[index] = initialValue + delta
        size++
        return values[index]
    }

    fun forEach(consumer: (Int, Int) -> Unit) {
        for (i in occupied.indices) {
            if (occupied[i]) {
                consumer(keys[i], values[i])
            }
        }
    }

    private fun putInternal(key: Int, value: Int, onlyIfAbsent: Boolean): Int {
        val mask = keys.size - 1
        var index = mix(key) and mask
        while (occupied[index]) {
            if (keys[index] == key) {
                val previous = values[index]
                if (!onlyIfAbsent) {
                    values[index] = value
                }
                return previous
            }
            index = (index + 1) and mask
        }

        occupied[index] = true
        keys[index] = key
        values[index] = value
        size++
        return missingValue
    }

    private fun findIndex(key: Int): Int {
        val mask = keys.size - 1
        var index = mix(key) and mask
        while (occupied[index]) {
            if (keys[index] == key) {
                return index
            }
            index = (index + 1) and mask
        }
        return -1
    }

    private fun ensureCapacityForInsert() {
        if (size + 1 <= threshold) {
            return
        }
        rehash(keys.size shl 1)
    }

    private fun rehash(newCapacity: Int) {
        val oldKeys = keys
        val oldValues = values
        val oldOccupied = occupied
        keys = IntArray(newCapacity)
        values = IntArray(newCapacity)
        occupied = BooleanArray(newCapacity)
        threshold = (newCapacity * loadFactor).toInt()
        size = 0
        for (i in oldOccupied.indices) {
            if (oldOccupied[i]) {
                putInternal(oldKeys[i], oldValues[i], onlyIfAbsent = false)
            }
        }
    }

    companion object {
        /**
         * Small integer mixer based on MurmurHash3-style finalization steps.
         *
         * The constants are avalanche multipliers commonly used in non-cryptographic hash mixing:
         * they spread low-entropy integer keys so linear-probing clusters less around nearby keys.
         */
        private fun mix(key: Int): Int {
            var h = key
            h = h xor (h ushr 16)
            h *= -0x7a143595
            h = h xor (h ushr 13)
            h *= -0x3d4d51cb
            return h xor (h ushr 16)
        }

        private fun tableSizeFor(capacity: Int): Int {
            var n = capacity - 1
            n = n or (n ushr 1)
            n = n or (n ushr 2)
            n = n or (n ushr 4)
            n = n or (n ushr 8)
            n = n or (n ushr 16)
            return if (n < 0) 1 else n + 1
        }
    }
}
