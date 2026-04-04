package com.yshalsager.shizukushortcuts

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yshalsager.shizukushortcuts.ui.theme.AppColors
import com.yshalsager.shizukushortcuts.ui.theme.shizuku_shortcuts_colors

class ActionWidgetConfigureActivity : ComponentActivity() {
    private var app_widget_id = AppWidgetManager.INVALID_APPWIDGET_ID
    private val custom_actions_repository by lazy { AppServices.custom_actions_repository(this) }
    private val widget_bindings_repository by lazy { AppServices.widget_bindings_repository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app_widget_id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (app_widget_id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setResult(
            RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, app_widget_id)
        )

        setContent {
            val custom_actions by custom_actions_repository.actions.collectAsState()
            val actions = ActionCatalog.built_in_actions(this@ActionWidgetConfigureActivity) + custom_actions.asReversed().map { action ->
                AppActionItem(
                    id = action.id,
                    short_label = action.label,
                    long_label = action.label,
                    icon_res = R.drawable.ic_shortcut_custom_action,
                    shell_command = action.shell_command
                )
            }

            ActionWidgetConfigureScreen(
                actions = actions,
                on_select_action = ::save_binding_and_finish
            )
        }
    }

    private fun save_binding_and_finish(action: AppActionItem) {
        widget_bindings_repository.set_binding(app_widget_id, action.id)
        ActionWidgetProvider.refresh_widgets(this, intArrayOf(app_widget_id))
        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, app_widget_id)
        )
        finish()
    }
}

@Composable
private fun ActionWidgetConfigureScreen(
    actions: List<AppActionItem>,
    on_select_action: (AppActionItem) -> Unit
) {
    val colors = shizuku_shortcuts_colors()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(colors.background, colors.background_accent, colors.background)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BasicText(
                text = stringResource(R.string.widget_configure_title),
                style = TextStyle(
                    color = colors.text,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
            )

            BasicText(
                text = stringResource(R.string.widget_choose_action_prompt),
                style = TextStyle(
                    color = colors.text_muted,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif
                )
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(actions.size) { index ->
                    WidgetActionRow(
                        colors = colors,
                        action = actions[index],
                        on_select_action = on_select_action
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetActionRow(
    colors: AppColors,
    action: AppActionItem,
    on_select_action: (AppActionItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .clickable { on_select_action(action) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(action.icon_res),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.accent),
            modifier = Modifier.size(20.dp)
        )

        BasicText(
            text = action.short_label,
            style = TextStyle(
                color = colors.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif
            )
        )
    }
}
