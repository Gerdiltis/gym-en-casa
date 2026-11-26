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

class CleanNotesTest {
    @org.junit.Test fun dropsChangelogLinkAndMarkdown() {
        val body = "v0.7.0: aviso\n\n- **Comprueba** al abrir\n* `Actualizar`\n\n**Full Changelog**: https://github.com/a/b/compare/v0.6.0...v0.7.0"
        org.junit.Assert.assertEquals("v0.7.0: aviso\n\n• Comprueba al abrir\n• Actualizar", GitHubUpdater.cleanNotes(body))
    }

    @org.junit.Test fun onlyChangelogBecomesEmpty() =
        org.junit.Assert.assertEquals("", GitHubUpdater.cleanNotes("**Full Changelog**: https://x"))
}
