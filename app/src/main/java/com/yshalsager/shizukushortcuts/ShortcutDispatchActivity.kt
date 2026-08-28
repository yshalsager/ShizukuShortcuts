package com.yshalsager.shizukushortcuts

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

class ShortcutDispatchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = ActionCatalog.find_by_intent(this, intent)
        if (action == null) {
            Toast.makeText(this, getString(R.string.dispatch_missing_action), Toast.LENGTH_SHORT).show()
        } else {
            startService(ShortcutDispatchService.build_intent(this, action))
        }
        finishAndRemoveTask()
    }
}
