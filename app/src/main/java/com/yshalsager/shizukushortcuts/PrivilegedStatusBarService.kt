package com.yshalsager.shizukushortcuts

class PrivilegedStatusBarService : IPrivilegedStatusBarService.Stub() {
    override fun perform_action(action_id: String) = ActionPerformer.perform_action(action_id)

    override fun perform_custom_action(action_id: String, shell_command: String) =
        ActionPerformer.perform_custom_action(action_id, shell_command)
}
