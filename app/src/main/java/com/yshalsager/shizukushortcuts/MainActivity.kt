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
import androidx.compose.foundation.text.BasicTextField
import androidx.annotation.DrawableRes
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
    private val custom_actions_repository by lazy { AppServices.custom_actions_repository(this) }
    private var inbound_message by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        inbound_message = intent.getStringExtra(extra_message).orEmpty()
        manager.refresh_state()

        setContent {
            val state by manager.state.collectAsState()
            val custom_actions by custom_actions_repository.actions.collectAsState()
            MainScreen(
                state = state,
                custom_actions = custom_actions,
                inbound_message = inbound_message,
                on_request_permission = manager::request_permission,
                on_try_action = ::try_action,
                on_pin_shortcut = ::pin_shortcut,
                on_add_custom_action = ::add_custom_action,
                on_update_custom_action = custom_actions_repository::update_action,
                on_delete_custom_action = custom_actions_repository::delete_action
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        inbound_message = intent.getStringExtra(extra_message).orEmpty()
    }

    private fun pin_shortcut(action: AppActionItem) {
        val shortcut = ActionCatalog.build_pinned_shortcut(this, action)
        val was_requested = ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
        val message_res = when {
            was_requested -> R.string.pin_success
            !ShortcutManagerCompat.isRequestPinShortcutSupported(this) -> R.string.pin_not_supported
            else -> R.string.pin_failed
        }
        Toast.makeText(this, getString(message_res), Toast.LENGTH_SHORT).show()
    }

    private fun try_action(action: AppActionItem) {
        lifecycleScope.launch {
            val result = manager.perform_action(action)
            val message = when (result.status_code) {
                ActionResult.STATUS_SUCCESS -> getString(
                    when (action.id) {
                        ShortcutActions.expand_notifications.id -> R.string.try_notifications_success
                        ShortcutActions.expand_quick_settings.id -> R.string.try_quick_settings_success
                        ShortcutActions.take_screenshot.id -> R.string.try_screenshot_success
                        ShortcutActions.screen_off.id -> R.string.try_screen_off_success
                        else -> R.string.try_custom_action_success
                    }
                )
                ActionResult.STATUS_SHIZUKU_UNAVAILABLE -> getString(R.string.dispatch_need_shizuku)
                ActionResult.STATUS_PERMISSION_DENIED -> getString(R.string.dispatch_need_permission)
                else -> result.message.ifBlank { getString(R.string.dispatch_failed) }
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            manager.refresh_state()
        }
    }

    private fun add_custom_action(label: String, shell_command: String): Int? {
        val validation_error = validate_custom_action(label, shell_command)
        if (validation_error != null) return validation_error
        custom_actions_repository.add_action(label, shell_command)
        return null
    }
}

@Composable
private fun MainScreen(
    state: ShizukuState,
    custom_actions: List<CustomAction>,
    inbound_message: String,
    on_request_permission: () -> Unit,
    on_try_action: (AppActionItem) -> Unit,
    on_pin_shortcut: (AppActionItem) -> Unit,
    on_add_custom_action: (String, String) -> Int?,
    on_update_custom_action: (String, String, String) -> Unit,
    on_delete_custom_action: (String) -> Unit
) {
    val colors = shizuku_shortcuts_colors()
    val is_ready = state.is_running && state.is_permission_granted
    val context = LocalContext.current
    val built_in_actions = remember(context) { ActionCatalog.built_in_actions(context) }
    val custom_action_items = remember(custom_actions) {
        custom_actions.asReversed().map { action ->
            AppActionItem(
                id = action.id,
                short_label = action.label,
                long_label = action.label,
                icon_res = R.drawable.ic_shortcut_custom_action,
                shell_command = action.shell_command
            )
        }
    }
    var is_add_dialog_visible by remember { mutableStateOf(false) }
    var editing_action by remember { mutableStateOf<CustomAction?>(null) }

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
                SectionHeader(colors = colors, title = stringResource(R.string.available_actions_title))
            }

            items(built_in_actions.size) { index ->
                ActionRow(
                    colors = colors,
                    action = built_in_actions[index],
                    is_ready = is_ready,
                    on_try_action = on_try_action,
                    on_pin_shortcut = on_pin_shortcut,
                    on_delete_action = null
                )
            }

            item {
                SectionHeader(
                    colors = colors,
                    title = stringResource(R.string.custom_actions_title),
                    action_label = stringResource(R.string.add_custom_action),
                    on_action = { is_add_dialog_visible = true }
                )
            }

            if (custom_action_items.isEmpty()) {
                item {
                    InlineInfoPanel(colors = colors, text = stringResource(R.string.custom_actions_empty))
                }
            } else {
                items(custom_action_items.size) { index ->
                    ActionRow(
                        colors = colors,
                        action = custom_action_items[index],
                        is_ready = is_ready,
                        on_try_action = on_try_action,
                        on_pin_shortcut = on_pin_shortcut,
                        on_edit_action = { action ->
                            editing_action = custom_actions.firstOrNull { it.id == action.id }
                        },
                        on_delete_action = on_delete_custom_action
                    )
                }
            }
        }

        if (is_add_dialog_visible || editing_action != null) {
            AddCustomActionDialog(
                colors = colors,
                title = stringResource(if (editing_action == null) R.string.add_custom_action_title else R.string.edit_custom_action_title),
                submit_label = stringResource(if (editing_action == null) R.string.add_custom_action else R.string.save_action),
                initial_label = editing_action?.label.orEmpty(),
                initial_shell_command = editing_action?.shell_command.orEmpty(),
                on_dismiss = {
                    is_add_dialog_visible = false
                    editing_action = null
                },
                on_submit = { label, shell_command ->
                    val error = validate_custom_action(label, shell_command)
                    if (error == null) {
                        editing_action?.let { on_update_custom_action(it.id, label, shell_command) }
                            ?: run { on_add_custom_action(label, shell_command) }
                        is_add_dialog_visible = false
                        editing_action = null
                    }
                    error
                }
            )
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
private fun SectionHeader(colors: AppColors, title: String, action_label: String? = null, on_action: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BasicText(
            text = title,
            style = eyebrow_text_style(colors)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.border.copy(alpha = 0.8f))
        )
        if (action_label != null && on_action != null) {
            BasicText(
                text = action_label,
                modifier = Modifier.clickable(onClick = on_action),
                style = action_button_text_style(colors.accent)
            )
        }
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
    action: AppActionItem,
    is_ready: Boolean,
    on_try_action: (AppActionItem) -> Unit,
    on_pin_shortcut: (AppActionItem) -> Unit,
    on_edit_action: ((AppActionItem) -> Unit)? = null,
    on_delete_action: ((String) -> Unit)?
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
            text = action.short_label,
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
                icon_res = R.drawable.ic_pin,
                content_description = stringResource(R.string.pin_action),
                on_click = { on_pin_shortcut(action) }
            )
            if (on_edit_action != null) {
                IconActionButton(
                    colors = colors,
                    icon_res = R.drawable.ic_edit,
                    content_description = stringResource(R.string.edit_action),
                    on_click = { on_edit_action(action) }
                )
            }
            if (on_delete_action != null) {
                IconActionButton(
                    colors = colors,
                    icon_res = R.drawable.ic_delete,
                    content_description = stringResource(R.string.delete_action),
                    on_click = { on_delete_action(action.id) }
                )
            }
        }
    }
}

@Composable
private fun AddCustomActionDialog(
    colors: AppColors,
    title: String,
    submit_label: String,
    initial_label: String,
    initial_shell_command: String,
    on_dismiss: () -> Unit,
    on_submit: (String, String) -> Int?
) {
    var label by remember(initial_label) { mutableStateOf(initial_label) }
    var shell_command by remember(initial_shell_command) { mutableStateOf(initial_shell_command) }
    var error_res by remember(initial_label, initial_shell_command) { mutableStateOf<Int?>(null) }

    Dialog(onDismissRequest = on_dismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BasicText(
                text = title,
                style = action_title_text_style(colors)
            )

            LabeledField(
                colors = colors,
                label = stringResource(R.string.custom_action_label),
                value = label,
                placeholder = stringResource(R.string.custom_action_label_placeholder),
                on_value_change = {
                    label = it
                    error_res = null
                }
            )

            LabeledField(
                colors = colors,
                label = stringResource(R.string.custom_action_shell_command),
                value = shell_command,
                placeholder = stringResource(R.string.custom_action_shell_placeholder),
                on_value_change = {
                    shell_command = it
                    error_res = null
                }
            )

            error_res?.let {
                InlineInfoPanel(colors = colors, text = stringResource(it))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                InlineActionButton(
                    colors = colors,
                    label = stringResource(R.string.cancel_action).uppercase(),
                    enabled = true,
                    on_click = on_dismiss
                )
                FilledActionButton(
                    colors = colors,
                    label = submit_label,
                    on_click = {
                        error_res = on_submit(label, shell_command)
                    }
                )
            }
        }
    }
}

@Composable
private fun LabeledField(
    colors: AppColors,
    label: String,
    value: String,
    placeholder: String,
    on_value_change: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(
            text = label,
            style = body_text_style(colors.text_muted)
        )
        BasicTextField(
            value = value,
            onValueChange = on_value_change,
            textStyle = body_text_style(colors.text),
            decorationBox = { inner_text_field ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface_alt)
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (value.isBlank()) {
                        BasicText(
                            text = placeholder,
                            style = body_text_style(colors.text_muted)
                        )
                    }
                    inner_text_field()
                }
            }
        )
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
    @DrawableRes icon_res: Int,
    content_description: String,
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
            painter = painterResource(icon_res),
            contentDescription = content_description,
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
