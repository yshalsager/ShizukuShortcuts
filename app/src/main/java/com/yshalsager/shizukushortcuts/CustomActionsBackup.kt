package com.yshalsager.shizukushortcuts

import android.content.ContentResolver
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val custom_actions_backup_version = 1
private const val custom_actions_backup_file_prefix = "shizuku-custom-actions-backup"
private const val max_backup_bytes = 256 * 1024
private const val max_backup_actions = 100
private const val max_backup_label_length = 200
private const val max_backup_command_length = 8192
private val custom_actions_backup_timestamp_format = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

fun custom_actions_backup_file_name(now: LocalDateTime = LocalDateTime.now()): String {
    return "$custom_actions_backup_file_prefix-${now.format(custom_actions_backup_timestamp_format)}.json"
}

fun write_custom_actions_backup(content_resolver: ContentResolver, uri: Uri, actions: List<CustomAction>): Boolean {
    return runCatching {
        content_resolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(serialize_custom_actions_backup(actions))
        } ?: error("Missing output stream")
    }.isSuccess
}

fun read_custom_actions_backup(content_resolver: ContentResolver, uri: Uri): List<CustomAction> {
    val serialized_backup = content_resolver.openInputStream(uri)?.use(InputStream::read_backup_text)
        ?: error("Missing input stream")
    return parse_custom_actions_backup(serialized_backup)
}

fun serialize_custom_actions_backup(actions: List<CustomAction>): String {
    return JSONObject()
        .put("version", custom_actions_backup_version)
        .put("actions", JSONArray(serialize_custom_actions(actions)))
        .toString()
}

fun parse_custom_actions_backup(serialized_backup: String): List<CustomAction> {
    val backup_json = runCatching { JSONObject(serialized_backup) }
        .getOrElse { throw IllegalArgumentException("Invalid backup file") }

    if (!backup_json.has("version")) throw IllegalArgumentException("Missing backup version")
    if (backup_json.get("version") != custom_actions_backup_version) throw IllegalArgumentException("Unsupported backup version")

    val actions_json = backup_json.optJSONArray("actions") ?: throw IllegalArgumentException("Missing backup actions")
    val actions = runCatching { parse_custom_actions(actions_json.toString()) }
        .getOrElse { throw IllegalArgumentException("Invalid backup actions") }
        .map { action ->
            action.copy(
                id = action.id.trim(),
                label = action.label.trim(),
                shell_command = action.shell_command.trim()
            )
        }

    if (actions.size > max_backup_actions) throw IllegalArgumentException("Too many backup actions")
    if (actions.any { it.id.isBlank() || it.label.isBlank() || it.shell_command.isBlank() }) {
        throw IllegalArgumentException("Invalid backup action")
    }
    if (actions.any { it.label.length > max_backup_label_length || it.shell_command.length > max_backup_command_length }) {
        throw IllegalArgumentException("Backup action too long")
    }
    if (actions.any { it.id in ShortcutActions.ids }) throw IllegalArgumentException("Reserved backup action id")
    if (actions.any { runCatching { UUID.fromString(it.id) }.getOrNull()?.toString() != it.id }) {
        throw IllegalArgumentException("Invalid backup action id")
    }
    if (actions.map { it.id }.toSet().size != actions.size) throw IllegalArgumentException("Duplicate backup action id")
    return actions
}

internal fun InputStream.read_backup_text(): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) return output.toString(Charsets.UTF_8.name())
        total += count
        if (total > max_backup_bytes) throw IllegalArgumentException("Backup too large")
        output.write(buffer, 0, count)
    }
}
