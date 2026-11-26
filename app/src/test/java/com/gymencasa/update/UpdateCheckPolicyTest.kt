package com.gymencasa.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckPolicyTest {
    @Test fun firstOpenAlwaysChecks() = assertTrue(UpdateCheckPolicy.shouldCheck(null, 5_000))

    @Test fun reopenWithinAMinuteDoesNotCheckAgain() =
        assertFalse(UpdateCheckPolicy.shouldCheck(100_000, 100_000 + UpdateCheckPolicy.MIN_INTERVAL_MS - 1))

    @Test fun reopenAfterAMinuteChecksAgain() =
        assertTrue(UpdateCheckPolicy.shouldCheck(100_000, 100_000 + UpdateCheckPolicy.MIN_INTERVAL_MS))

    @Test fun clockGoingBackwardsChecks() = assertTrue(UpdateCheckPolicy.shouldCheck(100_000, 50_000))
}
