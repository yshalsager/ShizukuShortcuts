package com.yshalsager.shizukushortcuts

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetBindingsRepositoryTest {
    @Test
    fun `widget bindings serialize and parse`() {
        val bindings = mapOf(
            11 to "expand_notifications",
            22 to "custom-id"
        )

        assertEquals(bindings, parse_widget_bindings(serialize_widget_bindings(bindings)))
    }

    @Test
    fun `parse empty bindings`() {
        assertEquals(emptyMap<Int, String>(), parse_widget_bindings(null))
        assertEquals(emptyMap<Int, String>(), parse_widget_bindings(""))
    }

    @Test
    fun `serialize empty bindings`() {
        assertEquals("[]", serialize_widget_bindings(emptyMap()))
    }
}
