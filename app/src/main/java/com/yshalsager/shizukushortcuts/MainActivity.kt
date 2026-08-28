package com.yshalsager.shizukushortcuts

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val manager by lazy { AppServices.shizuku_manager(this) }
    private val custom_actions_repository by lazy { AppServices.custom_actions_repository(this) }
    private var pending_restore_actions by mutableStateOf<List<CustomAction>?>(null)
    private val create_backup_document = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val was_saved = withContext(Dispatchers.IO) {
                write_custom_actions_backup(contentResolver, uri, custom_actions_repository.actions.value)
            }
            show_toast(getString(if (was_saved) R.string.custom_actions_backup_success else R.string.custom_actions_backup_failed))
        }
    }
    private val open_restore_document = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val imported_actions = withContext(Dispatchers.IO) {
                runCatching { read_custom_actions_backup(contentResolver, uri) }
            }
            imported_actions
                .onSuccess { actions -> pending_restore_actions = actions }
                .onFailure { show_toast(getString(R.string.custom_actions_restore_failed)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        manager.refresh_state()

        setContent {
            val state by manager.state.collectAsState()
            val running_action_id by manager.running_action_id.collectAsState()
            val custom_actions by custom_actions_repository.actions.collectAsState()
            MainScreen(
                state = state,
                running_action_id = running_action_id,
                custom_actions = custom_actions,
                on_request_permission = manager::request_permission,
                on_try_action = ::try_action,
                on_pin_shortcut = ::pin_shortcut,
                on_add_custom_action = ::add_custom_action,
                on_update_custom_action = custom_actions_repository::update_action,
                on_delete_custom_action = custom_actions_repository::delete_action,
                on_backup_custom_actions = ::backup_custom_actions,
                on_restore_custom_actions = ::select_restore_backup,
                pending_restore_count = pending_restore_actions?.size,
                on_confirm_restore_custom_actions = ::confirm_restore_custom_actions,
                on_dismiss_restore_custom_actions = { pending_restore_actions = null }
            )
        }
    }

    private fun pin_shortcut(action: AppActionItem) {
        val shortcut = ActionCatalog.build_pinned_shortcut(this, action)
        val was_requested = ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
        val message_res = when {
            was_requested -> R.string.pin_success
            !ShortcutManagerCompat.isRequestPinShortcutSupported(this) -> R.string.pin_not_supported
            else -> R.string.pin_failed
        }
        show_toast(getString(message_res))
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
                ActionResult.STATUS_BUSY -> null
                else -> result.message.ifBlank { getString(R.string.dispatch_failed) }
            }
            message?.let(::show_toast)
            manager.refresh_state()
        }
    }

    private fun show_toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun add_custom_action(label: String, shell_command: String): Int? {
        val validation_error = validate_custom_action(label, shell_command)
        if (validation_error != null) return validation_error
        custom_actions_repository.add_action(label, shell_command)
        return null
    }

    private fun backup_custom_actions() {
        create_backup_document.launch(custom_actions_backup_file_name())
    }

    private fun select_restore_backup() {
        open_restore_document.launch(arrayOf("application/json"))
    }

    private fun confirm_restore_custom_actions() {
        val actions = pending_restore_actions ?: return
        lifecycleScope.launch {
            try {
                val synchronized = custom_actions_repository.replace_all_actions(actions)
                pending_restore_actions = null
                show_toast(getString(if (synchronized) R.string.custom_actions_restore_success else R.string.custom_actions_restore_sync_failed))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                show_toast(getString(R.string.custom_actions_restore_failed))
            }
        }
    }
}

@Composable
private fun MainScreen(
    state: ShizukuState,
    running_action_id: String?,
    custom_actions: List<CustomAction>,
    on_request_permission: () -> Unit,
    on_try_action: (AppActionItem) -> Unit,
    on_pin_shortcut: (AppActionItem) -> Unit,
    on_add_custom_action: (String, String) -> Int?,
    on_update_custom_action: (String, String, String) -> Unit,
    on_delete_custom_action: (String) -> Unit,
    on_backup_custom_actions: () -> Unit,
    on_restore_custom_actions: () -> Unit,
    pending_restore_count: Int?,
    on_confirm_restore_custom_actions: () -> Unit,
    on_dismiss_restore_custom_actions: () -> Unit
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
                StatusSection(colors = colors, state = state, on_request_permission = on_request_permission)
            }

            item {
                SectionHeader(colors = colors, title = stringResource(R.string.available_actions_title))
            }

            items(built_in_actions.size) { index ->
                ActionRow(
                    colors = colors,
                    action = built_in_actions[index],
                    is_ready = is_ready,
                    running_action_id = running_action_id,
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

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InlineActionButton(
                        colors = colors,
                        label = stringResource(R.string.custom_actions_backup).uppercase(),
                        enabled = true,
                        on_click = on_backup_custom_actions
                    )
                    InlineActionButton(
                        colors = colors,
                        label = stringResource(R.string.custom_actions_restore).uppercase(),
                        enabled = true,
                        on_click = on_restore_custom_actions
                    )
                }
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
                        running_action_id = running_action_id,
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

        if (pending_restore_count != null) {
            RestoreCustomActionsDialog(
                colors = colors,
                actions_count = pending_restore_count,
                on_confirm = on_confirm_restore_custom_actions,
                on_dismiss = on_dismiss_restore_custom_actions
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
private fun StatusSection(colors: AppColors, state: ShizukuState, on_request_permission: () -> Unit) {
    val is_ready = state.is_running && state.is_permission_granted
    val title = stringResource(
        when {
            !state.is_running -> R.string.status_not_running_title
            !state.is_permission_granted -> R.string.status_permission_required_title
            else -> R.string.status_ready_title
        }
    )
    val detail = stringResource(
        when {
            !state.is_running -> R.string.dispatch_need_shizuku
            !state.is_permission_granted -> R.string.dispatch_need_permission
            else -> R.string.status_ready_detail
        }
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (is_ready) colors.success_surface else colors.surface_alt)
            .border(1.dp, if (is_ready) colors.success_border else colors.border, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "$title. $detail"
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusGlyph(
                background = if (is_ready) colors.success else colors.text_muted,
                text = if (is_ready) "✓" else "!"
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(text = title, style = action_title_text_style(colors))
                BasicText(text = detail, style = body_text_style(colors.text_muted))
            }
        }
        if (state.is_running && !state.is_permission_granted) {
            FilledActionButton(
                colors = colors,
                label = stringResource(R.string.request_permission),
                on_click = on_request_permission
            )
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
            InlineActionButton(
                colors = colors,
                label = action_label,
                enabled = true,
                on_click = on_action
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
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .background(colors.accent)
            .clickable(onClick = on_click)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
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
private fun StatusGlyph(background: Color, text: String) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clearAndSetSemantics { }
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
    running_action_id: String?,
    on_try_action: (AppActionItem) -> Unit,
    on_pin_shortcut: (AppActionItem) -> Unit,
    on_edit_action: ((AppActionItem) -> Unit)? = null,
    on_delete_action: ((String) -> Unit)?
) {
    val has_management_actions = on_edit_action != null || on_delete_action != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(colors.accent_soft),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(action.icon_res),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(colors.accent)
                )
            }

            BasicText(
                text = action.short_label,
                modifier = Modifier.weight(1f),
                style = action_title_text_style(colors),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!has_management_actions) {
                ActionControls(colors, action, is_ready, running_action_id, on_try_action, on_pin_shortcut, null, null)
            }
        }

        if (has_management_actions) {
            ActionControls(
                colors = colors,
                action = action,
                is_ready = is_ready,
                running_action_id = running_action_id,
                on_try_action = on_try_action,
                on_pin_shortcut = on_pin_shortcut,
                on_edit_action = on_edit_action,
                on_delete_action = on_delete_action,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ActionControls(
    colors: AppColors,
    action: AppActionItem,
    is_ready: Boolean,
    running_action_id: String?,
    on_try_action: (AppActionItem) -> Unit,
    on_pin_shortcut: (AppActionItem) -> Unit,
    on_edit_action: ((AppActionItem) -> Unit)?,
    on_delete_action: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)
    ) {
        InlineActionButton(
            colors = colors,
            label = stringResource(if (running_action_id == action.id) R.string.action_running else R.string.try_action).uppercase(),
            enabled = is_ready && running_action_id == null,
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
private fun RestoreCustomActionsDialog(
    colors: AppColors,
    actions_count: Int,
    on_confirm: () -> Unit,
    on_dismiss: () -> Unit
) {
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
                text = stringResource(R.string.custom_actions_restore_confirm_title),
                style = action_title_text_style(colors)
            )

            BasicText(
                text = stringResource(R.string.custom_actions_restore_confirm_message, actions_count),
                style = body_text_style(colors.text_muted)
            )

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
                    label = stringResource(R.string.custom_actions_restore_confirm_action),
                    on_click = on_confirm
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
                        .heightIn(min = 48.dp)
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
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = on_click)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
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
            .size(48.dp)
            .clip(CircleShape)
            .background(colors.surface_alt)
            .clickable(onClick = on_click),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(icon_res),
            contentDescription = content_description,
            modifier = Modifier.size(18.dp),
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
