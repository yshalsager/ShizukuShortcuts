package com.yshalsager.shizukushortcuts

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

data class CustomAction(
    val id: String,
    val label: String,
    val shell_command: String
)

interface CustomActionsRepositoryContract {
    val actions: StateFlow<List<CustomAction>>
    fun add_action(label: String, shell_command: String): CustomAction
    fun update_action(action_id: String, label: String, shell_command: String)
    suspend fun replace_all_actions(actions: List<CustomAction>): Boolean
    fun delete_action(action_id: String): IndexedValue<CustomAction>?
    fun restore_action(deleted_action: IndexedValue<CustomAction>)
    fun find_by_id(action_id: String): CustomAction?
}

class AppCustomActionsRepository(app_context: Context) : CustomActionsRepositoryContract {
    companion object {
        private const val prefs_name = "custom_actions"
        private const val actions_key = "actions"
    }

    private val app_context = app_context.applicationContext
    private val shared_preferences = this.app_context.getSharedPreferences(prefs_name, Context.MODE_PRIVATE)
    private val state_flow = MutableStateFlow(
        shared_preferences.load_json(actions_key, emptyList(), ::parse_custom_actions)
    )
    private val shortcut_sync_scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    override val actions: StateFlow<List<CustomAction>> = state_flow.asStateFlow()
    internal val initial_shortcut_sync = schedule_shortcut_sync(state_flow.value)

    override fun add_action(label: String, shell_command: String): CustomAction {
        val action = CustomAction(
            id = UUID.randomUUID().toString(),
            label = label.trim(),
            shell_command = shell_command.trim()
        )
        save_actions(state_flow.value + action)
        return action
    }

    override fun update_action(action_id: String, label: String, shell_command: String) {
        save_actions(
            state_flow.value.map { action ->
                if (action.id != action_id) action
                else action.copy(label = label.trim(), shell_command = shell_command.trim())
            }
        )
    }

    override suspend fun replace_all_actions(actions: List<CustomAction>) = withContext(Dispatchers.IO) {
        check(shared_preferences.edit().putString(actions_key, serialize_custom_actions(actions)).commit())
        state_flow.value = actions
        try {
            schedule_shortcut_sync(actions).await()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    override fun delete_action(action_id: String): IndexedValue<CustomAction>? {
        val deleted_action = state_flow.value.withIndex().firstOrNull { it.value.id == action_id } ?: return null
        save_actions(state_flow.value.filterNot { it.id == action_id })
        return deleted_action
    }

    override fun restore_action(deleted_action: IndexedValue<CustomAction>) {
        if (find_by_id(deleted_action.value.id) != null) return
        save_actions(state_flow.value.toMutableList().apply {
            add(deleted_action.index.coerceAtMost(size), deleted_action.value)
        })
    }

    override fun find_by_id(action_id: String) = state_flow.value.firstOrNull { it.id == action_id }

    private fun save_actions(actions: List<CustomAction>) {
        shared_preferences.edit().putString(actions_key, serialize_custom_actions(actions)).apply()
        state_flow.value = actions
        schedule_shortcut_sync(actions)
    }

    private fun schedule_shortcut_sync(actions: List<CustomAction>) =
        shortcut_sync_scope.async {
            val sensitive_shortcuts_synchronized = DynamicShortcutSync.refresh_sensitive_shortcuts(app_context)
            val custom_shortcuts_synchronized = DynamicShortcutSync.refresh_custom_shortcuts(app_context, actions)
            ActionWidgetProvider.refresh_widgets(app_context)
            sensitive_shortcuts_synchronized && custom_shortcuts_synchronized
        }
}

internal fun <T> SharedPreferences.load_json(key: String, fallback: T, parse: (String) -> T): T {
    val value = all[key] ?: return fallback
    if (value is String) {
        try {
            return parse(value)
        } catch (_: JSONException) {
            Unit
        }
    }
    edit().putString("${key}_corrupt", value.toString()).remove(key).apply()
    return fallback
}

fun validate_custom_action(label: String, shell_command: String): Int? {
    val trimmed_label = label.trim()
    val trimmed_command = shell_command.trim()

    return when {
        trimmed_label.isEmpty() -> R.string.custom_action_label_required
        trimmed_command.isEmpty() -> R.string.custom_action_command_required
        trimmed_command.startsWith("adb shell", ignoreCase = true) -> R.string.custom_action_strip_adb
        else -> null
    }
}

fun serialize_custom_actions(actions: List<CustomAction>): String {
    val json_array = JSONArray()

    actions.forEach { action ->
        json_array.put(
            JSONObject()
                .put("id", action.id)
                .put("label", action.label)
                .put("shell_command", action.shell_command)
        )
    }

    return json_array.toString()
}

fun parse_custom_actions(serialized_actions: String?): List<CustomAction> {
    if (serialized_actions.isNullOrBlank()) return emptyList()

    val json_array = JSONArray(serialized_actions)

    return List(json_array.length()) { index ->
        val json_object = json_array.getJSONObject(index)
        CustomAction(
            id = json_object.get("id") as? String ?: throw JSONException("Invalid action id"),
            label = json_object.get("label") as? String ?: throw JSONException("Invalid action label"),
            shell_command = json_object.get("shell_command") as? String ?: throw JSONException("Invalid action command")
        )
    }
}
