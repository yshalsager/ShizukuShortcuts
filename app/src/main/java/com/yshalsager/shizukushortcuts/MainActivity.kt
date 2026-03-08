package com.yshalsager.shizukushortcuts

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.lifecycleScope
import com.yshalsager.shizukushortcuts.ui.theme.AppColors
import com.yshalsager.shizukushortcuts.ui.theme.shizuku_shortcuts_colors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        const val extra_message = "extra_message"
    }

    private val manager by lazy { AppServices.shizuku_manager(this) }
    private var inbound_message by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        inbound_message = intent.getStringExtra(extra_message).orEmpty()
        manager.refresh_state()

        setContent {
            val state by manager.state.collectAsState()
            MainScreen(
                state = state,
                inbound_message = inbound_message,
                on_request_permission = manager::request_permission,
                on_try_action = ::try_action,
                on_pin_shortcut = ::pin_shortcut
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        inbound_message = intent.getStringExtra(extra_message).orEmpty()
    }

    private fun pin_shortcut(action: ShortcutAction) {
        val shortcut = ShortcutActions.build_pinned_shortcut(this, action)
        val was_requested = ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
        val message_res = when {
            was_requested -> R.string.pin_success
            !ShortcutManagerCompat.isRequestPinShortcutSupported(this) -> R.string.pin_not_supported
            else -> R.string.pin_failed
        }
        Toast.makeText(this, getString(message_res), Toast.LENGTH_SHORT).show()
    }

    private fun try_action(action: ShortcutAction) {
        lifecycleScope.launch {
            val result = manager.perform_action(action)
            val message = when (result.status_code) {
                ActionResult.STATUS_SUCCESS -> getString(
                    if (action == ShortcutActions.expand_notifications) R.string.try_notifications_success else R.string.try_quick_settings_success
                )
                ActionResult.STATUS_SHIZUKU_UNAVAILABLE -> getString(R.string.dispatch_need_shizuku)
                ActionResult.STATUS_PERMISSION_DENIED -> getString(R.string.dispatch_need_permission)
                else -> result.message.ifBlank { getString(R.string.dispatch_failed) }
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            manager.refresh_state()
        }
    }
}

@Composable
private fun MainScreen(
    state: ShizukuState,
    inbound_message: String,
    on_request_permission: () -> Unit,
    on_try_action: (ShortcutAction) -> Unit,
    on_pin_shortcut: (ShortcutAction) -> Unit
) {
    val colors = shizuku_shortcuts_colors()
    val is_ready = state.is_running && state.is_permission_granted

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(colors.background, colors.background_accent, colors.background)
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical)),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Header(colors = colors)
            }

            item {
                StatusSection(colors = colors, state = state, inbound_message = inbound_message, on_request_permission = on_request_permission)
            }

            item {
                SectionHeader(colors = colors)
            }

            items(ShortcutActions.all.size) { index ->
                ActionRow(
                    colors = colors,
                    action = ShortcutActions.all[index],
                    is_ready = is_ready,
                    on_try_action = on_try_action,
                    on_pin_shortcut = on_pin_shortcut
                )
            }
        }
    }
}

@Composable
private fun Header(colors: AppColors) {
    BasicText(
        text = stringResource(R.string.app_name),
        style = title_text_style(colors)
    )
}

@Composable
private fun InlineInfoPanel(colors: AppColors, text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface_alt)
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        BasicText(
            text = text,
            style = body_text_style(colors.text_muted)
        )
    }
}

@Composable
private fun StatusSection(colors: AppColors, state: ShizukuState, inbound_message: String, on_request_permission: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusBanner(
                colors = colors,
                text = stringResource(if (state.is_running) R.string.status_running_short else R.string.status_not_running_short),
                is_positive = state.is_running,
                modifier = Modifier.weight(1f)
            )
            StatusBanner(
                colors = colors,
                text = stringResource(
                    if (state.is_permission_granted) R.string.status_permission_granted_short else R.string.status_permission_missing_short
                ),
                is_positive = state.is_permission_granted,
                modifier = Modifier.weight(1f)
            )
        }

        if (inbound_message.isNotBlank()) {
            InlineInfoPanel(colors = colors, text = inbound_message)
        }

        if (!state.is_running) {
            InlineInfoPanel(colors = colors, text = stringResource(R.string.dispatch_need_shizuku))
        }

        if (state.is_running && !state.is_permission_granted) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface_alt)
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BasicText(
                    text = stringResource(R.string.dispatch_need_permission),
                    modifier = Modifier.weight(1f),
                    style = body_text_style(colors.text_muted)
                )
                FilledActionButton(
                    colors = colors,
                    label = stringResource(R.string.request_permission),
                    on_click = on_request_permission
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(colors: AppColors) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BasicText(
            text = stringResource(R.string.available_actions_title),
            style = eyebrow_text_style(colors)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.border.copy(alpha = 0.8f))
        )
    }
}

@Composable
private fun FilledActionButton(
    colors: AppColors,
    label: String,
    on_click: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.accent)
            .clickable(onClick = on_click)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                color = colors.accent_text
            )
        )
    }
}

@Composable
private fun StatusBanner(
    colors: AppColors,
    text: String,
    is_positive: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(if (is_positive) colors.success_surface else colors.surface_alt)
            .border(1.dp, if (is_positive) colors.success_border else colors.border, CircleShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusGlyph(
            background = if (is_positive) colors.success else colors.text_muted,
            text = if (is_positive) "✓" else "!"
        )
        BasicText(
            text = text,
            style = banner_text_style(if (is_positive) colors.success else colors.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatusGlyph(background: Color, text: String) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color.White
            )
        )
    }
}

@Composable
private fun ActionRow(
    colors: AppColors,
    action: ShortcutAction,
    is_ready: Boolean,
    on_try_action: (ShortcutAction) -> Unit,
    on_pin_shortcut: (ShortcutAction) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (is_ready) 1f else 0.56f)
            .clip(RoundedCornerShape(22.dp))
            .background(if (is_ready) colors.surface else colors.surface_alt)
            .border(1.dp, colors.border, RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (is_ready) colors.accent_soft else colors.surface_raised),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(action.icon_res),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(if (is_ready) colors.accent else colors.text_muted)
            )
        }

        BasicText(
            text = stringResource(action.short_label_res),
            modifier = Modifier.weight(1f),
            style = action_title_text_style(colors),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InlineActionButton(
                colors = colors,
                label = stringResource(R.string.try_action).uppercase(),
                enabled = is_ready,
                on_click = { on_try_action(action) }
            )
            IconActionButton(
                colors = colors,
                on_click = { on_pin_shortcut(action) }
            )
        }
    }
}

@Composable
private fun InlineActionButton(
    colors: AppColors,
    label: String,
    enabled: Boolean,
    on_click: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = on_click)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        BasicText(text = label, style = action_button_text_style(if (enabled) colors.accent else colors.text_muted))
    }
}

@Composable
private fun IconActionButton(
    colors: AppColors,
    on_click: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(colors.surface_alt)
            .clickable(onClick = on_click),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_pin),
            contentDescription = stringResource(R.string.pin_action),
            modifier = Modifier.size(14.dp),
            colorFilter = ColorFilter.tint(colors.text_muted)
        )
    }
}

private fun title_text_style(colors: AppColors) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 22.sp,
    lineHeight = 25.sp,
    color = colors.text
)

private fun banner_text_style(color: Color) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 13.sp,
    lineHeight = 15.sp,
    letterSpacing = 0.4.sp,
    color = color
)

private fun eyebrow_text_style(colors: AppColors) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 12.sp,
    lineHeight = 14.sp,
    letterSpacing = 2.1.sp,
    color = colors.text_muted
)

private fun action_title_text_style(colors: AppColors) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp,
    lineHeight = 18.sp,
    color = colors.text
)

private fun body_text_style(color: Color) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 13.sp,
    lineHeight = 17.sp,
    color = color
)

private fun action_button_text_style(color: Color) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    color = color
)
