package com.yshalsager.shizukushortcuts

import android.content.Context

object AppServices {
    private var default_manager: ShizukuManagerContract? = null
    private var default_custom_actions_repository: CustomActionsRepositoryContract? = null
    private var default_widget_bindings_repository: WidgetBindingsRepository? = null

    @Volatile
    var manager_factory: (Context) -> ShizukuManagerContract = { context ->
        default_manager ?: synchronized(this) {
            default_manager ?: AppShizukuManager(context.applicationContext).also { default_manager = it }
        }
    }

    @Volatile
    var custom_actions_repository_factory: (Context) -> CustomActionsRepositoryContract = { context ->
        default_custom_actions_repository ?: synchronized(this) {
            default_custom_actions_repository ?: AppCustomActionsRepository(context.applicationContext).also {
                default_custom_actions_repository = it
            }
        }
    }

    fun shizuku_manager(context: Context) = manager_factory(context.applicationContext)
    fun custom_actions_repository(context: Context) = custom_actions_repository_factory(context.applicationContext)
    fun widget_bindings_repository(context: Context) =
        default_widget_bindings_repository ?: synchronized(this) {
            default_widget_bindings_repository ?: WidgetBindingsRepository(context.applicationContext).also {
                default_widget_bindings_repository = it
            }
        }

    fun reset_for_tests() {
        default_manager = null
        default_custom_actions_repository = null
        default_widget_bindings_repository = null
        manager_factory = { context ->
            default_manager ?: synchronized(this) {
                default_manager ?: AppShizukuManager(context.applicationContext).also { default_manager = it }
            }
        }
        custom_actions_repository_factory = { context ->
            default_custom_actions_repository ?: synchronized(this) {
                default_custom_actions_repository ?: AppCustomActionsRepository(context.applicationContext).also {
                    default_custom_actions_repository = it
                }
            }
        }
    }
}
