package com.yshalsager.shizukushortcuts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionPerformerTest {
    @Test
    fun `notifications action falls back to service call`() {
        val attempted_commands = mutableListOf<List<String>>()

        val result = ActionPerformer.perform_action(ShortcutActions.expand_notifications.id) { command ->
            attempted_commands += command
            if (attempted_commands.size == 1) {
                CommandRun(exit_code = 1, output = "cmd failed")
            } else {
                CommandRun(exit_code = 0, output = "")
            }
        }

        assertTrue(result.is_success)
        assertTrue(result.used_fallback)
        assertEquals(ShortcutActions.expand_notifications.all_commands, attempted_commands)
    }

    @Test
    fun `quick settings action stays on primary command`() {
        val attempted_commands = mutableListOf<List<String>>()

        val result = ActionPerformer.perform_action(ShortcutActions.expand_quick_settings.id) { command ->
            attempted_commands += command
            CommandRun(exit_code = 0, output = "")
        }

        assertTrue(result.is_success)
        assertFalse(result.used_fallback)
        assertEquals(listOf(ShortcutActions.expand_quick_settings.primary_command), attempted_commands)
    }

    @Test
    fun `unknown action returns unknown status`() {
        val result = ActionPerformer.perform_action("missing")

        assertEquals(ActionResult.STATUS_UNKNOWN_ACTION, result.status_code)
    }

    @Test
    fun `custom action output is capped before returning`() {
        val long_output = "x".repeat(20_000)

        val result = ActionPerformer.perform_custom_action("custom-id", "echo test") {
            CommandRun(exit_code = 0, output = long_output)
        }

        assertTrue(result.is_success)
        assertEquals(8_192, result.message.length)
    }
}
