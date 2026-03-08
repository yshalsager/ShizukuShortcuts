package com.yshalsager.shizukushortcuts

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat

data class AppActionItem(
    val id: String,
    val short_label: String,
    val long_label: String,
    @param:DrawableRes val icon_res: Int,
    val shortcut_intent_action: String? = null,
    val shell_command: String? = null
)

object ActionCatalog {
    private const val custom_shortcut_intent_action_prefix = "com.yshalsager.shizukushortcuts.action.CUSTOM."
    internal fun shortcut_activity(context: Context) = ComponentName(context, MainActivity::class.java)
    private fun custom_shortcut_intent_action(action_id: String) = "${custom_shortcut_intent_action_prefix}${action_id}"

    fun built_in_actions(context: Context) = ShortcutActions.all.map { action ->
        AppActionItem(
            id = action.id,
            short_label = context.getString(action.short_label_res),
            long_label = context.getString(action.long_label_res),
            icon_res = action.icon_res,
            shortcut_intent_action = action.shortcut_intent_action
        )
    }

    fun custom_actions(context: Context) = AppServices.custom_actions_repository(context).actions.value
        .asReversed()
        .map { action ->
            AppActionItem(
                id = action.id,
                short_label = action.label,
                long_label = action.label,
                icon_res = R.drawable.ic_shortcut_custom_action,
                shell_command = action.shell_command
            )
        }

    fun find_by_id(context: Context, action_id: String?): AppActionItem? {
        if (action_id == null) return null
        return built_in_actions(context).firstOrNull { it.id == action_id }
            ?: custom_actions(context).firstOrNull { it.id == action_id }
    }

    fun find_by_intent(context: Context, intent: Intent?): AppActionItem? {
        val requested_action_id = intent?.getStringExtra(ShortcutActions.extra_action_id)
        if (requested_action_id != null) return find_by_id(context, requested_action_id)
        val action = intent?.action
        if (action != null && action.startsWith(custom_shortcut_intent_action_prefix)) return find_by_id(context, action.removePrefix(custom_shortcut_intent_action_prefix))
        return built_in_actions(context).firstOrNull { it.shortcut_intent_action == action }
    }

    fun build_dispatch_intent(context: Context, action: AppActionItem) =
        Intent(context, ShortcutDispatchActivity::class.java)
            .setAction(action.shortcut_intent_action ?: custom_shortcut_intent_action(action.id))
            .putExtra(ShortcutActions.extra_action_id, action.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

    fun build_pinned_shortcut(context: Context, action: AppActionItem) =
        ShortcutInfoCompat.Builder(context, action.id)
            .setShortLabel(action.short_label)
            .setLongLabel(action.long_label)
            .setActivity(shortcut_activity(context))
            .setIcon(IconCompat.createWithResource(context, action.icon_res))
            .setIntent(build_dispatch_intent(context, action))
            .build()
}

object DynamicShortcutSync {
    internal data class SyncPlan(
        val all_custom_actions: List<CustomAction>,
        val dynamic_shortcut_count: Int
    )

    fun delete_custom_shortcut(context: Context, action_id: String) {
        val shortcut_manager = context.getSystemService(ShortcutManager::class.java) ?: return
        shortcut_manager.disableShortcuts(listOf(action_id))
        shortcut_manager.removeDynamicShortcuts(listOf(action_id))
    }

    fun refresh_custom_shortcuts(context: Context, custom_actions: List<CustomAction>) {
        val shortcut_manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val sync_plan = sync_plan(custom_actions, shortcut_manager.maxShortcutCountPerActivity)
        val shortcuts = sync_plan.all_custom_actions.map { build_custom_shortcut(context, it) }

        shortcut_manager.updateShortcuts(shortcuts)
        shortcut_manager.dynamicShortcuts = shortcuts.take(sync_plan.dynamic_shortcut_count)
    }

    fun published_custom_actions(custom_actions: List<CustomAction>, max_shortcut_count: Int): List<CustomAction> {
        val sync_plan = sync_plan(custom_actions, max_shortcut_count)
        return sync_plan.all_custom_actions.take(sync_plan.dynamic_shortcut_count)
    }

    internal fun sync_plan(custom_actions: List<CustomAction>, max_shortcut_count: Int): SyncPlan {
        val all_custom_actions = custom_actions.asReversed()
        return SyncPlan(
            all_custom_actions = all_custom_actions,
            dynamic_shortcut_count = (max_shortcut_count - ShortcutActions.all.size).coerceAtLeast(0)
        )
    }

    private fun build_custom_shortcut(context: Context, action: CustomAction): ShortcutInfo {
        val app_action = AppActionItem(
            id = action.id,
            short_label = action.label,
            long_label = action.label,
            icon_res = R.drawable.ic_shortcut_custom_action,
            shell_command = action.shell_command
        )

        return ShortcutInfo.Builder(context, action.id)
            .setShortLabel(action.label)
            .setLongLabel(action.label)
            .setActivity(ActionCatalog.shortcut_activity(context))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_custom_action))
            .setIntent(ActionCatalog.build_dispatch_intent(context, app_action))
            .build()
    }
}
