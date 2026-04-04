package com.yshalsager.shizukushortcuts

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    fun custom_action_is_visible_and_can_be_deleted() {
        compose_rule.onNodeWithText("Custom command").assertIsDisplayed()
        compose_rule.onNodeWithContentDescription(compose_rule.activity.getString(R.string.delete_action)).performClick()
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

        override fun replace_all_actions(actions: List<CustomAction>) {
            state_flow.value = actions
        }

        override fun delete_action(action_id: String) {
            state_flow.value = state_flow.value.filterNot { it.id == action_id }
        }

        override fun find_by_id(action_id: String) = state_flow.value.firstOrNull { it.id == action_id }
    }
}
