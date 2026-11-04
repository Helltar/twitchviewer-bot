package com.helltar.twitchviewerbot.media

import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The recording slots of one user. At most [MAX_CONCURRENT] clips record at a time, however many /clip
 * commands and menu buttons asked for them, and at most [MAX_PENDING] are recording or waiting for a slot.
 * A clip is claimed under a key, so the same clip is never queued twice.
 */
class ClipSlots private constructor() {

    companion object {
        const val MAX_CONCURRENT = 3
        const val MAX_PENDING = 32

        // one entry per user who has recorded a clip; an idle one holds a semaphore and an empty set
        private val users = ConcurrentHashMap<Long, ClipSlots>()

        fun forUser(userId: Long): ClipSlots =
            users.computeIfAbsent(userId) { ClipSlots() }
    }

    // fair: clips get the freed slots in the order they started waiting, across requests
    private val permits = Semaphore(MAX_CONCURRENT)
    private val lock = Any()
    private val claimed = HashSet<String>() // guarded by lock

    /**
     * Claims the [keys] that are not claimed already and returns a ticket for each of them: an empty map
     * when every key is queued already, null when they would take the user past [MAX_PENDING].
     */
    fun claim(keys: Collection<String>): Map<String, Ticket>? {
        synchronized(lock) {
            val fresh = keys.distinct().filterNot { it in claimed }

            if (claimed.size + fresh.size > MAX_PENDING)
                return null

            claimed += fresh

            return fresh.associateWith { Ticket(it) }
        }
    }

    private fun unclaim(key: String) {
        synchronized(lock) {
            claimed -= key
        }
    }

    /** One claimed clip: its place in the queue, then its slot. [close] gives both back, and only once. */
    inner class Ticket internal constructor(private val key: String) : AutoCloseable {

        private val closed = AtomicBoolean()

        @Volatile
        var hasSlot = false
            private set

        /** Takes a free slot if there is one, without waiting. */
        fun tryTakeSlot(): Boolean {
            hasSlot = permits.tryAcquire()
            return hasSlot
        }

        /** Waits for a free slot; cancelling the wait leaves no slot taken. */
        suspend fun awaitSlot() {
            permits.acquire()
            hasSlot = true
        }

        override fun close() {
            if (!closed.compareAndSet(false, true))
                return

            if (hasSlot)
                permits.release()

            unclaim(key)
        }
    }
}
