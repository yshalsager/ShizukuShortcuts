package com.yshalsager.shizukushortcuts

import android.content.Context

object AppServices {
    private var default_manager: ShizukuManagerContract? = null

    @Volatile
    var manager_factory: (Context) -> ShizukuManagerContract = { context ->
        default_manager ?: synchronized(this) {
            default_manager ?: AppShizukuManager(context.applicationContext).also { default_manager = it }
        }
    }

    fun shizuku_manager(context: Context) = manager_factory(context.applicationContext)

    fun reset_for_tests() {
        default_manager = null
        manager_factory = { context ->
            default_manager ?: synchronized(this) {
                default_manager ?: AppShizukuManager(context.applicationContext).also { default_manager = it }
            }
        }
    }
}

