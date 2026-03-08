package com.yshalsager.shizukushortcuts

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat

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

    val all = listOf(expand_notifications, expand_quick_settings)
    val ids = all.map { it.id }

    fun find_by_id(action_id: String?) = all.firstOrNull { it.id == action_id }

    fun find_by_intent(intent: Intent?): ShortcutAction? {
        val requested_action_id = intent?.getStringExtra(extra_action_id)
        if (requested_action_id != null) return find_by_id(requested_action_id)
        return all.firstOrNull { it.shortcut_intent_action == intent?.action }
    }

    fun build_dispatch_intent(context: Context, action: ShortcutAction) =
        Intent(context, ShortcutDispatchActivity::class.java)
            .setAction(action.shortcut_intent_action)
            .putExtra(extra_action_id, action.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun build_pinned_shortcut(context: Context, action: ShortcutAction) =
        ShortcutInfoCompat.Builder(context, action.id)
            .setShortLabel(context.getString(action.short_label_res))
            .setLongLabel(context.getString(action.long_label_res))
            .setIcon(IconCompat.createWithResource(context, action.icon_res))
            .setIntent(build_dispatch_intent(context, action))
            .build()
}
