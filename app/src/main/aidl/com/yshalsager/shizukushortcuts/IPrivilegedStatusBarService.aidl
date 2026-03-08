package com.yshalsager.shizukushortcuts;

import com.yshalsager.shizukushortcuts.ActionResult;

interface IPrivilegedStatusBarService {
    ActionResult perform_action(String action_id);
    ActionResult perform_custom_action(String action_id, String shell_command);
}
