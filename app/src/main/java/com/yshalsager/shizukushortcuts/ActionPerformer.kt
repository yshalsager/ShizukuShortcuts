package com.yshalsager.shizukushortcuts

import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream

data class CommandRun(
    val exit_code: Int,
    val output: String,
    val timed_out: Boolean = false
)

fun interface CommandRunner {
    fun run_command(command: List<String>): CommandRun
}

object ProcessCommandRunner : CommandRunner {
    private const val max_output_bytes = 64 * 1_024
    // ponytail: timeout exits overlap command exits; restore an app timer if exact classification matters
    private val timeout_exit_codes = setOf(124, 137, 143)

    override fun run_command(command: List<String>): CommandRun {
        val process = ProcessBuilder(listOf("timeout", "-k", ".25", "5") + command)
            .redirectErrorStream(true)
            .start()
        val output = ByteArrayOutputStream(max_output_bytes)
        val output_reader = Thread {
            process.inputStream.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = runCatching { input.read(buffer) }.getOrDefault(-1)
                    if (count < 0) break
                    val remaining = max_output_bytes - output.size()
                    if (remaining > 0) output.write(buffer, 0, minOf(count, remaining))
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        try {
            val exit_code = process.waitFor()
            if (output_reader.isAlive) process.inputStream.close()
            output_reader.join()
            return CommandRun(
                exit_code,
                output.toByteArray().toString(Charsets.UTF_8).trim(),
                exit_code in timeout_exit_codes
            )
        } catch (exception: InterruptedException) {
            // ponytail: native timeout may take the remaining five seconds to clean up its child
            Thread.currentThread().interrupt()
            throw exception
        } finally {
            if (output_reader.isAlive) runCatching { process.inputStream.close() }
        }
    }
}

object ActionPerformer {
    private const val max_custom_action_message_length = 8_192

    fun perform_action(action_id: String, command_runner: CommandRunner = ProcessCommandRunner): ActionResult {
        val action = ShortcutActions.find_by_id(action_id) ?: return ActionResult.unknown_action(action_id)
        var last_command = action.primary_command.joinToString(" ")
        var last_error = "Command failed"
        var used_fallback = false

        for ((index, command) in action.all_commands.withIndex()) {
            last_command = command.joinToString(" ")
            used_fallback = index > 0
            val run = try {
                command_runner.run_command(command)
            } catch (exception: Exception) {
                if (exception is InterruptedException || exception is CancellationException) throw exception
                last_error = exception.message ?: "Command failed"
                continue
            }

            if (run.timed_out) {
                return ActionResult.execution_timed_out(action.id, last_command, used_fallback)
            }

            if (run.exit_code == 0) {
                return ActionResult.success(action.id, last_command, used_fallback, run.output)
            }

            last_error = run.output.ifBlank { "Exit code ${run.exit_code}" }
        }

        return ActionResult.execution_failed(action.id, last_command, last_error, used_fallback)
    }

    fun perform_custom_action(action_id: String, shell_command: String, command_runner: CommandRunner = ProcessCommandRunner): ActionResult {
        val command = listOf("sh", "-c", shell_command)
        val run = runCatching { command_runner.run_command(command) }
            .getOrElse { exception ->
                if (exception is InterruptedException) throw exception
                return ActionResult.execution_failed(
                    action_id = action_id,
                    executed_command = command.joinToString(" "),
                    message = exception.message ?: "Command failed"
                )
            }

        if (run.timed_out) {
            return ActionResult.execution_timed_out(
                action_id = action_id,
                executed_command = command.joinToString(" ")
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
