package com.gymencasa.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {
    @Test
    fun compares() {
        assertTrue(GitHubUpdater.isNewer("0.1.1", "0.1.0"))
        assertTrue(GitHubUpdater.isNewer("0.10.0", "0.9.9"))
        assertTrue(GitHubUpdater.isNewer("1.0", "0.9.9"))
        assertFalse(GitHubUpdater.isNewer("0.1.0", "0.1.0"))
        assertFalse(GitHubUpdater.isNewer("0.1.0", "0.2.0"))
    }
}
