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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortcutDispatchActivityTest {
    private val fake_manager = FakeShizukuManager()
    private val fake_custom_actions_repository = FakeCustomActionsRepository()
    private lateinit var instrumentation: Instrumentation
    private lateinit var context: Context

    @Before
    fun set_up() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        context = ApplicationProvider.getApplicationContext()
        AppServices.manager_factory = { fake_manager }
        AppServices.custom_actions_repository_factory = { fake_custom_actions_repository }
    }

    @After
    fun tear_down() {
        AppServices.reset_for_tests()
    }

    @Test
    fun permission_denied_finishes_without_setup() = assert_dispatch_finishes_without_setup(
        ActionResult.permission_denied(ShortcutActions.expand_notifications.id)
    )

    @Test
    fun shizuku_unavailable_finishes_without_setup() = assert_dispatch_finishes_without_setup(
        ActionResult.shizuku_unavailable(ShortcutActions.expand_notifications.id)
    )

    @Test
    fun busy_dispatch_finishes_silently() = assert_dispatch_finishes_without_setup(
        ActionResult.busy(ShortcutActions.expand_notifications.id)
    )

    @Test
    fun successful_dispatch_finishes_without_setup() = assert_dispatch_finishes_without_setup(
        ActionResult.success(
            action_id = ShortcutActions.expand_notifications.id,
            executed_command = "cmd statusbar expand-notifications",
            used_fallback = false
        )
    )

    @Test
    fun custom_dispatch_resolves_by_id() {
        fake_custom_actions_repository.set_actions(listOf(CustomAction("custom-id", "Custom", "cmd statusbar expand-notifications")))
        fake_manager.action_result = ActionResult.success(
            action_id = "custom-id",
            executed_command = "sh -c cmd statusbar expand-notifications",
            used_fallback = false
        )

        ActivityScenario.launch<ShortcutDispatchActivity>(
            ActionCatalog.build_dispatch_intent(context, ActionCatalog.find_by_id(context, "custom-id")!!)
        ).use { scenario ->
            instrumentation.waitForIdleSync()
            assertEquals("custom-id", fake_manager.last_action?.id)
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    private fun assert_dispatch_finishes_without_setup(result: ActionResult) {
        fake_manager.action_result = result
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)

        ActivityScenario.launch<ShortcutDispatchActivity>(
            ActionCatalog.build_dispatch_intent(context, ActionCatalog.built_in_actions(context).first { it.id == ShortcutActions.expand_notifications.id })
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
        override val running_action_id = MutableStateFlow<String?>(null)
        var action_result: ActionResult = ActionResult.success("expand_notifications", "", false)
        var last_action: AppActionItem? = null

        override fun refresh_state() = Unit

        override fun request_permission() = Unit

        override suspend fun perform_action(action: AppActionItem): ActionResult {
            last_action = action
            return action_result.copy(action_id = action.id)
        }
    }

    private class FakeCustomActionsRepository : CustomActionsRepositoryContract {
        private val state_flow = MutableStateFlow<List<CustomAction>>(emptyList())

        override val actions: StateFlow<List<CustomAction>> = state_flow

        override fun add_action(label: String, shell_command: String) =
            CustomAction("ignored", label, shell_command)

        override fun update_action(action_id: String, label: String, shell_command: String) = Unit

        override suspend fun replace_all_actions(actions: List<CustomAction>): Boolean {
            state_flow.value = actions
            return true
        }

        override fun delete_action(action_id: String) = Unit

        override fun find_by_id(action_id: String) = state_flow.value.firstOrNull { it.id == action_id }

        fun set_actions(actions: List<CustomAction>) {
            state_flow.value = actions
        }
    }
}
