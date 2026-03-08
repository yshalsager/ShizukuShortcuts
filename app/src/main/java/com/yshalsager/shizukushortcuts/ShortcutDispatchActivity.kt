package com.yshalsager.shizukushortcuts

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ShortcutDispatchActivity : ComponentActivity() {
    private val manager by lazy { AppServices.shizuku_manager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = ShortcutActions.find_by_intent(intent)
        if (action == null) {
            Toast.makeText(this, getString(R.string.dispatch_missing_action), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            handle_result(manager.perform_action(action))
        }
    }

    private fun handle_result(result: ActionResult) {
        when (result.status_code) {
            ActionResult.STATUS_SUCCESS -> finish()
            ActionResult.STATUS_SHIZUKU_UNAVAILABLE -> open_setup(getString(R.string.dispatch_need_shizuku))
            ActionResult.STATUS_PERMISSION_DENIED -> open_setup(getString(R.string.dispatch_need_permission))
            else -> {
                Toast.makeText(this, result.message.ifBlank { getString(R.string.dispatch_failed) }, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun open_setup(message: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.extra_message, message)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }
}
