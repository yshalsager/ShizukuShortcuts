package com.yshalsager.shizukushortcuts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ShortcutMigrationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending_result = goAsync()
        (AppServices.custom_actions_repository(context) as AppCustomActionsRepository)
            .initial_shortcut_sync
            .invokeOnCompletion { pending_result.finish() }
    }
}
