package com.yshalsager.shizukushortcuts

import android.content.Context
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActionCatalogTest {
    private lateinit var context: Context

    @Before
    fun set_up() {
        context = ApplicationProvider.getApplicationContext()
        AppServices.custom_actions_repository_factory = {
            FakeCustomActionsRepository(
                listOf(CustomAction("custom-id", "Custom command", "cmd statusbar expand-notifications"))
            )
        }
    }

    @After
    fun tear_down() {
        AppServices.reset_for_tests()
    }

    @Test
    fun custom_actions_are_available_in_catalog() {
        val action = ActionCatalog.find_by_id(context, "custom-id")!!

        assertEquals("Custom command", action.short_label)
        assertEquals("cmd statusbar expand-notifications", action.shell_command)
    }

    @Test
    fun custom_pinned_shortcut_uses_shared_dispatch_path() {
        val action = ActionCatalog.find_by_id(context, "custom-id")!!
        val shortcut = ActionCatalog.build_pinned_shortcut(context, action)

        assertEquals("custom-id", shortcut.id)
        assertEquals("Custom command", shortcut.shortLabel.toString())
        assertEquals(ComponentName(context, MainActivity::class.java), shortcut.activity)
        assertEquals("com.yshalsager.shizukushortcuts.action.CUSTOM.custom-id", shortcut.intent.action)
        assertEquals("custom-id", shortcut.intent.getStringExtra(ShortcutActions.extra_action_id))
    }

    private class FakeCustomActionsRepository(actions: List<CustomAction>) : CustomActionsRepositoryContract {
        override val actions = kotlinx.coroutines.flow.MutableStateFlow(actions)

        override fun add_action(label: String, shell_command: String) = CustomAction("ignored", label, shell_command)

        override fun update_action(action_id: String, label: String, shell_command: String) = Unit

        override fun delete_action(action_id: String) = Unit

        override fun find_by_id(action_id: String) = actions.value.firstOrNull { it.id == action_id }
    }
}
