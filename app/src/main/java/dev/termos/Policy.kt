package dev.termos

/** When a setup dialog may be shown again. Pure, so it is unit-tested. */
object SetupPolicy {
    const val SNOOZE_MS = 60L * 60 * 1000

    /** askedMs = when this item was last shown/dismissed (null = never). */
    fun due(nowMs: Long, askedMs: Long?, snoozeMs: Long = SNOOZE_MS): Boolean =
        askedMs == null || nowMs - askedMs >= snoozeMs
}
