package com.gymencasa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipmentFilterTest {
    @Test
    fun `without dumbbell goblet squat is not available`() {
        val available = EquipmentFilter.available(catalog, owned = setOf("gomas"))
        assertFalse(available.any { it.slug == "goblet_squat" })
        assertTrue(available.any { it.slug == "remo_goma" })
    }

    @Test
    fun `no-equipment exercises are always available`() {
        val available = EquipmentFilter.available(catalog, owned = emptySet())
        assertTrue(available.any { it.slug == "sentadilla" })
        assertTrue(available.all { it.equipment == setOf("ninguno") })
    }

    @Test
    fun `exercise needing two items requires both`() {
        val e = ex("remo_mochila_silla", equipment = setOf("mochila", "silla"))
        assertFalse(EquipmentFilter.isAvailable(e, setOf("silla")))
        assertEquals(setOf("mochila"), EquipmentFilter.missing(e, setOf("silla")))
        assertTrue(EquipmentFilter.isAvailable(e, setOf("silla", "mochila")))
    }
}
