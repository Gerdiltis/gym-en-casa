package com.gymencasa.update

/** When to ask GitHub again for a new release: on every app open, but at most once per [MIN_INTERVAL_MS]. */
object UpdateCheckPolicy {
    const val MIN_INTERVAL_MS = 60_000L

    fun shouldCheck(lastCheckMs: Long?, nowMs: Long): Boolean =
        lastCheckMs == null || nowMs < lastCheckMs || nowMs - lastCheckMs >= MIN_INTERVAL_MS
}
