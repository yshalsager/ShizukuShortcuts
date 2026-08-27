package com.yshalsager.shizukushortcuts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

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
    fun `launch failure falls back and reports the fallback`() {
        val attempted_commands = mutableListOf<List<String>>()

        val result = ActionPerformer.perform_action(ShortcutActions.expand_notifications.id) { command ->
            attempted_commands += command
            if (attempted_commands.size == 1) throw IOException("cmd missing")
            CommandRun(exit_code = 1, output = "fallback failed")
        }

        assertEquals(ActionResult.STATUS_EXECUTION_FAILED, result.status_code)
        assertEquals(ShortcutActions.expand_notifications.all_commands, attempted_commands)
        assertEquals(ShortcutActions.expand_notifications.fallback_commands.single().joinToString(" "), result.executed_command)
        assertEquals("fallback failed", result.message)
        assertTrue(result.used_fallback)
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
    fun `screen off action stays on primary command`() {
        val attempted_commands = mutableListOf<List<String>>()

        val result = ActionPerformer.perform_action(ShortcutActions.screen_off.id) { command ->
            attempted_commands += command
            CommandRun(exit_code = 0, output = "")
        }

        assertTrue(result.is_success)
        assertFalse(result.used_fallback)
        assertEquals(listOf(ShortcutActions.screen_off.primary_command), attempted_commands)
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

    @Test
    fun `process timeout has distinct result`() {
        val result = ActionPerformer.perform_custom_action("custom-id", "sleep 60") {
            CommandRun(exit_code = -1, output = "", timed_out = true)
        }

        assertEquals(ActionResult.STATUS_EXECUTION_TIMED_OUT, result.status_code)
    }
}
