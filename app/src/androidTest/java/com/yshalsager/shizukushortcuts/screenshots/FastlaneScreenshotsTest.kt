package com.yshalsager.shizukushortcuts.screenshots

import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yshalsager.shizukushortcuts.ActionResult
import com.yshalsager.shizukushortcuts.AppServices
import com.yshalsager.shizukushortcuts.AppActionItem
import com.yshalsager.shizukushortcuts.CustomAction
import com.yshalsager.shizukushortcuts.CustomActionsRepositoryContract
import com.yshalsager.shizukushortcuts.MainActivity
import com.yshalsager.shizukushortcuts.ShizukuManagerContract
import com.yshalsager.shizukushortcuts.ShizukuState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.locale.LocaleTestRule

@RunWith(AndroidJUnit4::class)
class FastlaneScreenshotsTest {
    @get:Rule
    val locale_rule = LocaleTestRule()

    private lateinit var instrumentation: Instrumentation
    private lateinit var context: Context

    @Before
    fun set_up() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        context = ApplicationProvider.getApplicationContext()
        AppServices.manager_factory = { FakeShizukuManager() }
        AppServices.custom_actions_repository_factory = { FakeCustomActionsRepository() }
    }

    @After
    fun tear_down() {
        AppServices.reset_for_tests()
    }

    @Test
    fun capture_home() {
        apply_app_locale()
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<MainActivity>(intent).use {
            instrumentation.waitForIdleSync()
            SystemClock.sleep(1200)
            Screengrab.screenshot("01_home")
        }
    }

    private fun apply_app_locale() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val locale_manager = context.getSystemService(LocaleManager::class.java) ?: return
        locale_manager.applicationLocales = LocaleList.forLanguageTags(Screengrab.getLocale())
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300)
    }

    private class FakeShizukuManager : ShizukuManagerContract {
        private val state_flow = MutableStateFlow(
            ShizukuState(
                is_running = true,
                is_permission_granted = true,
                should_show_permission_rationale = false
            )
        )

        override val state: StateFlow<ShizukuState> = state_flow

        override fun refresh_state() = Unit

        override fun request_permission() = Unit

        override suspend fun perform_action(action: AppActionItem) =
            ActionResult.success(action.id, "mock", false)
    }

    private class FakeCustomActionsRepository : CustomActionsRepositoryContract {
        private val state_flow = MutableStateFlow(
            listOf(CustomAction("custom-screenshot", "Custom action", "cmd statusbar expand-notifications"))
        )

        override val actions: StateFlow<List<CustomAction>> = state_flow

        override fun add_action(label: String, shell_command: String) =
            CustomAction("ignored", label, shell_command)

        override fun update_action(action_id: String, label: String, shell_command: String) = Unit

        override fun replace_all_actions(actions: List<CustomAction>) {
            state_flow.value = actions
        }

        override fun delete_action(action_id: String) = Unit

        override fun find_by_id(action_id: String) = null
    }
}
