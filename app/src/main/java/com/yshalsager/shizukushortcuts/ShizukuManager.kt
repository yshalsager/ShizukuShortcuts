package com.yshalsager.shizukushortcuts

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

data class ShizukuState(
    val is_running: Boolean = false,
    val is_permission_granted: Boolean = false,
    val should_show_permission_rationale: Boolean = false
)

interface ShizukuManagerContract {
    val state: StateFlow<ShizukuState>
    val running_action_id: StateFlow<String?>
    fun refresh_state()
    fun request_permission()
    suspend fun perform_action(action: AppActionItem): ActionResult
}

class AppShizukuManager(app_context: Context) : ShizukuManagerContract {
    companion object {
        private const val permission_request_code = 4001
        private const val binder_readiness_timeout_ms = 1_250L
        private const val user_service_timeout_ms = 7_000L
        private const val service_tag = "statusbar_shortcuts"
        private const val service_version = 2
    }

    private val state_flow = MutableStateFlow(ShizukuState())
    private val running_action_flow = MutableStateFlow<String?>(null)
    // ponytail: one worker contains stuck Binder calls; use cancellable oneway calls if parallelism matters
    private val worker_scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val user_service_args = Shizuku.UserServiceArgs(
        ComponentName(app_context.packageName, PrivilegedStatusBarService::class.java.name)
    )
        .daemon(false)
        .tag(service_tag)
        .version(service_version)
        .processNameSuffix("statusbar_shortcuts")

    override val state: StateFlow<ShizukuState> = state_flow.asStateFlow()
    override val running_action_id: StateFlow<String?> = running_action_flow.asStateFlow()

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
        if (!running_action_flow.compareAndSet(null, action.id)) return ActionResult.busy(action.id)
        return withContext(NonCancellable) {
            try {
                if (!await_binder()) return@withContext ActionResult.shizuku_unavailable(action.id)
                val permission = runCatching { Shizuku.checkSelfPermission() }.getOrNull()
                    ?: return@withContext ActionResult.shizuku_unavailable(action.id)
                if (permission != PackageManager.PERMISSION_GRANTED) {
                    return@withContext ActionResult.permission_denied(action.id)
                }
                withTimeoutOrNull(user_service_timeout_ms) { bind_and_perform(action) }
                    ?: ActionResult.execution_timed_out(
                        action_id = action.id,
                        executed_command = action.shell_command ?: action.id
                    )
            } finally {
                running_action_flow.value = null
            }
        }
    }

    private suspend fun bind_and_perform(action: AppActionItem): ActionResult = suspendCancellableCoroutine { continuation ->
        val completed = AtomicBoolean()
        lateinit var connection: ServiceConnection

        fun finish(result: ActionResult? = null) {
            if (!completed.compareAndSet(false, true)) return
            worker_scope.launch {
                runCatching { Shizuku.unbindUserService(user_service_args, connection, false) }
            }
            if (result != null && continuation.isActive) continuation.resume(result)
        }

        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                worker_scope.launch {
                    if (completed.get()) return@launch
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

        worker_scope.launch {
            if (completed.get()) return@launch
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
        continuation.invokeOnCancellation { finish() }
    }

    private suspend fun await_binder(): Boolean {
        if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return true
        refresh_state()
        return withTimeoutOrNull(binder_readiness_timeout_ms) { state.first { it.is_running } } != null
    }

    private fun current_state(permission_granted_override: Boolean? = null): ShizukuState {
        val is_running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!is_running) return ShizukuState()

        val is_permission_granted = permission_granted_override
            ?: runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        val should_show_permission_rationale = !is_permission_granted &&
            runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false)

        return ShizukuState(
            is_running = true,
            is_permission_granted = is_permission_granted,
            should_show_permission_rationale = should_show_permission_rationale
        )
    }
}
