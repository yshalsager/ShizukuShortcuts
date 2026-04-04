package com.yshalsager.shizukushortcuts

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class ShortcutAction(
    val id: String,
    val shortcut_intent_action: String,
    @param:StringRes val short_label_res: Int,
    @param:StringRes val long_label_res: Int,
    @param:DrawableRes val icon_res: Int,
    val primary_command: List<String>,
    val fallback_commands: List<List<String>> = emptyList()
) {
    val all_commands: List<List<String>>
        get() = listOf(primary_command) + fallback_commands
}

object ShortcutActions {
    const val extra_action_id = "action_id"

    private const val notifications_intent_action = "com.yshalsager.shizukushortcuts.action.EXPAND_NOTIFICATIONS"
    private const val quick_settings_intent_action = "com.yshalsager.shizukushortcuts.action.EXPAND_QUICK_SETTINGS"
    private const val screenshot_intent_action = "com.yshalsager.shizukushortcuts.action.TAKE_SCREENSHOT"
    private const val screen_off_intent_action = "com.yshalsager.shizukushortcuts.action.SCREEN_OFF"

    val expand_notifications = ShortcutAction(
        id = "expand_notifications",
        shortcut_intent_action = notifications_intent_action,
        short_label_res = R.string.open_notifications,
        long_label_res = R.string.open_notifications_long,
        icon_res = R.drawable.ic_shortcut_notifications,
        primary_command = listOf("cmd", "statusbar", "expand-notifications"),
        fallback_commands = listOf(listOf("service", "call", "statusbar", "1"))
    )

    val expand_quick_settings = ShortcutAction(
        id = "expand_quick_settings",
        shortcut_intent_action = quick_settings_intent_action,
        short_label_res = R.string.open_quick_settings,
        long_label_res = R.string.open_quick_settings_long,
        icon_res = R.drawable.ic_shortcut_quick_settings,
        primary_command = listOf("cmd", "statusbar", "expand-settings")
    )

    val take_screenshot = ShortcutAction(
        id = "take_screenshot",
        shortcut_intent_action = screenshot_intent_action,
        short_label_res = R.string.take_screenshot,
        long_label_res = R.string.take_screenshot_long,
        icon_res = R.drawable.ic_shortcut_screenshot,
        primary_command = listOf("input", "keyevent", "120")
    )

    val screen_off = ShortcutAction(
        id = "screen_off",
        shortcut_intent_action = screen_off_intent_action,
        short_label_res = R.string.screen_off,
        long_label_res = R.string.screen_off_long,
        icon_res = R.drawable.ic_shortcut_screen_off,
        primary_command = listOf("input", "keyevent", "26")
    )

    val all = listOf(expand_notifications, expand_quick_settings, take_screenshot, screen_off)
    val ids = all.map { it.id }

    fun find_by_id(action_id: String?) = all.firstOrNull { it.id == action_id }
}
