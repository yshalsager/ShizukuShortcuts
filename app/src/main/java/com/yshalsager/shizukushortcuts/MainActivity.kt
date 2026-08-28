package com.yshalsager.shizukushortcuts

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val manager by lazy { AppServices.shizuku_manager(this) }
    private val custom_actions_repository by lazy { AppServices.custom_actions_repository(this) }
    private val delete_undo_state by viewModels<DeleteUndoState>()
    private var pending_restore_actions by mutableStateOf<List<CustomAction>?>(null)
    private val create_backup_document = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val was_saved = withContext(Dispatchers.IO) {
                write_custom_actions_backup(contentResolver, uri, custom_actions_repository.actions.value)
            }
            show_toast(getString(if (was_saved) R.string.custom_actions_backup_success else R.string.custom_actions_backup_failed))
        }
    }
    private val open_restore_document = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val imported_actions = withContext(Dispatchers.IO) {
                runCatching { read_custom_actions_backup(contentResolver, uri) }
            }
            imported_actions
                .onSuccess { actions -> pending_restore_actions = actions }
                .onFailure { show_toast(getString(R.string.custom_actions_restore_failed)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        manager.refresh_state()

        setContent {
            val state by manager.state.collectAsState()
            val running_action_id by manager.running_action_id.collectAsState()
            val custom_actions by custom_actions_repository.actions.collectAsState()
            MainScreen(
                state = state,
                running_action_id = running_action_id,
                custom_actions = custom_actions,
                deleted_action = delete_undo_state.deleted_action,
                on_request_permission = manager::request_permission,
                on_try_action = ::try_action,
                on_pin_shortcut = ::pin_shortcut,
                on_add_custom_action = ::add_custom_action,
                on_update_custom_action = custom_actions_repository::update_action,
                on_delete_custom_action = { action_id ->
                    custom_actions_repository.delete_action(action_id)?.let { delete_undo_state.deleted_action = it }
                },
                on_restore_custom_action = { deleted_action ->
                    if (delete_undo_state.deleted_action == deleted_action) {
                        custom_actions_repository.restore_action(deleted_action)
                        delete_undo_state.deleted_action = null
                    }
                },
                on_dismiss_delete_undo = { deleted_action ->
                    if (delete_undo_state.deleted_action == deleted_action) delete_undo_state.deleted_action = null
                },
                on_backup_custom_actions = ::backup_custom_actions,
                on_restore_custom_actions = ::select_restore_backup,
                pending_restore_count = pending_restore_actions?.size,
                on_confirm_restore_custom_actions = ::confirm_restore_custom_actions,
                on_dismiss_restore_custom_actions = { pending_restore_actions = null }
            )
        }
    }

    private fun pin_shortcut(action: AppActionItem) {
        val shortcut = ActionCatalog.build_pinned_shortcut(this, action)
        val was_requested = ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
        val message_res = when {
            was_requested -> R.string.pin_success
            !ShortcutManagerCompat.isRequestPinShortcutSupported(this) -> R.string.pin_not_supported
            else -> R.string.pin_failed
        }
        show_toast(getString(message_res))
    }

    private fun try_action(action: AppActionItem) {
        lifecycleScope.launch {
            val result = manager.perform_action(action)
            val message = when (result.status_code) {
                ActionResult.STATUS_SUCCESS -> getString(
                    when (action.id) {
                        ShortcutActions.expand_notifications.id -> R.string.try_notifications_success
                        ShortcutActions.expand_quick_settings.id -> R.string.try_quick_settings_success
                        ShortcutActions.take_screenshot.id -> R.string.try_screenshot_success
                        ShortcutActions.screen_off.id -> R.string.try_screen_off_success
                        else -> R.string.try_custom_action_success
                    }
                )
                ActionResult.STATUS_SHIZUKU_UNAVAILABLE -> getString(R.string.dispatch_need_shizuku)
                ActionResult.STATUS_PERMISSION_DENIED -> getString(R.string.dispatch_need_permission)
                ActionResult.STATUS_BUSY -> null
                else -> result.message.ifBlank { getString(R.string.dispatch_failed) }
            }
            message?.let(::show_toast)
            manager.refresh_state()
        }
    }

    private fun show_toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun add_custom_action(label: String, shell_command: String): Int? {
        val validation_error = validate_custom_action(label, shell_command)
        if (validation_error != null) return validation_error
        custom_actions_repository.add_action(label, shell_command)
        return null
    }

    private fun backup_custom_actions() {
        create_backup_document.launch(custom_actions_backup_file_name())
    }

    private fun select_restore_backup() {
        open_restore_document.launch(arrayOf("application/json"))
    }

    private fun confirm_restore_custom_actions() {
        val actions = pending_restore_actions ?: return
        lifecycleScope.launch {
            try {
                val synchronized = custom_actions_repository.replace_all_actions(actions)
                pending_restore_actions = null
                show_toast(getString(if (synchronized) R.string.custom_actions_restore_success else R.string.custom_actions_restore_sync_failed))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                show_toast(getString(R.string.custom_actions_restore_failed))
            }
        }
    }
}

internal class DeleteUndoState : ViewModel() {
    var deleted_action by mutableStateOf<IndexedValue<CustomAction>?>(null)
}
