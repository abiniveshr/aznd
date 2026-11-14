package com.ashrdev.aznd.ui.common.tiling

/**
 * A small hash map from primitive `Long` keys, open addressing with linear probing. The search remembers millions of
 * small results by a packed key; boxed keys in a [HashMap] fold the key so that packed fields collide, and the
 * boxing itself costs more than the lookup. The slot comes from a 64-bit mix of the whole key. Not thread-safe.
 */
internal class TileLongMap<V : Any>(capacity: Int = 64) {
    private var keys = LongArray(capacity)
    private var values = arrayOfNulls<Any>(capacity)
    private var mask = capacity - 1
    private var count = 0
    private var limit = capacity * 3 / 5

    init {
        require(capacity > 0 && capacity and (capacity - 1) == 0) { "capacity must be a power of two" }
    }

    val size: Int get() = count

    @Suppress("UNCHECKED_CAST")
    operator fun get(key: Long): V? {
        var at = slot(key)
        while (true) {
            val v = values[at] ?: return null
            if (keys[at] == key) return v as V
            at = (at + 1) and mask
        }
    }

    operator fun set(key: Long, value: V) {
        var at = slot(key)
        while (true) {
            if (values[at] == null) break
            if (keys[at] == key) {
                values[at] = value
                return
            }
            at = (at + 1) and mask
        }
        keys[at] = key
        values[at] = value
        if (++count > limit) grow()
    }

    fun clear() {
        values.fill(null)
        count = 0
    }

    private fun slot(key: Long): Int {
        val h = key * -0x61c8864680b583ebL
        return (h xor (h ushr 32)).toInt() and mask
    }

    private fun grow() {
        val oldKeys = keys
        val oldValues = values
        val capacity = oldKeys.size * 2
        keys = LongArray(capacity)
        values = arrayOfNulls(capacity)
        mask = capacity - 1
        limit = capacity * 3 / 5
        for (i in oldKeys.indices) {
            if (oldValues[i] == null) continue
            var at = slot(oldKeys[i])
            while (values[at] != null) at = (at + 1) and mask
            keys[at] = oldKeys[i]
            values[at] = oldValues[i]
        }
    }
}
