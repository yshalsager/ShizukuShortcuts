package com.yshalsager.shizukushortcuts

import android.app.Instrumentation
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortcutDispatchActivityTest {
    private val fake_manager = FakeShizukuManager()
    private lateinit var instrumentation: Instrumentation
    private lateinit var context: Context

    @Before
    fun set_up() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        context = ApplicationProvider.getApplicationContext()
        AppServices.manager_factory = { fake_manager }
    }

    @After
    fun tear_down() {
        AppServices.reset_for_tests()
    }

    @Test
    fun permission_denied_routes_to_setup() {
        fake_manager.action_result = ActionResult.permission_denied(ShortcutActions.expand_notifications.id)
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)

        ActivityScenario.launch<ShortcutDispatchActivity>(
            ShortcutActions.build_dispatch_intent(context, ShortcutActions.expand_notifications)
        ).use {}

        val launched_activity = instrumentation.waitForMonitorWithTimeout(monitor, 5_000)
        assertNotNull(launched_activity)
        launched_activity?.finish()
        instrumentation.removeMonitor(monitor)
    }

    @Test
    fun shizuku_unavailable_routes_to_setup() {
        fake_manager.action_result = ActionResult.shizuku_unavailable(ShortcutActions.expand_notifications.id)
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)

        ActivityScenario.launch<ShortcutDispatchActivity>(
            ShortcutActions.build_dispatch_intent(context, ShortcutActions.expand_notifications)
        ).use {}

        val launched_activity = instrumentation.waitForMonitorWithTimeout(monitor, 5_000)
        assertNotNull(launched_activity)
        launched_activity?.finish()
        instrumentation.removeMonitor(monitor)
    }

    @Test
    fun successful_dispatch_finishes_without_setup() {
        fake_manager.action_result = ActionResult.success(
            action_id = ShortcutActions.expand_notifications.id,
            executed_command = "cmd statusbar expand-notifications",
            used_fallback = false
        )
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)

        ActivityScenario.launch<ShortcutDispatchActivity>(
            ShortcutActions.build_dispatch_intent(context, ShortcutActions.expand_notifications)
        ).use { scenario ->
            instrumentation.waitForIdleSync()
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }

        assertNull(instrumentation.waitForMonitorWithTimeout(monitor, 1_000))
        instrumentation.removeMonitor(monitor)
    }

    private class FakeShizukuManager : ShizukuManagerContract {
        private val state_flow = MutableStateFlow(ShizukuState())

        override val state: StateFlow<ShizukuState> = state_flow
        var action_result: ActionResult = ActionResult.success("expand_notifications", "", false)

        override fun refresh_state() = Unit

        override fun request_permission() = Unit

        override suspend fun perform_action(action: ShortcutAction) =
            action_result.copy(action_id = action.id)
    }
}
