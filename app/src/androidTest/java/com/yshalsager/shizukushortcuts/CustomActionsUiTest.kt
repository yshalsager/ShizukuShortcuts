package com.yshalsager.shizukushortcuts

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

class CustomActionsUiTest {
    private val fake_manager = FakeShizukuManager()
    private val fake_repository = FakeCustomActionsRepository(
        listOf(CustomAction("custom-id", "Custom command", "cmd statusbar expand-notifications"))
    )

    private val app_services_rule = object : ExternalResource() {
        override fun before() {
            AppServices.manager_factory = { fake_manager }
            AppServices.custom_actions_repository_factory = { fake_repository }
        }

        override fun after() {
            AppServices.reset_for_tests()
        }
    }

    private val compose_rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(app_services_rule).around(compose_rule)

    @Test
    fun custom_action_management_stays_accessible_when_shizuku_is_unavailable() {
        fake_manager.set_not_ready()
        compose_rule.onNodeWithText("Custom command").assertIsDisplayed()
        val delete_button = compose_rule.onNodeWithContentDescription(compose_rule.activity.getString(R.string.delete_action))

        delete_button.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).assertIsEnabled().assertHasClickAction()
        compose_rule.onNodeWithContentDescription(compose_rule.activity.getString(R.string.edit_action)).assertIsEnabled().assertHasClickAction()
        compose_rule.onAllNodesWithContentDescription(compose_rule.activity.getString(R.string.pin_action))[ShortcutActions.all.size]
            .assertIsEnabled().assertHasClickAction()
        compose_rule.onAllNodesWithText(compose_rule.activity.getString(R.string.try_action).uppercase())[ShortcutActions.all.size]
            .assertIsNotEnabled()
        delete_button.performClick()
        compose_rule.onAllNodesWithText("Custom command").assertCountEquals(0)
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

        fun set_not_ready() {
            state_flow.value = state_flow.value.copy(is_running = false, is_permission_granted = false)
        }
    }

    private class FakeCustomActionsRepository(actions: List<CustomAction>) : CustomActionsRepositoryContract {
        private val state_flow = MutableStateFlow(actions)

        override val actions: StateFlow<List<CustomAction>> = state_flow

        override fun add_action(label: String, shell_command: String) =
            CustomAction("ignored", label, shell_command)

        override fun update_action(action_id: String, label: String, shell_command: String) {
            state_flow.value = state_flow.value.map { action ->
                if (action.id != action_id) action else action.copy(label = label, shell_command = shell_command)
            }
        }

        override suspend fun replace_all_actions(actions: List<CustomAction>): Boolean {
            state_flow.value = actions
            return true
        }

        override fun delete_action(action_id: String) {
            state_flow.value = state_flow.value.filterNot { it.id == action_id }
        }

        override fun find_by_id(action_id: String) = state_flow.value.firstOrNull { it.id == action_id }
    }
}
