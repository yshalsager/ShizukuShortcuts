package com.yshalsager.shizukushortcuts

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

data class ShizukuState(
    val is_running: Boolean = false,
    val is_permission_granted: Boolean = false,
    val should_show_permission_rationale: Boolean = false
)

interface ShizukuManagerContract {
    val state: StateFlow<ShizukuState>
    fun refresh_state()
    fun request_permission()
    suspend fun perform_action(action: AppActionItem): ActionResult
}

class AppShizukuManager(app_context: Context) : ShizukuManagerContract {
    companion object {
        private const val permission_request_code = 4001
        private const val service_tag = "statusbar_shortcuts"
        private const val service_version = 2
    }

    private val state_flow = MutableStateFlow(ShizukuState())
    private val worker_scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val user_service_args = Shizuku.UserServiceArgs(
        ComponentName(app_context.packageName, PrivilegedStatusBarService::class.java.name)
    )
        .daemon(false)
        .tag(service_tag)
        .version(service_version)
        .processNameSuffix("statusbar_shortcuts")

    override val state: StateFlow<ShizukuState> = state_flow.asStateFlow()

    init {
        Shizuku.addBinderReceivedListenerSticky { refresh_state() }
        Shizuku.addBinderDeadListener { refresh_state() }
        Shizuku.addRequestPermissionResultListener { request_code, grant_result ->
            if (request_code == permission_request_code) {
                state_flow.value = current_state(grant_result == PackageManager.PERMISSION_GRANTED)
            }
        }
        refresh_state()
    }

    override fun refresh_state() {
        state_flow.value = current_state()
    }

    override fun request_permission() {
        if (!Shizuku.pingBinder()) {
            refresh_state()
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            refresh_state()
            return
        }
        Shizuku.requestPermission(permission_request_code)
    }

    override suspend fun perform_action(action: AppActionItem): ActionResult {
        if (!Shizuku.pingBinder()) return ActionResult.shizuku_unavailable(action.id)
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return ActionResult.permission_denied(action.id)
        }

        return suspendCancellableCoroutine { continuation ->
            var is_finished = false
            lateinit var connection: ServiceConnection

            fun finish(result: ActionResult) {
                if (is_finished) return
                is_finished = true
                runCatching { Shizuku.unbindUserService(user_service_args, connection, false) }
                if (continuation.isActive) continuation.resume(result)
            }

            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    worker_scope.launch {
                        val remote = IPrivilegedStatusBarService.Stub.asInterface(service)
                        val result = runCatching {
                            action.shell_command?.let { remote.perform_custom_action(action.id, it) }
                                ?: remote.perform_action(action.id)
                        }
                            .getOrElse { exception ->
                                ActionResult.execution_failed(
                                    action_id = action.id,
                                    executed_command = action.shell_command ?: action.id,
                                    message = exception.message ?: "Remote execution failed"
                                )
                            }
                        finish(result)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    finish(
                        ActionResult.execution_failed(
                            action_id = action.id,
                            executed_command = action.shell_command ?: action.id,
                            message = "User service disconnected"
                        )
                    )
                }
            }

            continuation.invokeOnCancellation {
                runCatching { Shizuku.unbindUserService(user_service_args, connection, false) }
            }

            runCatching { Shizuku.bindUserService(user_service_args, connection) }
                .onFailure { exception ->
                    finish(
                        ActionResult.execution_failed(
                            action_id = action.id,
                            executed_command = action.shell_command ?: action.id,
                            message = exception.message ?: "Could not bind user service"
                        )
                    )
                }
        }
    }

    private fun current_state(permission_granted_override: Boolean? = null): ShizukuState {
        val is_running = Shizuku.pingBinder()
        val is_permission_granted = permission_granted_override ?: (
            is_running && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        )
        val should_show_permission_rationale = is_running &&
            !is_permission_granted &&
            Shizuku.shouldShowRequestPermissionRationale()

        return ShizukuState(
            is_running = is_running,
            is_permission_granted = is_permission_granted,
            should_show_permission_rationale = should_show_permission_rationale
        )
    }
}
