package com.yshalsager.shizukushortcuts

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ShortcutDispatchActivity : ComponentActivity() {
    private val manager by lazy { AppServices.shizuku_manager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = ActionCatalog.find_by_intent(this, intent)
        if (action == null) {
            Toast.makeText(this, getString(R.string.dispatch_missing_action), Toast.LENGTH_SHORT).show()
            finishAndRemoveTask()
            return
        }

        lifecycleScope.launch {
            handle_result(manager.perform_action(action))
        }
    }

    private fun handle_result(result: ActionResult) {
        val message = when (result.status_code) {
            ActionResult.STATUS_SUCCESS -> null
            ActionResult.STATUS_SHIZUKU_UNAVAILABLE -> getString(R.string.dispatch_need_shizuku)
            ActionResult.STATUS_PERMISSION_DENIED -> getString(R.string.dispatch_need_permission)
            else -> result.message.ifBlank { getString(R.string.dispatch_failed) }
        }
        message?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        finishAndRemoveTask()
    }
}
