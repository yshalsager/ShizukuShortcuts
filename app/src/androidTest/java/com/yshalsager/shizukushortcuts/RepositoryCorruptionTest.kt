package com.yshalsager.shizukushortcuts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class RepositoryCorruptionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tear_down() {
        context.getSharedPreferences("custom_actions", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("widget_bindings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun replace_waits_for_persistence_and_reconciliation() {
        runBlocking {
            val preferences = context.getSharedPreferences("custom_actions", Context.MODE_PRIVATE)
            val action = CustomAction("00000000-0000-0000-0000-000000000001", "Test", "echo test")
            val repository = AppCustomActionsRepository(context)

            assertTrue(repository.replace_all_actions(listOf(action)))
            assertEquals(listOf(action), parse_custom_actions(preferences.getString("actions", null)))

            repository.replace_all_actions(emptyList())
        }
    }

    @Test
    fun wrong_typed_json_fields_are_rejected_on_android() {
        val custom_preferences = context.getSharedPreferences("custom_actions", Context.MODE_PRIVATE)
        val widget_preferences = context.getSharedPreferences("widget_bindings", Context.MODE_PRIVATE)
        custom_preferences.edit()
            .putString("actions", """[{"id":1,"label":2,"shell_command":3}]""")
            .commit()
        widget_preferences.edit()
            .putString("bindings", """[{"app_widget_id":"1","action_id":2}]""")
            .commit()

        assertEquals(emptyList<CustomAction>(), AppCustomActionsRepository(context).actions.value)
        assertEquals(null, WidgetBindingsRepository(context).get_binding(1))
        assertTrue(runCatching { parse_custom_actions_backup("""{"version":"1","actions":[]}""") }.isFailure)
        assertEquals("""[{"id":1,"label":2,"shell_command":3}]""", custom_preferences.getString("actions_corrupt", null))
        assertEquals("""[{"app_widget_id":"1","action_id":2}]""", widget_preferences.getString("bindings_corrupt", null))
    }

    @Test
    fun corrupt_preferences_are_quarantined() {
        val custom_preferences = context.getSharedPreferences("custom_actions", Context.MODE_PRIVATE)
        val widget_preferences = context.getSharedPreferences("widget_bindings", Context.MODE_PRIVATE)
        custom_preferences.edit().putInt("actions", 42).commit()
        widget_preferences.edit().putString("bindings", "not-json").commit()

        assertEquals(emptyList<CustomAction>(), AppCustomActionsRepository(context).actions.value)
        assertEquals(null, WidgetBindingsRepository(context).get_binding(1))
        assertFalse(custom_preferences.contains("actions"))
        assertEquals("42", custom_preferences.getString("actions_corrupt", null))
        assertFalse(widget_preferences.contains("bindings"))
        assertEquals("not-json", widget_preferences.getString("bindings_corrupt", null))
    }
}
