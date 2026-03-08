package com.yshalsager.shizukushortcuts

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ActionResult(
    val status_code: Int,
    val action_id: String,
    val executed_command: String = "",
    val message: String = "",
    val used_fallback: Boolean = false
) : Parcelable {

    val is_success: Boolean
        get() = status_code == STATUS_SUCCESS

    companion object {
        const val STATUS_SUCCESS = 0
        const val STATUS_UNKNOWN_ACTION = 1
        const val STATUS_EXECUTION_FAILED = 2
        const val STATUS_SHIZUKU_UNAVAILABLE = 3
        const val STATUS_PERMISSION_DENIED = 4

        fun success(action_id: String, executed_command: String, used_fallback: Boolean, message: String = "") =
            ActionResult(STATUS_SUCCESS, action_id, executed_command, message, used_fallback)

        fun unknown_action(action_id: String) =
            ActionResult(STATUS_UNKNOWN_ACTION, action_id, message = "Unknown action")

        fun execution_failed(action_id: String, executed_command: String, message: String, used_fallback: Boolean = false) =
            ActionResult(STATUS_EXECUTION_FAILED, action_id, executed_command, message, used_fallback)

        fun shizuku_unavailable(action_id: String) =
            ActionResult(STATUS_SHIZUKU_UNAVAILABLE, action_id, message = "Shizuku is not running")

        fun permission_denied(action_id: String) =
            ActionResult(STATUS_PERMISSION_DENIED, action_id, message = "Permission denied")
    }
}

