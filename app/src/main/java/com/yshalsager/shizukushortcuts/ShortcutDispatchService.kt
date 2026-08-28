package com.yshalsager.shizukushortcuts

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ShortcutDispatchService : Service() {
    private val manager by lazy { AppServices.shizuku_manager(this) }
    private val scope = MainScope()
    private var running_job: Job? = null
    private var latest_start_id = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, start_id: Int): Int {
        val action = ActionCatalog.find_by_id(this, intent?.getStringExtra(ShortcutActions.extra_action_id))
        if (action == null) {
            if (running_job?.isActive == true) latest_start_id = start_id else stopSelf(start_id)
            return START_NOT_STICKY
        }
        latest_start_id = start_id
        if (running_job?.isActive == true) return START_NOT_STICKY

        running_job = scope.launch {
            val result = manager.perform_action(action)
            val message = when (result.status_code) {
                ActionResult.STATUS_SUCCESS, ActionResult.STATUS_BUSY -> null
                ActionResult.STATUS_SHIZUKU_UNAVAILABLE -> getString(R.string.dispatch_need_shizuku)
                ActionResult.STATUS_PERMISSION_DENIED -> getString(R.string.dispatch_need_permission)
                else -> result.message.ifBlank { getString(R.string.dispatch_failed) }
            }
            message?.let { Toast.makeText(this@ShortcutDispatchService, it, Toast.LENGTH_SHORT).show() }
            stopSelf(latest_start_id)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun build_intent(context: Context, action: AppActionItem) =
            Intent(context, ShortcutDispatchService::class.java)
                .putExtra(ShortcutActions.extra_action_id, action.id)
    }
}
