package com.yshalsager.shizukushortcuts

class PrivilegedStatusBarService : IPrivilegedStatusBarService.Stub() {
    override fun perform_action(action_id: String) = ActionPerformer.perform_action(action_id)
}
