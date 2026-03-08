# Implementation Walkthrough

This document walks through the app from build setup to the final privileged shell command.

## 1. Project setup

The project is a single Android app module using Kotlin DSL, Compose, SDK 36, and a minified release build.

File: [app/build.gradle.kts](/Users/yshalsager/tmp/research/shizuku-shortcuts/app/build.gradle.kts)

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

File: [mise.toml](/Users/yshalsager/tmp/research/shizuku-shortcuts/mise.toml)

```toml
[tools]
java = 'openjdk-25.0.2'
```

## 2. Manifest entrypoints

The app has four important manifest entries:

- `ShizukuProvider` receives the Shizuku binder
- `MainActivity` is the launcher/setup UI
- `ShortcutDispatchActivity` is the transparent shortcut trampoline
- `localeConfig` exposes app languages to Android system settings

File: [AndroidManifest.xml](/Users/yshalsager/tmp/research/shizuku-shortcuts/app/src/main/AndroidManifest.xml)

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

File: [locales_config.xml](/Users/yshalsager/tmp/research/shizuku-shortcuts/app/src/main/res/xml/locales_config.xml)

```xml
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="ar" />
</locale-config>
```

## 3. One action registry for everything

Both supported actions live in a single registry:

- stable action id
- launcher intent action
- icon and labels
- primary shell command
- optional fallback commands

File: [ShortcutAction.kt](/Users/yshalsager/tmp/research/shizuku-shortcuts/app/src/main/java/com/yshalsager/shizukushortcuts/ShortcutAction.kt)

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
```

The same file also builds the intent used by both static and pinned shortcuts.

```kotlin
fun build_dispatch_intent(context: Context, action: ShortcutAction) =
    Intent(context, ShortcutDispatchActivity::class.java)
        .setAction(action.shortcut_intent_action)
        .putExtra(extra_action_id, action.id)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
```

## 4. Static launcher shortcuts

The launcher long-press shortcuts are declared in XML.

File: [shortcuts.xml](/Users/yshalsager/tmp/research/shizuku-shortcuts/app/src/main/res/xml/shortcuts.xml)

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

Pinned home-screen shortcuts use the same registry, so there is one source of truth.

## 5. MainActivity: condensed home screen

`MainActivity` is a compact Compose screen that shows:

- two side-by-side status chips
- inline guidance only when Shizuku is stopped or permission is missing
- a button to request permission when needed
- two compact action rows
- a `Try` text action and a `Pin` icon action for each row

File: [MainActivity.kt](/Users/yshalsager/tmp/research/shizuku-shortcuts/app/src/main/java/com/yshalsager/shizukushortcuts/MainActivity.kt)

```kotlin
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

Each action row now has both direct execution and pinning:

```kotlin
ActionRow(
    colors = colors,
    action = ShortcutActions.expand_notifications,
    on_try_action = on_try_action,
    on_pin_shortcut = on_pin_shortcut
)
```

The `Try` flow runs the action immediately from the app:

```kotlin
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
```

The pin flow stays intentionally thin:

```kotlin
private fun pin_shortcut(action: ShortcutAction) {
    val shortcut = ShortcutActions.build_pinned_shortcut(this, action)
    val was_requested = ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
    Toast.makeText(this, getString(
        if (was_requested) R.string.pin_success else R.string.pin_failed
    ), Toast.LENGTH_SHORT).show()
}
```

## 6. Dynamic theme and app languages

The app uses Android 12+ dynamic colors when available and falls back to a fixed palette on older versions.

File: [Theme.kt](/Users/yshalsager/tmp/research/shizuku-shortcuts/app/src/main/java/com/yshalsager/shizukushortcuts/ui/theme/Theme.kt)

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

Arabic strings live in [values-ar/strings.xml](/Users/yshalsager/tmp/research/shizuku-shortcuts/app/src/main/res/values-ar/strings.xml), and language switching is handled by Android settings rather than by in-app UI.

## 7. Shizuku state and permission

`AppShizukuManager` wraps:

- Shizuku binder availability
- permission requests
- binding to the remote user service

File: [ShizukuManager.kt](/Users/yshalsager/tmp/research/shizuku-shortcuts/app/src/main/java/com/yshalsager/shizukushortcuts/ShizukuManager.kt)

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

When the user taps a launcher shortcut, `ShortcutDispatchActivity` starts first.

File: [ShortcutDispatchActivity.kt](/Users/yshalsager/tmp/research/shizuku-shortcuts/app/src/main/java/com/yshalsager/shizukushortcuts/ShortcutDispatchActivity.kt)

It resolves the action and runs it through the manager:

```kotlin
val action = ShortcutActions.find_by_intent(intent)
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

File: [ShizukuManager.kt](/Users/yshalsager/tmp/research/shizuku-shortcuts/app/src/main/java/com/yshalsager/shizukushortcuts/ShizukuManager.kt)

```kotlin
private val user_service_args = Shizuku.UserServiceArgs(
    ComponentName(app_context.packageName, PrivilegedStatusBarService::class.java.name)
)
    .daemon(false)
    .tag("statusbar_shortcuts")
    .version(1)
    .processNameSuffix("statusbar_shortcuts")
```

The bind call:

```kotlin
runCatching { Shizuku.bindUserService(user_service_args, connection) }
    .onFailure { exception ->
        finish(
            ActionResult.execution_failed(
                action_id = action.id,
                executed_command = action.primary_command.joinToString(" "),
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
        val result = runCatching { remote.perform_action(action.id) }
            .getOrElse { exception ->
                ActionResult.execution_failed(
                    action_id = action.id,
                    executed_command = action.primary_command.joinToString(" "),
                    message = exception.message ?: "Remote execution failed"
                )
            }
        finish(result)
    }
}
```

## 10. Binder contract

The binder interface is intentionally small: one method that accepts an action id and returns an `ActionResult`.

Files:

- [IPrivilegedStatusBarService.aidl](/Users/yshalsager/tmp/research/shizuku-shortcuts/app/src/main/aidl/com/yshalsager/shizukushortcuts/IPrivilegedStatusBarService.aidl)
- [ActionResult.kt](/Users/yshalsager/tmp/research/shizuku-shortcuts/app/src/main/java/com/yshalsager/shizukushortcuts/ActionResult.kt)

```aidl
interface IPrivilegedStatusBarService {
    ActionResult perform_action(String action_id);
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

File: [PrivilegedStatusBarService.kt](/Users/yshalsager/tmp/research/shizuku-shortcuts/app/src/main/java/com/yshalsager/shizukushortcuts/PrivilegedStatusBarService.kt)

```kotlin
class PrivilegedStatusBarService : IPrivilegedStatusBarService.Stub() {
    override fun perform_action(action_id: String) = ActionPerformer.perform_action(action_id)
}
```

This shape matters because an earlier `Service()` implementation failed at runtime with a `ClassCastException` when Shizuku tried to treat it as an `IBinder`.

## 12. Running the shell commands

`ActionPerformer` is where the actual status bar command is chosen and executed.

File: [ActionPerformer.kt](/Users/yshalsager/tmp/research/shizuku-shortcuts/app/src/main/java/com/yshalsager/shizukushortcuts/ActionPerformer.kt)

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

In practice this means:

- notifications tries `cmd statusbar expand-notifications`
- notifications falls back to `service call statusbar 1`
- quick settings tries `cmd statusbar expand-settings`

## 13. Tests

There are three test surfaces:

- command and fallback behavior in [ActionPerformerTest.kt](/Users/yshalsager/tmp/research/shizuku-shortcuts/app/src/test/java/com/yshalsager/shizukushortcuts/ActionPerformerTest.kt)
- Shizuku service contract in [PrivilegedStatusBarServiceTest.kt](/Users/yshalsager/tmp/research/shizuku-shortcuts/app/src/test/java/com/yshalsager/shizukushortcuts/PrivilegedStatusBarServiceTest.kt)
- XML/registry consistency in [ShortcutXmlSyncTest.kt](/Users/yshalsager/tmp/research/shizuku-shortcuts/app/src/test/java/com/yshalsager/shizukushortcuts/ShortcutXmlSyncTest.kt)
- instrumentation-side dispatch flow in [ShortcutDispatchActivityTest.kt](/Users/yshalsager/tmp/research/shizuku-shortcuts/app/src/androidTest/java/com/yshalsager/shizukushortcuts/ShortcutDispatchActivityTest.kt)

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

1. User opens the home screen or taps a static or pinned launcher shortcut
2. Home screen `Try` buttons call `AppShizukuManager.perform_action()` directly
3. Launcher shortcuts go through `ShortcutDispatchActivity`
4. `AppShizukuManager` checks whether Shizuku is running and permission is granted
5. The app binds `PrivilegedStatusBarService` through Shizuku
6. The app calls `perform_action(action_id)` over Binder
7. `ActionPerformer` runs the shell command in the privileged user service process
8. The result is returned to the app
9. The UI either shows a small toast, finishes silently, or routes the user back to setup
