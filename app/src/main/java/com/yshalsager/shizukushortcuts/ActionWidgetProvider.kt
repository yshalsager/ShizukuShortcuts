package com.yshalsager.shizukushortcuts

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context

class ActionWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, app_widget_manager: AppWidgetManager, app_widget_ids: IntArray) {
        refresh_widgets(context, app_widget_ids)
    }

    override fun onDeleted(context: Context, app_widget_ids: IntArray) {
        AppServices.widget_bindings_repository(context).remove_bindings(app_widget_ids)
        refresh_widgets(context)
    }

    override fun onDisabled(context: Context) {
        AppServices.widget_bindings_repository(context).clear_all_bindings()
    }

    companion object {
        fun refresh_widgets(context: Context, app_widget_ids: IntArray? = null) {
            val app_widget_manager = AppWidgetManager.getInstance(context)
            val ids = app_widget_ids ?: app_widget_manager.getAppWidgetIds(ComponentName(context, ActionWidgetProvider::class.java))
            if (ids.isEmpty()) return
            ids.forEach { app_widget_id ->
                app_widget_manager.updateAppWidget(app_widget_id, ActionWidgetRenderer.build_remote_views(context, app_widget_id))
            }
        }
    }
}
