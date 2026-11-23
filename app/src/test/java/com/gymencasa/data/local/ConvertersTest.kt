package com.gymencasa.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {
    private val c = Converters()

    @Test fun tagsRoundTrip() {
        val tags = listOf("calistenia")
        assertEquals(tags, c.toList(c.fromList(tags)))
    }

    @Test fun migrationDefaultIsEmptyList() {
        assertEquals(emptyList<String>(), c.toList("[]"))
        assertEquals(emptyList<String>(), c.toList(""))
    }

    @Test fun equipmentAlternativesSurvive() {
        val eq = listOf("gomas|bandas_largas", "silla")
        assertEquals(eq, c.toList(c.fromList(eq)))
    }

    @Test fun migration2To3IsRegistered() {
        assertEquals(2, AppDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, AppDatabase.MIGRATION_2_3.endVersion)
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
    }
}
