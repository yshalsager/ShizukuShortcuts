package com.yshalsager.shizukushortcuts.screenshots

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yshalsager.shizukushortcuts.ActionResult
import com.yshalsager.shizukushortcuts.AppServices
import com.yshalsager.shizukushortcuts.MainActivity
import com.yshalsager.shizukushortcuts.ShizukuManagerContract
import com.yshalsager.shizukushortcuts.ShizukuState
import com.yshalsager.shizukushortcuts.ShortcutAction
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
    }

    @After
    fun tear_down() {
        AppServices.reset_for_tests()
    }

    @Test
    fun capture_home() {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<MainActivity>(intent).use {
            instrumentation.waitForIdleSync()
            SystemClock.sleep(1200)
            Screengrab.screenshot("01_home")
        }
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

        override suspend fun perform_action(action: ShortcutAction) =
            ActionResult.success(action.id, "mock", false)
    }
}
