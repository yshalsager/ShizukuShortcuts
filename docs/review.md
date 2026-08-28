# Shizuku Shortcuts 0.4.0 — code and UX review

## Overall verdict

The app is small, focused, and reasonably easy to understand. The action model is clear, dependencies are limited, Arabic/RTL is supported, and there are useful unit and instrumentation tests.

The main weakness is the boundary between **launcher shortcut → Android activity → Shizuku binder → command execution**. That path currently assumes everything is immediately available and turns several transient failures into visible UI. It also lacks execution timeouts and exposes privileged actions through an unprotected exported component.

I would treat the shortcut-dispatch behavior and command timeouts as release blockers.

## Implementation status

The original findings are retained below for context. Completed work:

- [x] Shortcut failures close silently after a bounded toast instead of opening `MainActivity` (`3c65289`).
- [x] Cold-start dispatch waits for Shizuku Binder readiness within a bounded window (`3c65289`).
- [x] The trampoline uses empty task affinity, no `MULTIPLE_TASK`, a dedicated transparent theme, and `finishAndRemoveTask()` (`3c65289`).
- [x] Command execution has a five-second timeout, forced cleanup, bounded output, and a distinct timeout result (`8f745fe`).
- [x] User-service binding and remote calls are bounded and race-safe; Binder failures have fallback diagnostics (`c3e7155`).
- [x] Recoverable command-launch failures continue to fallbacks and report the actual attempted command (`c3e7155`).
- [x] Exported dispatch keeps notifications and Quick Settings public while per-install tokens protect screenshot, screen-off, and custom actions (`e3fe6b7`).
- [x] Static shortcuts were reduced to two so devices with a quota of four retain two dynamic custom-action slots (`e3fe6b7`).
- [x] Package-upgrade migration republishes legacy sensitive shortcuts with local tokens (`e3fe6b7`).
- [x] Custom shortcut deletion, replace-all restore, re-enabling, stale dynamic cleanup, and rate-limit outcomes use one reconciliation path (`704ceff`).
- [x] Malformed or wrong-typed custom-action and widget preferences are quarantined instead of crashing startup (`3300259`).
- [x] Backup/restore file I/O runs off the main thread with bounded input, strict validation, durable persistence, and truthful reconciliation outcomes (`3300259`).
- [x] Screen off uses `KEYCODE_SLEEP` (`223`) instead of toggle-prone `KEYCODE_POWER` (`26`) (`3300259`).

Outstanding work:

- [x] Move shortcut and widget execution to a lifecycle-independent internal service; widgets retain the activity trampoline required by background-service limits.
- [x] Consolidate Shizuku and permission presentation into one mutually exclusive readiness status.
- [x] Show the active action, disable `Try` while it runs, and reject concurrent dispatch instead of queueing duplicates.
- [x] Interactive controls use 48 dp targets; unavailable Shizuku state disables only `Try`, not shortcut management.
- [x] Show plural-aware current/imported restore counts and explain pinned-shortcut effects.
- [ ] Add delete undo, safer shell-command editing, and higher-contrast widgets.
- [ ] Split the oversized activity and prefer standard Material components where they reduce code and accessibility debt.
- [ ] Run lint and regular instrumentation tests in CI, align Java versions, and pin release tooling.
- [ ] Complete physical-device/OEM and true two-version upgrade testing.

---

# Kvaesitso gesture bug

## High-confidence root cause

The app opening instead of expanding notifications is not merely Kvaesitso falling back to the launcher icon. The app explicitly opens its main screen when shortcut execution reports that Shizuku is unavailable or permission is missing:

```
ShortcutDispatchActivity.kt:28-37
    STATUS_SHIZUKU_UNAVAILABLE -> open_setup(...)
    STATUS_PERMISSION_DENIED   -> open_setup(...)

ShortcutDispatchActivity.kt:40-46
    startActivity(Intent(this, MainActivity::class.java) ...)
```

The intermittent sequence is likely:

1. Kvaesitso invokes the notification shortcut.
2. Android cold-starts the app and creates `ShortcutDispatchActivity`.
3. `AppShizukuManager.perform_action()` immediately calls `Shizuku.pingBinder()`.
4. The Shizuku provider/binder callback has not yet completed, so `pingBinder()` temporarily returns `false`.
5. The result becomes `STATUS_SHIZUKU_UNAVAILABLE`.
6. `ShortcutDispatchActivity` deliberately launches `MainActivity`.

The relevant immediate check is:

```
ShizukuManager.kt:78-82
```

Shizuku’s own developer guidance says to acquire and track the Binder first and only call Shizuku methods while that Binder is alive. [![](https://www.google.com/s2/favicons?domain=https://github.com&sz=128)GitHub](https://github.com/RikkaApps/Shizuku-API)

This explains why it is intermittent:

- **Hot process with Binder already delivered:** notification shade opens.
- **Cold process or Shizuku reconnect:** app’s setup screen opens.

The current instrumentation tests actually encode this unwanted behavior as correct:

```
ShortcutDispatchActivityTest.kt:40-68
    permission_denied_routes_to_setup()
    shizuku_unavailable_routes_to_setup()
```

Those tests should be replaced with assertions that shortcut failures close silently after a brief error message and never foreground `MainActivity`.

## A second visible-launch mechanism: splash screen

Even when the action succeeds, the shortcut target is still an Android `Activity`. On Android 12 and later, the system splash screen covers an activity during cold and warm starts. [![](https://www.google.com/s2/favicons?domain=https://developer.android.com&sz=128)Android Developers](https://developer.android.com/develop/ui/views/launch/splash-screen)

The transparent dispatch theme inherits from the main app theme:

```
values/themes.xml:10-15
```

The API 31 main theme explicitly defines the app’s launcher icon as its splash icon:

```
values-v31/themes.xml:2-10
```

Therefore a successful shortcut can still briefly show the app icon or splash, while a failed cold-start check shows the complete main screen.

## Task configuration makes it worse

Dynamic shortcuts and widgets add:

```
Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
```

in:

```
ActionCatalog.kt:63-67
ActionWidgetRenderer.kt:43-51
```

`FLAG_ACTIVITY_MULTIPLE_TASK` skips reuse and unconditionally creates a new task. Android explicitly says not to combine it with `NEW_TASK` unless implementing a top-level application launcher. [![](https://www.google.com/s2/favicons?domain=https://developer.android.com&sz=128)Android Developers](https://developer.android.com/reference/android/content/Intent)

The static shortcut trampoline also lacks:

```
android:taskAffinity=""
```

Android’s shortcut documentation states that static shortcut intents receive `NEW_TASK | CLEAR_TASK` and specifically recommends an empty task affinity for trampoline activities. [![](https://www.google.com/s2/favicons?domain=https://developer.android.com&sz=128)Android Developers](https://developer.android.com/develop/ui/compose/system/shortcuts/managing-shortcuts)

## Immediate Kvaesitso workaround

Kvaesitso 1.40.2 is currently the latest release. Starting with 1.40.0, Kvaesitso supports notification and Quick Settings gestures directly without requiring its accessibility service. Configure the swipe gesture to Kvaesitso’s native **Notifications** action instead of invoking this app’s shortcut. [![](https://www.google.com/s2/favicons?domain=https://github.com&sz=128)GitHub+1](https://github.com/MM2-0/Kvaesitso/releases)

That bypasses the Android shortcut activity entirely and should be more reliable and visually seamless.

---

# Recommended fix for the gesture path

## 1\. Never open the setup screen automatically from a shortcut

A gesture is expected to be immediate and non-navigational. Failure should produce a short toast and finish.

Conceptually:

```
private fun handle_result(result: ActionResult) {
    when (result.status_code) {
        ActionResult.STATUS_SUCCESS -> close_dispatch_task()

        ActionResult.STATUS_SHIZUKU_UNAVAILABLE -> {
            show_error(R.string.dispatch_need_shizuku)
            close_dispatch_task()
        }

        ActionResult.STATUS_PERMISSION_DENIED -> {
            show_error(R.string.dispatch_need_permission)
            close_dispatch_task()
        }

        else -> {
            Toast.makeText(
                this,
                result.message.ifBlank { getString(R.string.dispatch_failed) },
                Toast.LENGTH_SHORT
            ).show()
            close_dispatch_task()
        }
    }
}
```

Opening the setup UI should happen only from an explicit user action such as tapping the normal app icon.

## 2\. Give the Shizuku Binder a bounded readiness window

A cold start should wait briefly for the sticky Binder callback rather than treating the initial default state as final.

For example:

```
private suspend fun await_binder(timeout_ms: Long = 1_250): Boolean {
    if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
        return true
    }

    return withTimeoutOrNull(timeout_ms) {
        state.filter { it.is_running }.first()
        true
    } ?: false
}
```

Then use `await_binder()` before checking permission or binding the user service. All subsequent Shizuku calls should still be wrapped because the Binder can die between two calls.

A delay such as `delay(500)` would be simpler but less correct. Waiting on the Binder state is deterministic and usually returns immediately.

## 3\. Correct the trampoline task configuration

Change the manifest entry to include an empty affinity:

```
<activity
    android:name=".ShortcutDispatchActivity"
    android:exported="true"
    android:excludeFromRecents="true"
    android:noHistory="true"
    android:taskAffinity=""
    android:theme="@style/Theme.ShizukuShortcuts.Dispatch" />
```

Remove `MULTIPLE_TASK` from both shortcut and widget intents:

```
.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
```

The exact need for `NEW_TASK` depends on the caller, but `MULTIPLE_TASK` has no valid role here.

After adding an empty affinity, close the isolated trampoline task with:

```
finishAndRemoveTask()
```

## 4\. Use a dedicated dispatch theme

Do not inherit the main app’s API 31 splash configuration:

```
<style
    name="Theme.ShizukuShortcuts.Dispatch"
    parent="@android:style/Theme.Material.Light.NoActionBar">

    <item name="android:windowIsTranslucent">true</item>
    <item name="android:windowBackground">@android:color/transparent</item>
    <item name="android:windowNoTitle">true</item>
    <item name="android:windowDisablePreview">true</item>
    <item name="android:windowAnimationStyle">@null</item>
    <item name="android:colorBackgroundCacheHint">@null</item>
</style>
```

The existing theme sets:

```
<item name="android:windowAnimationStyle">@android:style/Animation</item>
```

which does not disable animations.

This theme change should reduce flashes, but an activity-based shortcut target can still produce platform-dependent starting-window behavior.

## 5\. Preferred architecture: finish the activity immediately

The most robust architecture is:

```
Launcher
  → zero-UI ShortcutDispatchActivity
      → internal non-exported DispatchService
      → finishAndRemoveTask immediately
          → wait for Shizuku Binder
          → execute command
          → stop service
```

The current activity cannot simply finish before completion because `lifecycleScope` would be cancelled and `perform_action()` would be interrupted. Moving execution to a short-lived internal service separates the command from the trampoline lifecycle and minimizes visible activity time.

For widgets, direct `PendingIntent.getService()` was considered but is not reliable for a cold background UID on Android 8+. The implemented widget path keeps the zero-UI activity trampoline, which immediately hands execution to the internal service.

---

# Correctness and robustness findings

## P0 — Custom commands can hang forever or exhaust memory

`ProcessCommandRunner` reads all output before waiting for process completion:

```
ActionPerformer.kt:12-25
```
```
val output = process.inputStream.bufferedReader().use { it.readText().trim() }
val exit_code = process.waitFor()
```

Problems:

- `sleep 999999` never completes.
- `yes` produces unbounded output and can exhaust process memory.
- Cancellation does not destroy the child process.
- Output is truncated only after the complete output has already been loaded.
- There is no maximum command duration.

Use:

- A 5–10 second default timeout, optionally configurable for custom actions.
- A bounded output collector, for example 64 KiB.
- `process.destroy()` followed by `destroyForcibly()` after a short grace period.
- Guaranteed cleanup in `finally`.
- Interrupt preservation.
- A distinct timeout result.

The size limit must be applied while reading, not afterward.

## P0 — User-service binding has no timeout and has a completion race

`ShizukuManager.kt:84-139` suspends until one of the callbacks completes. If the service never connects or disconnects cleanly, the coroutine can remain suspended indefinitely.

`is_finished` is also a plain mutable Boolean accessed from callback and worker threads:

```
var is_finished = false
```

That is not a valid cross-thread completion guard.

Use:

```
val completed = AtomicBoolean(false)

fun finish(result: ActionResult) {
    if (!completed.compareAndSet(false, true)) return
    ...
}
```

Wrap the complete bind/remote-call operation in `withTimeout`, and always unbind in a single cleanup path.

Also handle Binder death between:

```
Shizuku.pingBinder()
Shizuku.checkSelfPermission()
Shizuku.bindUserService()
```

`current_state()` has the same check-then-call race at `ShizukuManager.kt:142-150`.

## P1 — Command fallback and diagnostics are incorrect

At `ActionPerformer.kt:35-44`, an exception while launching the primary command returns immediately. Fallback commands are only attempted after a normal nonzero exit.

For example, if `cmd` is absent or cannot start but the `service call` fallback is valid, the fallback is never tried.

After all commands fail, the result always reports the primary command:

```
ActionPerformer.kt:58-63
```

even when the fallback was the last command actually executed. `used_fallback` is also set merely because fallback commands exist.

Track the actual last attempted command and whether its index was greater than zero. Continue to the next fallback after recoverable process-start failures; only propagate cancellation or interruption immediately.

## P1 — Exported activity is a privileged proxy

`ShortcutDispatchActivity` is exported without a permission:

```
AndroidManifest.xml:24-29
```

Any application capable of starting that component can ask this app to execute known built-in actions using **this app’s Shizuku grant**:

- Take screenshots
- Toggle the power key
- Expand notifications or Quick Settings

That is a confused-deputy surface. It is particularly relevant for screenshot and screen-power actions.

Cross-app invocation appears intentional in the README, so simply making the activity non-exported might break supported integrations. A safer design is:

- Separate internal shortcut dispatch from the optional public API.
- Make external integration opt-in.
- Expose only an explicit allowlist of harmless built-in actions.
- Never allow arbitrary shell commands through the exported entry point.
- Consider a per-install capability token for generated intents.
- Add throttling so another app cannot trigger hundreds of actions.

Launcher compatibility should be tested before changing export behavior.

## P1 — Static shortcuts consume the custom shortcut quota

There are four static shortcuts in `shortcuts.xml:4-54`. Dynamic publication computes:

```
maxShortcutCountPerActivity - ShortcutActions.all.size
```

at `ActionCatalog.kt:105-110`.

On a device reporting a maximum of four, this becomes zero, so no custom action is published dynamically. Android limits static and dynamic shortcuts together, and notes that launchers commonly display a maximum of four. [![](https://www.google.com/s2/favicons?domain=https://developer.android.com&sz=128)Android Developers](https://developer.android.com/develop/ui/compose/system/shortcuts/managing-shortcuts)

A better mix is:

- Two highest-value static shortcuts.
- Up to two recent or user-selected dynamic custom shortcuts.
- All remaining actions available as pinned shortcuts and inside the app.

Allow the user to choose which built-ins occupy the static/dynamic launcher menu.

## P1 — Deleted and restored shortcuts become stale or permanently disabled

Deletion does:

```
disableShortcuts(listOf(action_id))
removeDynamicShortcuts(listOf(action_id))
```

at `ActionCatalog.kt:85-89`.

Two edge cases follow:

1. Restoring a custom action with the same ID does not call `enableShortcuts()`, so an existing pinned copy can remain disabled.
2. Replace-all restore does not diff old and new IDs. Pinned shortcuts for removed actions remain present and launch into “Unknown shortcut action.”

Android distinguishes removing dynamic shortcuts from disabling their pinned copies; disabled shortcuts need explicit lifecycle handling. [![](https://www.google.com/s2/favicons?domain=https://developer.android.com&sz=128)Android Developers](https://developer.android.com/develop/ui/compose/system/shortcuts/managing-shortcuts)

During replace-all:

```
removedIds = oldIds - newIds
retainedIds = oldIds ∩ newIds
restoredIds = newIds that may previously have been disabled
```

Disable removed IDs with a useful message, update retained pinned shortcuts, and re-enable restored IDs.

Also handle:

- `ShortcutManager` return values.
- Rate limiting.
- `IllegalArgumentException`.
- IDs colliding with built-in IDs.
- Locale changes for dynamic and pinned labels.

## P1 — Corrupt local JSON can prevent app startup

Both repositories parse stored JSON directly during construction:

```
CustomAction.kt:38
WidgetBindingsRepository.kt:16
```

Malformed or partially written data throws from:

```
CustomAction.kt:121-133
WidgetBindingsRepository.kt:55-65
```

Because these objects are initialized during app entry points, one corrupt preference can repeatedly crash the app.

Use either:

- DataStore with a serializer and corruption handler, or
- At minimum, guarded parsing that quarantines the bad payload and falls back to an empty collection.

DataStore is designed for asynchronous, consistent, transactional small-data persistence and is the recommended replacement for `SharedPreferences`. [![](https://www.google.com/s2/favicons?domain=https://developer.android.com&sz=128)Android Developers](https://developer.android.com/topic/libraries/architecture/datastore)

## P1 — Backup and restore perform unbounded file I/O on the main thread

The Activity Result callbacks call backup functions directly:

```
MainActivity.kt:73-96
```

The restore function uses unrestricted `readText()`:

```
CustomActionsBackup.kt:26-29
```

A sufficiently large selected file can cause a long UI freeze or memory pressure.

Move file access and parsing to `Dispatchers.IO`, and define explicit limits for:

- File byte size.
- Number of actions.
- Label length.
- Command length.
- ID format.
- Reserved built-in IDs.

The restore UI currently reports success after updating in-memory state and scheduling asynchronous persistence/synchronization. It should report success only after the durable write and shortcut reconciliation complete.

## P2 — “Screen off” sends the Power key, not the Sleep key

The action uses:

```
ShortcutAction.kt:55-62
input keyevent 26
```

Key code 26 is `KEYCODE_POWER`. Android provides key code 223, `KEYCODE_SLEEP`, specifically to put the device to sleep while having no effect if it is already asleep. [![](https://www.google.com/s2/favicons?domain=https://developer.android.com&sz=128)Android Developers+1](https://developer.android.com/reference/android/view/KeyEvent)

Prefer:

```
listOf("input", "keyevent", "223")
```

with OEM testing. Otherwise rename the action to “Power button” or “Toggle screen” so its behavior is accurate.

---

# UX review

## Status presentation

The screen uses separate “Shizuku running” and “permission” chips. This can produce the confusing combination:

```
Stopped
Permission needed
```

when permission cannot even be evaluated meaningfully until Shizuku is running.

Use one primary status card with mutually exclusive states:

```
Shizuku is not running      [Open Shizuku]
Permission required         [Grant permission]
Ready
Connecting…
Command timed out
```

Do not automatically open setup from a gesture, but provide an explicit setup action in the main screen.

## Touch targets are too small

`IconActionButton` is exactly `28.dp`:

```
MainActivity.kt:790-811
```

`InlineActionButton` only has `6.dp × 4.dp` padding:

```
MainActivity.kt:773-787
```

Android’s accessibility guidance calls for a minimum interactive target of 48 dp. [![](https://www.google.com/s2/favicons?domain=https://developer.android.com&sz=128)Android Developers](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)

Use Material 3 `IconButton`, `TextButton`, `Button`, and `OutlinedTextField`, or enforce:

```
Modifier.minimumInteractiveComponentSize()
```

The icon itself may remain 18–24 dp inside a 48 dp target.

## Disabled-state semantics are misleading

At `MainActivity.kt:540-547`, the entire action row is dimmed when Shizuku is not ready, even though Pin, Edit, and Delete remain available.

Only the **Try** control depends on Shizuku. Leave the row and management controls at normal opacity, and disable or replace only the execution control.

## No running state or duplicate-action protection

Repeated taps or swipe invocations can start overlapping service bindings and commands. The UI provides no feedback that an action is executing.

Add:

- Per-action in-progress state.
- A short debounce for repeated identical gesture requests.
- Cancellation or serialization in the dispatcher.
- No “Opened notifications” toast after success—the opened shade is already the feedback.
- A visible timeout/error only when something fails.

## Destructive operations need recovery

Delete is immediate:

```
MainActivity.kt:598-604
```

A lightweight undo Snackbar is preferable to a confirmation dialog for ordinary deletion. Replace-all restore should show:

- Imported count.
- Current count being replaced.
- Conflicting or invalid entries.
- Whether pinned shortcuts will be disabled or updated.

The restore count string should use Android `<plurals>` rather than `%1$d imported actions`, especially for Arabic.

## Shell-command editing

For both English and Arabic:

- Render command input with a monospace font.
- Force the command content itself to LTR while leaving labels and surrounding UI RTL-aware.
- Explain that commands run with Shizuku-level shell privileges.
- Show the default timeout and maximum captured output.
- Offer common command templates rather than requiring every user to type shell syntax.

## Widget readability

`widget_action.xml:2-30` has:

- Transparent background.
- Permanently white icon.
- Permanently white text.

It can be nearly invisible on light wallpaper or light launcher surfaces.

Use a rounded, semi-opaque surface with day/night resources, sufficient contrast, and slightly larger touch dimensions. Add a widget preview and consider horizontal resizing rather than `resizeMode="none"`.

---

# Simplicity and maintainability

The project should remain a single app module; introducing a large dependency-injection framework would add more complexity than value. The main simplification opportunities are narrower.

## Split the 861-line activity

`MainActivity.kt` currently mixes:

- Activity-result file I/O.
- Shizuku orchestration.
- Mutable screen state.
- Shortcut pinning.
- Backup/restore coordination.
- Dialog logic.
- All UI components.
- Typography and styling.

A modest split would be enough:

```
MainActivity
MainViewModel
MainScreen
ActionRow / StatusCard / CustomActionDialog
CustomActionsRepository
ShortcutDispatcher
```

Use one immutable `MainUiState` and events flowing back to the ViewModel. `collectAsStateWithLifecycle()` is the recommended Android collection API for flows. [![](https://www.google.com/s2/favicons?domain=https://developer.android.com&sz=128)Android Developers](https://developer.android.com/develop/ui/compose/state)

## Prefer standard Material components

The custom `BasicText`, `Box.clickable`, text field decoration, and button implementations recreate behavior that Material components already provide:

- Touch-target enforcement.
- Focus indicators.
- Semantics.
- Disabled states.
- Ripple/interaction behavior.
- Keyboard navigation.
- Error labels.

Replacing them will reduce code and accessibility debt simultaneously.

## Keep persistence APIs suspendable

Repository methods currently mutate state synchronously, call `SharedPreferences.apply()`, and schedule shortcut updates in a private scope:

```
CustomAction.kt:79-90
```

The caller cannot know whether persistence or shortcut synchronization succeeded.

Prefer:

```
suspend fun addAction(...): Result<CustomAction>
suspend fun updateAction(...): Result<Unit>
suspend fun replaceActions(...): RestoreResult
```

This allows the UI to show truthful completion states.

---

# Testing and build review

I could not execute the Gradle test suite in this environment because Gradle 9.7.0 was not cached and the environment could not resolve `services.gradle.org`. The review is therefore based on static source analysis and execution-path tracing; I am not claiming that the project currently compiles or that its tests pass.

The repository does contain useful unit tests, but the CI gaps are significant:

- `ci.yml:32-39` runs unit tests and debug/release builds.
- It does not run `lintDebug`.
- Regular instrumentation tests are not run in CI.
- The screenshot lane runs only the screenshot test package.
- Local Mise selects Java 25 while CI selects Java 17.
- The release workflow invokes `changelogen@latest`, making release behavior non-reproducible.

Add:

```
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

Pin release tooling to exact versions and align local and CI Java versions.

## Essential regression tests

The next test set should cover:

1. Cold app process with Shizuku already running: shortcut succeeds without opening `MainActivity`.
2. Binder delivered 500–1000 ms after dispatch: action waits and succeeds.
3. Binder never arrives: bounded failure, no main UI.
4. Binder dies between permission check and service bind.
5. User service never connects: timeout and cleanup.
6. Custom `sleep 60`: terminates at configured timeout.
7. Infinite-output command: captured output remains bounded.
8. Repeated gesture invocations: only one identical command is in flight.
9. Shortcut quota of four: selected custom actions remain available.
10. Delete and restore the same action ID: pinned shortcut is re-enabled.
11. Replace-all restore removes stale pinned actions.
12. Kvaesitso cold, warm, and hot launches on Android 12–16.
13. Arabic, large font, and TalkBack interaction for every action control.

---

# Recommended implementation order

**Before the next release:** stop shortcut failures from opening `MainActivity`; add the Binder readiness wait; add `taskAffinity=""`; remove `MULTIPLE_TASK`; isolate the dispatch theme; add command, bind, and remote-call timeouts.

**Immediately afterward:** move execution to an internal service, secure the exported dispatch surface, repair shortcut delete/restore lifecycle handling, and make persistence corruption-tolerant.

**Then polish:** replace undersized custom controls with Material components, consolidate status UX, improve widget contrast, and split `MainActivity`.

The app does not need a large rewrite. The key is to make the shortcut trampoline truly disposable and move all potentially slow or unreliable work behind a bounded, lifecycle-independent dispatcher.
