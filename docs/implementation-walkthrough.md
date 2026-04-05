# Implementation Walkthrough

This document walks through the app from build setup to the final privileged shell command, including widgets and custom-action backup/restore.

## 1. Project setup

The project is a single Android app module using Kotlin DSL, Compose, SDK 36, and a minified release build.
Debug builds use an application id suffix (`.debug`) for clean separation from release and for screenshot/instrumentation runs.

File: [app/build.gradle.kts](../app/build.gradle.kts)

```kotlin
android {
    compileSdk = 36

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "com.yshalsager.shizukushortcuts"
        minSdk = 26
        targetSdk = 36
    }

    buildFeatures {
        aidl = true
        compose = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }

    androidResources {
        localeFilters += listOf("en", "ar")
    }
}
```

`mise` is pinned at the repo root.

File: [mise.toml](../mise.toml)

```toml
[tools]
java = 'openjdk-25.0.2'
```

## 2. Manifest entrypoints

The app has six important manifest entries:

- `ShizukuProvider` receives the Shizuku binder
- `MainActivity` is the launcher/setup UI
- `ShortcutDispatchActivity` is the transparent shortcut trampoline
- `ActionWidgetConfigureActivity` is launched by widget placement/rebinding
- `ActionWidgetProvider` is the app widget receiver
- `localeConfig` exposes app languages to Android system settings

File: [AndroidManifest.xml](../app/src/main/AndroidManifest.xml)

```xml
<application
    android:localeConfig="@xml/locales_config"
    android:supportsRtl="true">

<provider
    android:name="rikka.shizuku.ShizukuProvider"
    android:authorities="${applicationId}.shizuku"
    android:exported="true"
    android:multiprocess="false"
    android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />

<activity
    android:name=".ShortcutDispatchActivity"
    android:exported="true"
    android:theme="@style/Theme.ShizukuShortcuts.Transparent" />

<activity
    android:name=".ActionWidgetConfigureActivity"
    android:exported="true">

    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
    </intent-filter>
</activity>

<receiver
    android:name=".ActionWidgetProvider"
    android:exported="false">

    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>

    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/action_widget_info" />
</receiver>

<activity
    android:name=".MainActivity"
    android:exported="true">

    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>

    <meta-data
        android:name="android.app.shortcuts"
        android:resource="@xml/shortcuts" />
</activity>
```

The locale config itself is small and platform-owned.

File: [locales_config.xml](../app/src/main/res/xml/locales_config.xml)

```xml
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="ar" />
</locale-config>
```

## 3. Built-in actions and custom actions

The built-in actions still live in a single hardcoded registry:

- stable action id
- launcher intent action
- icon and labels
- primary shell command
- optional fallback commands

File: [ShortcutAction.kt](../app/src/main/java/com/yshalsager/shizukushortcuts/ShortcutAction.kt)

```kotlin
val expand_notifications = ShortcutAction(
    id = "expand_notifications",
    shortcut_intent_action = "com.yshalsager.shizukushortcuts.action.EXPAND_NOTIFICATIONS",
    short_label_res = R.string.open_notifications,
    long_label_res = R.string.open_notifications_long,
    icon_res = R.drawable.ic_shortcut_notifications,
    primary_command = listOf("cmd", "statusbar", "expand-notifications"),
    fallback_commands = listOf(listOf("service", "call", "statusbar", "1"))
)

val expand_quick_settings = ShortcutAction(
    id = "expand_quick_settings",
    shortcut_intent_action = "com.yshalsager.shizukushortcuts.action.EXPAND_QUICK_SETTINGS",
    short_label_res = R.string.open_quick_settings,
    long_label_res = R.string.open_quick_settings_long,
    icon_res = R.drawable.ic_shortcut_quick_settings,
    primary_command = listOf("cmd", "statusbar", "expand-settings")
)

val take_screenshot = ShortcutAction(
    id = "take_screenshot",
    shortcut_intent_action = "com.yshalsager.shizukushortcuts.action.TAKE_SCREENSHOT",
    short_label_res = R.string.take_screenshot,
    long_label_res = R.string.take_screenshot_long,
    icon_res = R.drawable.ic_shortcut_screenshot,
    primary_command = listOf("input", "keyevent", "120")
)

val screen_off = ShortcutAction(
    id = "screen_off",
    shortcut_intent_action = "com.yshalsager.shizukushortcuts.action.SCREEN_OFF",
    short_label_res = R.string.screen_off,
    long_label_res = R.string.screen_off_long,
    icon_res = R.drawable.ic_shortcut_screen_off,
    primary_command = listOf("input", "keyevent", "26")
)
```

Custom actions are stored separately as local data:

- `id`
- `label`
- `shell_command`

File: [CustomAction.kt](../app/src/main/java/com/yshalsager/shizukushortcuts/CustomAction.kt)

```kotlin
data class CustomAction(
    val id: String,
    val label: String,
    val shell_command: String
)
```

They are persisted in one SharedPreferences JSON string and refreshed into launcher dynamic shortcuts and widgets whenever the list changes:

```kotlin
private fun save_actions(actions: List<CustomAction>, deleted_action_id: String? = null) {
    shared_preferences.edit().putString(actions_key, serialize_custom_actions(actions)).apply()
    state_flow.value = actions
    schedule_shortcut_sync(actions, deleted_action_id)
}
```

Restore support is replace-all and reuses the same persistence path via `replace_all_actions(...)` so shortcut and widget refresh behavior stays identical.

File: [CustomActionsBackup.kt](../app/src/main/java/com/yshalsager/shizukushortcuts/CustomActionsBackup.kt)

Backup/restore helpers are separated from `MainActivity`:

- versioned JSON payload (`version = 1`)
- strict parse validation (version, structure, non-empty fields, duplicate IDs)
- SAF read/write helpers
- timestamped default names, e.g. `shizuku-custom-actions-backup-20260404-153045.json`

The merged lookup layer is `ActionCatalog`, which turns built-ins and customs into one UI and dispatch model.

File: [ActionCatalog.kt](../app/src/main/java/com/yshalsager/shizukushortcuts/ActionCatalog.kt)

```kotlin
fun find_by_id(context: Context, action_id: String?): AppActionItem? {
    if (action_id == null) return null
    return built_in_actions(context).firstOrNull { it.id == action_id }
        ?: custom_actions(context).firstOrNull { it.id == action_id }
}
```

The same catalog builds the dispatch intent used by dynamic and pinned shortcuts.

## 4. Static, dynamic, and pinned shortcuts

The built-in launcher long-press shortcuts are declared in XML (now four entries: notifications, quick settings, screenshot, and screen off).

File: [shortcuts.xml](../app/src/main/res/xml/shortcuts.xml)

```xml
<shortcut
    android:shortcutId="expand_notifications"
    android:shortcutShortLabel="@string/open_notifications"
    android:shortcutLongLabel="@string/open_notifications_long"
    android:icon="@drawable/ic_shortcut_notifications">

    <intent
        android:action="com.yshalsager.shizukushortcuts.action.EXPAND_NOTIFICATIONS"
        android:targetClass="com.yshalsager.shizukushortcuts.ShortcutDispatchActivity"
        android:targetPackage="com.yshalsager.shizukushortcuts" />
</shortcut>
```

Custom actions cannot use XML static shortcuts, so they are published as dynamic shortcuts at runtime.

```kotlin
val sync_plan = sync_plan(custom_actions, shortcut_manager.maxShortcutCountPerActivity)
val shortcuts = sync_plan.all_custom_actions.map { build_custom_shortcut(context, it) }

shortcut_manager.updateShortcuts(shortcuts)
shortcut_manager.dynamicShortcuts = shortcuts.take(sync_plan.dynamic_shortcut_count)
```

Pinned home-screen shortcuts for both built-ins and customs go through the same `ActionCatalog.build_pinned_shortcut()` path.

Widgets are also action-based and use the same dispatch contract:

- one widget instance binds to one `action_id` in `WidgetBindingsRepository`
- widget tap sends an explicit `PendingIntent` to `ShortcutDispatchActivity` with `ShortcutActions.extra_action_id`
- if a previously bound custom action is deleted, the widget shows a removed state and opens `ActionWidgetConfigureActivity` for rebinding

## 5. MainActivity: condensed home screen

`MainActivity` is a compact Compose screen that shows:

- two side-by-side status chips
- inline guidance only when Shizuku is stopped or permission is missing
- a button to request permission when needed
- a built-in actions section
- a custom actions section with `Add`
- custom-action `Backup` and `Restore` entry actions
- a `Try` text action and a `Pin` icon action for each row
- `Edit` and `Delete` for custom rows
- a restore confirmation dialog before destructive replace-all import

File: [MainActivity.kt](../app/src/main/java/com/yshalsager/shizukushortcuts/MainActivity.kt)

```kotlin
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
        on_delete_custom_action = custom_actions_repository::delete_action,
        on_backup_custom_actions = ::backup_custom_actions,
        on_restore_custom_actions = ::select_restore_backup,
        pending_restore_count = pending_restore_actions?.size,
        on_confirm_restore_custom_actions = ::confirm_restore_custom_actions,
        on_dismiss_restore_custom_actions = { pending_restore_actions = null }
    )
}
```

Backup/restore uses SAF contracts from the activity:

```kotlin
private val create_backup_document = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { ... }
private val open_restore_document = registerForActivityResult(ActivityResultContracts.OpenDocument()) { ... }
```

The screen uses `enableEdgeToEdge()` plus safe drawing insets so content actually respects system bars:

```kotlin
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical)),
    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp)
)
```

The custom section uses one small dialog for both add and edit:

```kotlin
AddCustomActionDialog(
    colors = colors,
    title = stringResource(if (editing_action == null) R.string.add_custom_action_title else R.string.edit_custom_action_title),
    submit_label = stringResource(if (editing_action == null) R.string.add_custom_action else R.string.save_action),
    on_submit = { label, shell_command ->
        val error = validate_custom_action(label, shell_command)
        if (error == null) {
            editing_action?.let { on_update_custom_action(it.id, label, shell_command) }
                ?: on_add_custom_action(label, shell_command)
        }
        error
    }
)
```

Validation rejects empty values and `adb shell ...` input:

```kotlin
fun validate_custom_action(label: String, shell_command: String): Int? {
    val trimmed_label = label.trim()
    val trimmed_command = shell_command.trim()

    return when {
        trimmed_label.isEmpty() -> R.string.custom_action_label_required
        trimmed_command.isEmpty() -> R.string.custom_action_command_required
        trimmed_command.startsWith("adb shell", ignoreCase = true) -> R.string.custom_action_strip_adb
        else -> null
    }
}
```

Each action row now has direct execution and pinning, and custom rows also expose edit/delete:

```kotlin
ActionRow(
    colors = colors,
    action = action,
    is_ready = is_ready,
    on_try_action = on_try_action,
    on_pin_shortcut = on_pin_shortcut
)
```

The `Try` flow runs the action immediately from the app:

```kotlin
private fun try_action(action: AppActionItem) {
    lifecycleScope.launch {
        val result = manager.perform_action(action)
        val message = when (result.status_code) {
            ActionResult.STATUS_SUCCESS -> getString(when (action.id) {
                ShortcutActions.expand_notifications.id -> R.string.try_notifications_success
                ShortcutActions.expand_quick_settings.id -> R.string.try_quick_settings_success
                ShortcutActions.take_screenshot.id -> R.string.try_screenshot_success
                ShortcutActions.screen_off.id -> R.string.try_screen_off_success
                else -> R.string.try_custom_action_success
            })
            ActionResult.STATUS_SHIZUKU_UNAVAILABLE -> getString(R.string.dispatch_need_shizuku)
            ActionResult.STATUS_PERMISSION_DENIED -> getString(R.string.dispatch_need_permission)
            else -> result.message.ifBlank { getString(R.string.dispatch_failed) }
        }
        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        manager.refresh_state()
    }
}
```

The pin flow stays intentionally thin:

```kotlin
private fun pin_shortcut(action: AppActionItem) {
    val shortcut = ActionCatalog.build_pinned_shortcut(this, action)
    val was_requested = ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
    Toast.makeText(this, getString(if (was_requested) R.string.pin_success else R.string.pin_failed), Toast.LENGTH_SHORT).show()
}
```

## 6. Dynamic theme and app languages

The app uses Android 12+ dynamic colors when available and falls back to a fixed palette on older versions.

File: [Theme.kt](../app/src/main/java/com/yshalsager/shizukushortcuts/ui/theme/Theme.kt)

```kotlin
@Composable
fun shizuku_shortcuts_colors(): AppColors {
    val context = LocalContext.current
    val is_dark = isSystemInDarkTheme()

    return remember(context, is_dark) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamic_colors(context, is_dark)
        else if (is_dark) dark_colors
        else light_colors
    }
}
```

The dynamic palette is mapped from Android's system accent and neutral colors, with light and dark roles following the platform tonal scale.

Arabic strings live in [values-ar/strings.xml](../app/src/main/res/values-ar/strings.xml), and language switching is handled by Android settings rather than by in-app UI.

## 7. Shizuku state and permission

`AppShizukuManager` wraps:

- Shizuku binder availability
- permission requests
- binding to the remote user service

File: [ShizukuManager.kt](../app/src/main/java/com/yshalsager/shizukushortcuts/ShizukuManager.kt)

It listens for binder and permission changes:

```kotlin
init {
    Shizuku.addBinderReceivedListenerSticky { refresh_state() }
    Shizuku.addBinderDeadListener { refresh_state() }
    Shizuku.addRequestPermissionResultListener { request_code, grant_result ->
        if (request_code == permission_request_code) {
            state_flow.value = current_state(grant_result == PackageManager.PERMISSION_GRANTED)
        }
    }
    refresh_state()
}
```

Permission request logic:

```kotlin
override fun request_permission() {
    if (!Shizuku.pingBinder()) {
        refresh_state()
        return
    }
    if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
        refresh_state()
        return
    }
    Shizuku.requestPermission(permission_request_code)
}
```

## 8. Shortcut dispatch

When the user taps a launcher shortcut or widget action, `ShortcutDispatchActivity` starts first.
It stays a lightweight trampoline and finishes normally so it does not tear down an existing app task.

File: [ShortcutDispatchActivity.kt](../app/src/main/java/com/yshalsager/shizukushortcuts/ShortcutDispatchActivity.kt)

It resolves the action and runs it through the manager:

```kotlin
val action = ActionCatalog.find_by_intent(this, intent)
if (action == null) {
    Toast.makeText(this, getString(R.string.dispatch_missing_action), Toast.LENGTH_SHORT).show()
    finish()
    return
}

lifecycleScope.launch {
    handle_result(manager.perform_action(action))
}
```

If Shizuku is missing or permission is denied, the activity redirects back to setup:

```kotlin
private fun open_setup(message: String) {
    startActivity(
        Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.extra_message, message)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    )
    finish()
}
```

## 9. Binding the Shizuku user service

The privileged work does not run in the app process. The app binds a Shizuku `UserService`.

File: [ShizukuManager.kt](../app/src/main/java/com/yshalsager/shizukushortcuts/ShizukuManager.kt)

```kotlin
private val user_service_args = Shizuku.UserServiceArgs(
    ComponentName(app_context.packageName, PrivilegedStatusBarService::class.java.name)
)
    .daemon(false)
    .tag("statusbar_shortcuts")
    .version(2)
    .processNameSuffix("statusbar_shortcuts")
```

The bind call:

```kotlin
runCatching { Shizuku.bindUserService(user_service_args, connection) }
    .onFailure { exception ->
        finish(
            ActionResult.execution_failed(
                action_id = action.id,
                executed_command = action.shell_command ?: action.id,
                message = exception.message ?: "Could not bind user service"
            )
        )
    }
```

When connected, the app talks to the remote binder:

```kotlin
override fun onServiceConnected(name: ComponentName, service: IBinder) {
    worker_scope.launch {
        val remote = IPrivilegedStatusBarService.Stub.asInterface(service)
        val result = runCatching {
            action.shell_command?.let { remote.perform_custom_action(action.id, it) }
                ?: remote.perform_action(action.id)
        }
            .getOrElse { exception ->
                ActionResult.execution_failed(
                    action_id = action.id,
                    executed_command = action.shell_command ?: action.id,
                    message = exception.message ?: "Remote execution failed"
                )
            }
        finish(result)
    }
}
```

## 10. Binder contract

The binder interface stays intentionally small: one method for built-ins and one method for custom shell commands.

Files:

- [IPrivilegedStatusBarService.aidl](../app/src/main/aidl/com/yshalsager/shizukushortcuts/IPrivilegedStatusBarService.aidl)
- [ActionResult.kt](../app/src/main/java/com/yshalsager/shizukushortcuts/ActionResult.kt)

```aidl
interface IPrivilegedStatusBarService {
    ActionResult perform_action(String action_id);
    ActionResult perform_custom_action(String action_id, String shell_command);
}
```

```kotlin
data class ActionResult(
    val status_code: Int,
    val action_id: String,
    val executed_command: String = "",
    val message: String = "",
    val used_fallback: Boolean = false
) : Parcelable
```

## 11. Privileged service implementation

The Shizuku service itself is tiny. The important detail is that this is not a normal Android `Service`. It must be the binder object itself for Shizuku `bindUserService()`.

File: [PrivilegedStatusBarService.kt](../app/src/main/java/com/yshalsager/shizukushortcuts/PrivilegedStatusBarService.kt)

```kotlin
class PrivilegedStatusBarService : IPrivilegedStatusBarService.Stub() {
    override fun perform_action(action_id: String) = ActionPerformer.perform_action(action_id)
    override fun perform_custom_action(action_id: String, shell_command: String) =
        ActionPerformer.perform_custom_action(action_id, shell_command)
}
```

This shape matters because an earlier `Service()` implementation failed at runtime with a `ClassCastException` when Shizuku tried to treat it as an `IBinder`.

## 12. Running the shell commands

`ActionPerformer` is where the actual status bar command is chosen and executed.

File: [ActionPerformer.kt](../app/src/main/java/com/yshalsager/shizukushortcuts/ActionPerformer.kt)

It loops over the primary command and any fallbacks until one succeeds:

```kotlin
fun perform_action(action_id: String, command_runner: CommandRunner = ProcessCommandRunner): ActionResult {
    val action = ShortcutActions.find_by_id(action_id) ?: return ActionResult.unknown_action(action_id)
    var last_error = ""

    action.all_commands.forEachIndexed { index, command ->
        val run = runCatching { command_runner.run_command(command) }
            .getOrElse { exception ->
                return ActionResult.execution_failed(
                    action_id = action.id,
                    executed_command = command.joinToString(" "),
                    message = exception.message ?: "Command failed",
                    used_fallback = index > 0
                )
            }

        if (run.exit_code == 0) {
            return ActionResult.success(
                action_id = action.id,
                executed_command = command.joinToString(" "),
                used_fallback = index > 0,
                message = run.output
            )
        }

        last_error = run.output.ifBlank { "Exit code ${run.exit_code}" }
    }

    return ActionResult.execution_failed(
        action_id = action.id,
        executed_command = action.primary_command.joinToString(" "),
        message = last_error.ifBlank { "Command failed" },
        used_fallback = action.fallback_commands.isNotEmpty()
    )
}
```

The actual execution uses `ProcessBuilder`:

```kotlin
val process = ProcessBuilder(command)
    .redirectErrorStream(true)
    .start()
val output = process.inputStream.bufferedReader().use { it.readText().trim() }
val exit_code = process.waitFor()
```

Custom actions use one direct shell command path:

```kotlin
fun perform_custom_action(action_id: String, shell_command: String, command_runner: CommandRunner = ProcessCommandRunner): ActionResult {
    val command = listOf("sh", "-c", shell_command)
    ...
}
```

The custom command output is capped before it is copied into `ActionResult.message` so large stdout or stderr does not break the Binder call back to the app.

In practice this means:

- notifications tries `cmd statusbar expand-notifications`
- notifications falls back to `service call statusbar 1`
- quick settings tries `cmd statusbar expand-settings`
- screenshot triggers `input keyevent 120`
- screen off triggers `input keyevent 26`
- custom actions run exactly what the user entered through `sh -c`
- custom action results keep only a bounded slice of command output for Binder safety

## 13. Tests

There are five test surfaces:

- built-in fallback behavior plus custom-command execution in [ActionPerformerTest.kt](../app/src/test/java/com/yshalsager/shizukushortcuts/ActionPerformerTest.kt) and [CustomActionTest.kt](../app/src/test/java/com/yshalsager/shizukushortcuts/CustomActionTest.kt)
- Shizuku service contract in [PrivilegedStatusBarServiceTest.kt](../app/src/test/java/com/yshalsager/shizukushortcuts/PrivilegedStatusBarServiceTest.kt)
- XML/registry consistency in [ShortcutXmlSyncTest.kt](../app/src/test/java/com/yshalsager/shizukushortcuts/ShortcutXmlSyncTest.kt)
- widget-binding serialization and CRUD in [WidgetBindingsRepositoryTest.kt](../app/src/test/java/com/yshalsager/shizukushortcuts/WidgetBindingsRepositoryTest.kt)
- instrumentation-side dispatch, catalog, and custom-action UI coverage in [ShortcutDispatchActivityTest.kt](../app/src/androidTest/java/com/yshalsager/shizukushortcuts/ShortcutDispatchActivityTest.kt), [ActionCatalogTest.kt](../app/src/androidTest/java/com/yshalsager/shizukushortcuts/ActionCatalogTest.kt), and [CustomActionsUiTest.kt](../app/src/androidTest/java/com/yshalsager/shizukushortcuts/CustomActionsUiTest.kt)

`CustomActionTest.kt` also covers backup payload parsing/validation (malformed JSON, unsupported version, duplicate IDs) and timestamped backup file naming.

Example fallback test:

```kotlin
val result = ActionPerformer.perform_action(ShortcutActions.expand_notifications.id) { command ->
    attempted_commands += command
    if (attempted_commands.size == 1) {
        CommandRun(exit_code = 1, output = "cmd failed")
    } else {
        CommandRun(exit_code = 0, output = "")
    }
}

assertTrue(result.is_success)
assertTrue(result.used_fallback)
```

The binder-shape regression test is intentionally simple:

```kotlin
assertTrue(IPrivilegedStatusBarService.Stub::class.java.isAssignableFrom(PrivilegedStatusBarService::class.java))
```

## 14. End-to-end summary

The runtime path is:

1. User opens the home screen or taps a static, dynamic, or pinned launcher shortcut, or taps a configured widget
2. Home screen `Try` buttons call `AppShizukuManager.perform_action()` directly
3. Launcher shortcuts go through `ShortcutDispatchActivity`
4. `ActionCatalog` resolves the requested built-in or custom action
5. `AppShizukuManager` checks whether Shizuku is running and permission is granted
6. The app binds `PrivilegedStatusBarService` through Shizuku
7. The app calls either `perform_action(action_id)` or `perform_custom_action(action_id, shell_command)` over Binder
8. `ActionPerformer` runs the built-in argv command or the custom `sh -c` command in the privileged user service process
9. The result is returned to the app
10. The UI either shows a small toast, finishes silently, or routes the user back to setup
