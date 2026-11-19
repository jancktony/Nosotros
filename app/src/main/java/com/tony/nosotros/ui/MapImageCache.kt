package com.tony.nosotros.ui

/** Per-style sprite history. Visible images remain protected even above the history limit. */
internal class MapImageCache(private val capacity: Int = 32) {
    init { require(capacity > 0) }
    private val history = linkedSetOf<String>()
    fun touch(id: String): Boolean {
        val fresh = history.add(id)
        if (!fresh) { history.remove(id); history.add(id) }
        return fresh
    }
    fun retireUnused(visible: Set<String>): List<String> {
        val retired = history.filter { it !in visible }.take((history.size - capacity).coerceAtLeast(0))
        history.removeAll(retired.toSet())
        return retired
    }
    internal val size get() = history.size
}
