package com.yshalsager.shizukushortcuts

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import java.util.UUID

data class AppActionItem(
    val id: String,
    val short_label: String,
    val long_label: String,
    @param:DrawableRes val icon_res: Int,
    val shortcut_intent_action: String? = null,
    val shell_command: String? = null
)

object ActionCatalog {
    private const val custom_shortcut_intent_action = "com.yshalsager.shizukushortcuts.action.CUSTOM"
    private const val dispatch_token_key = "dispatch_token"
    internal fun shortcut_activity(context: Context) = ComponentName(context, MainActivity::class.java)

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
        val intent_action = intent?.action
        val action_id = intent?.getStringExtra(ShortcutActions.extra_action_id)
            ?: built_in_actions(context).firstOrNull { it.shortcut_intent_action == intent_action }?.id
        val action = find_by_id(context, action_id) ?: return null
        return action.takeIf {
            it.id in ShortcutActions.public_shortcut_ids ||
                intent?.getStringExtra(dispatch_token_key) == dispatch_token(context)
        }
    }

    fun build_dispatch_intent(context: Context, action: AppActionItem) =
        Intent(context, ShortcutDispatchActivity::class.java)
            .setAction(action.shortcut_intent_action ?: custom_shortcut_intent_action)
            .putExtra(ShortcutActions.extra_action_id, action.id)
            .putExtra(dispatch_token_key, dispatch_token(context))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun dispatch_token(context: Context): String {
        val preferences = context.getSharedPreferences(dispatch_token_key, Context.MODE_PRIVATE)
        return synchronized(this) {
            preferences.getString(dispatch_token_key, null)
                ?: UUID.randomUUID().toString().also {
                    check(preferences.edit().putString(dispatch_token_key, it).commit())
                }
        }
    }

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

    fun refresh_custom_shortcuts(context: Context, custom_actions: List<CustomAction>): Boolean {
        val shortcut_manager = context.getSystemService(ShortcutManager::class.java) ?: return false
        val sync_plan = sync_plan(custom_actions, shortcut_manager.maxShortcutCountPerActivity)
        val shortcuts = sync_plan.all_custom_actions.map { build_custom_shortcut(context, it) }
        val current_ids = shortcuts.mapTo(mutableSetOf(), ShortcutInfo::getId)
        val pinned_custom_ids = custom_ids(shortcut_manager.pinnedShortcuts)
        val stale_dynamic_ids = custom_ids(shortcut_manager.dynamicShortcuts) - current_ids
        val removed_ids = pinned_custom_ids - current_ids
        val restored_ids = pinned_custom_ids intersect current_ids

        if (stale_dynamic_ids.isNotEmpty()) shortcut_manager.removeDynamicShortcuts(stale_dynamic_ids.toList())
        if (removed_ids.isNotEmpty()) shortcut_manager.disableShortcuts(removed_ids.toList())
        if (!shortcut_manager.setDynamicShortcuts(shortcuts.take(sync_plan.dynamic_shortcut_count))) return false
        if (shortcuts.isNotEmpty() && !shortcut_manager.updateShortcuts(shortcuts)) return false
        if (restored_ids.isNotEmpty()) shortcut_manager.enableShortcuts(restored_ids.toList())
        return true
    }

    fun refresh_sensitive_shortcuts(context: Context): Boolean {
        val shortcut_manager = context.getSystemService(ShortcutManager::class.java) ?: return false
        val sensitive_ids = shortcut_manager.pinnedShortcuts
            .map(ShortcutInfo::getId)
            .filter { id ->
                id !in ShortcutActions.public_shortcut_ids && id in ShortcutActions.ids
            }
        if (sensitive_ids.isEmpty()) return true
        val updated = ShortcutManagerCompat.updateShortcuts(
            context,
            ActionCatalog.built_in_actions(context)
                .filter { it.id in sensitive_ids }
                .map { ActionCatalog.build_pinned_shortcut(context, it) }
        )
        if (updated) shortcut_manager.enableShortcuts(sensitive_ids)
        return updated
    }

    fun published_custom_actions(custom_actions: List<CustomAction>, max_shortcut_count: Int): List<CustomAction> {
        val sync_plan = sync_plan(custom_actions, max_shortcut_count)
        return sync_plan.all_custom_actions.take(sync_plan.dynamic_shortcut_count)
    }

    internal fun sync_plan(custom_actions: List<CustomAction>, max_shortcut_count: Int): SyncPlan {
        val all_custom_actions = custom_actions.asReversed()
        return SyncPlan(
            all_custom_actions = all_custom_actions,
            dynamic_shortcut_count = (max_shortcut_count - ShortcutActions.public_shortcut_ids.size).coerceAtLeast(0)
        )
    }

    private fun custom_ids(shortcuts: List<ShortcutInfo>) =
        shortcuts.mapTo(mutableSetOf(), ShortcutInfo::getId).apply { removeAll(ShortcutActions.ids) }

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
