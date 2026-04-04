package com.yshalsager.shizukushortcuts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CustomActionTest {
    @Test
    fun `custom actions serialize and parse`() {
        val actions = listOf(
            CustomAction("one", "Open notifications", "cmd statusbar expand-notifications"),
            CustomAction("two", "Show settings", "cmd statusbar expand-settings")
        )

        assertEquals(actions, parse_custom_actions(serialize_custom_actions(actions)))
    }

    @Test
    fun `custom actions backup roundtrip preserves ids`() {
        val actions = listOf(
            CustomAction("id-1", "Open notifications", "cmd statusbar expand-notifications"),
            CustomAction("id-2", "Show settings", "cmd statusbar expand-settings")
        )

        val restored_actions = parse_custom_actions_backup(serialize_custom_actions_backup(actions))

        assertEquals(actions, restored_actions)
    }

    @Test
    fun `custom actions backup rejects malformed json`() {
        assertTrue(runCatching { parse_custom_actions_backup("not-json") }.isFailure)
    }

    @Test
    fun `custom actions backup rejects unsupported version`() {
        val invalid_backup = """{"version":2,"actions":[]}"""

        assertTrue(runCatching { parse_custom_actions_backup(invalid_backup) }.isFailure)
    }

    @Test
    fun `custom actions backup rejects duplicate ids`() {
        val invalid_backup = """
            {"version":1,"actions":[
                {"id":"dup","label":"One","shell_command":"one"},
                {"id":"dup","label":"Two","shell_command":"two"}
            ]}
        """.trimIndent()

        assertTrue(runCatching { parse_custom_actions_backup(invalid_backup) }.isFailure)
    }

    @Test
    fun `custom actions backup file name includes timestamp`() {
        assertEquals(
            "shizuku-custom-actions-backup-20260404-153045.json",
            custom_actions_backup_file_name(LocalDateTime.of(2026, 4, 4, 15, 30, 45))
        )
    }

    @Test
    fun `validation rejects invalid custom actions`() {
        assertEquals(R.string.custom_action_label_required, validate_custom_action("", "cmd statusbar expand-notifications"))
        assertEquals(R.string.custom_action_command_required, validate_custom_action("Label", ""))
        assertEquals(R.string.custom_action_strip_adb, validate_custom_action("Label", "adb shell cmd statusbar expand-notifications"))
        assertNull(validate_custom_action("Label", "cmd statusbar expand-notifications"))
    }

    @Test
    fun `custom action executes through sh c`() {
        val result = ActionPerformer.perform_custom_action("custom-id", "cmd statusbar expand-notifications") { command ->
            assertEquals(listOf("sh", "-c", "cmd statusbar expand-notifications"), command)
            CommandRun(exit_code = 0, output = "")
        }

        assertEquals(ActionResult.STATUS_SUCCESS, result.status_code)
    }

    @Test
    fun `dynamic shortcuts prefer newest custom actions within limit`() {
        val actions = listOf(
            CustomAction("one", "One", "one"),
            CustomAction("two", "Two", "two"),
            CustomAction("three", "Three", "three")
        )

        assertEquals(
            listOf(actions[2], actions[1]),
            DynamicShortcutSync.published_custom_actions(actions, ShortcutActions.all.size + 2)
        )
    }

    @Test
    fun `sync plan still updates older pinned custom actions`() {
        val actions = listOf(
            CustomAction("one", "One", "one"),
            CustomAction("two", "Two", "two"),
            CustomAction("three", "Three", "three")
        )

        val sync_plan = DynamicShortcutSync.sync_plan(actions, ShortcutActions.all.size + 2)

        assertEquals(listOf(actions[2], actions[1], actions[0]), sync_plan.all_custom_actions)
        assertEquals(2, sync_plan.dynamic_shortcut_count)
    }
}
