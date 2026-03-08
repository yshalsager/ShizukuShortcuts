package com.yshalsager.shizukushortcuts

data class CommandRun(
    val exit_code: Int,
    val output: String
)

fun interface CommandRunner {
    fun run_command(command: List<String>): CommandRun
}

object ProcessCommandRunner : CommandRunner {
    override fun run_command(command: List<String>): CommandRun {
        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            val exit_code = process.waitFor()
            return CommandRun(exit_code, output)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw exception
        }
    }
}

object ActionPerformer {
    private const val max_custom_action_message_length = 8_192

    fun perform_action(action_id: String, command_runner: CommandRunner = ProcessCommandRunner): ActionResult {
        val action = ShortcutActions.find_by_id(action_id) ?: return ActionResult.unknown_action(action_id)
        var last_error = ""

        action.all_commands.forEachIndexed { index, command ->
            val run = runCatching { command_runner.run_command(command) }
                .getOrElse { exception ->
                    return ActionResult.execution_failed(
                        action_id = action.id,
                        executed_command = command.joinToString(" "),
                        message = exception.message ?: "Command failed",
                        used_fallback = index > 0
                    )
                }

            if (run.exit_code == 0) {
                return ActionResult.success(
                    action_id = action.id,
                    executed_command = command.joinToString(" "),
                    used_fallback = index > 0,
                    message = run.output
                )
            }

            last_error = run.output.ifBlank { "Exit code ${run.exit_code}" }
        }

        return ActionResult.execution_failed(
            action_id = action.id,
            executed_command = action.primary_command.joinToString(" "),
            message = last_error.ifBlank { "Command failed" },
            used_fallback = action.fallback_commands.isNotEmpty()
        )
    }

    fun perform_custom_action(action_id: String, shell_command: String, command_runner: CommandRunner = ProcessCommandRunner): ActionResult {
        val command = listOf("sh", "-c", shell_command)
        val run = runCatching { command_runner.run_command(command) }
            .getOrElse { exception ->
                return ActionResult.execution_failed(
                    action_id = action_id,
                    executed_command = command.joinToString(" "),
                    message = exception.message ?: "Command failed"
                )
            }

        if (run.exit_code == 0) {
            return ActionResult.success(
                action_id = action_id,
                executed_command = command.joinToString(" "),
                used_fallback = false,
                message = truncate_custom_action_message(run.output)
            )
        }

        return ActionResult.execution_failed(
            action_id = action_id,
            executed_command = command.joinToString(" "),
            message = truncate_custom_action_message(run.output).ifBlank { "Exit code ${run.exit_code}" }
        )
    }

    private fun truncate_custom_action_message(message: String) =
        if (message.length <= max_custom_action_message_length) message else message.take(max_custom_action_message_length)
}
