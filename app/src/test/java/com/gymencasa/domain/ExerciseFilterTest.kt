package com.gymencasa.domain

import com.gymencasa.domain.model.ExerciseType
import com.gymencasa.domain.model.Pregnancy
import com.gymencasa.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseFilterTest {
    private fun settings(vararg owned: String, pregnant: Boolean = false) = UserSettings(equipment = owned.toSet(), pregnant = pregnant)

    @Test
    fun `without dumbbell goblet squat is not available`() {
        val available = ExerciseFilter.available(catalog, settings("gomas"))
        assertFalse(available.any { it.slug == "goblet_squat" })
        assertTrue(available.any { it.slug == "remo_goma" })
    }

    @Test
    fun `no-equipment exercises are always available`() {
        val available = ExerciseFilter.available(catalog, settings())
        assertTrue(available.any { it.slug == "sentadilla" })
        assertTrue(available.all { it.equipment == setOf("ninguno") })
    }

    @Test
    fun `exercise needing two items requires both`() {
        val e = ex("remo_mochila_silla", equipment = setOf("mochila", "silla"))
        assertFalse(ExerciseFilter.hasEquipment(e, setOf("silla")))
        assertEquals(setOf("mochila"), ExerciseFilter.missing(e, setOf("silla")))
        assertTrue(ExerciseFilter.hasEquipment(e, setOf("silla", "mochila")))
    }

    @Test
    fun `alternatives are met by either item`() {
        val e = ex("remo", equipment = setOf("gomas|bandas_largas"))
        assertTrue(ExerciseFilter.hasEquipment(e, setOf("bandas_largas")))
        assertTrue(ExerciseFilter.hasEquipment(e, setOf("gomas")))
        assertEquals(setOf("gomas|bandas_largas"), ExerciseFilter.missing(e, emptySet()))
        assertEquals(listOf("gomas", "bandas_largas"), ExerciseFilter.allSlugs(e))
    }

    @Test
    fun `fitball exercises need the ball`() {
        val e = ex("circulos_cadera_pelota", equipment = setOf("pelota"), pregnancy = Pregnancy.PARTO)
        assertFalse(ExerciseFilter.isAvailable(e, settings("gomas")))
        assertTrue(ExerciseFilter.isAvailable(e, settings("pelota", pregnant = true)))
    }

    @Test
    fun `pregnancy mode hides unsuitable exercises`() {
        val list = listOf(
            ex("burpee", type = ExerciseType.CARDIO),
            ex("marcha", pregnancy = Pregnancy.APTO),
            ex("mariposa", type = ExerciseType.MOVILIDAD, pregnancy = Pregnancy.PARTO),
        )
        assertEquals(listOf("marcha", "mariposa"), ExerciseFilter.available(list, settings(pregnant = true)).map { it.slug })
        assertEquals(3, ExerciseFilter.available(list, settings()).size)
    }
}

class ConvertersTest {
    @Test
    fun `lists keep values containing the alternatives separator`() {
        val c = com.gymencasa.data.local.Converters()
        val list = listOf("gomas|bandas_largas", "silla")
        assertEquals(list, c.toList(c.fromList(list)))
        assertEquals(emptyList<String>(), c.toList(c.fromList(emptyList())))
    }
}
