package com.yshalsager.shizukushortcuts

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.RemoteViews

object ActionWidgetRenderer {
    fun build_remote_views(app_context: android.content.Context, app_widget_id: Int): RemoteViews {
        val action_id = AppServices.widget_bindings_repository(app_context).get_binding(app_widget_id)
        val action = ActionCatalog.find_by_id(app_context, action_id)
        val remote_views = RemoteViews(app_context.packageName, R.layout.widget_action)

        if (action != null) {
            remote_views.setImageViewResource(R.id.widget_icon, action.icon_res)
            remote_views.setTextViewText(R.id.widget_label, action.short_label)
            remote_views.setOnClickPendingIntent(
                R.id.widget_root,
                dispatch_pending_intent(app_context, app_widget_id, action.id)
            )
        } else {
            remote_views.setImageViewResource(R.id.widget_icon, R.drawable.ic_shortcut_custom_action)
            remote_views.setTextViewText(
                R.id.widget_label,
                app_context.getString(
                    if (action_id == null) R.string.widget_choose_action_prompt else R.string.widget_action_removed
                )
            )
            remote_views.setOnClickPendingIntent(
                R.id.widget_root,
                configure_pending_intent(app_context, app_widget_id)
            )
        }

        return remote_views
    }

    private fun dispatch_pending_intent(
        app_context: android.content.Context,
        app_widget_id: Int,
        action_id: String
    ): PendingIntent {
        val intent = Intent()
            .setClassName(app_context.packageName, ShortcutDispatchActivity::class.java.name)
            .putExtra(ShortcutActions.extra_action_id, action_id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        return PendingIntent.getActivity(
            app_context,
            app_widget_id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun configure_pending_intent(
        app_context: android.content.Context,
        app_widget_id: Int
    ): PendingIntent {
        val intent = Intent()
            .setClassName(app_context.packageName, ActionWidgetConfigureActivity::class.java.name)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, app_widget_id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            app_context,
            app_widget_id + 100_000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
