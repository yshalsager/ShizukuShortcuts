package com.yshalsager.shizukushortcuts

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class WidgetBindingsRepository(
    app_context: Context
) {
    companion object {
        private const val prefs_name = "widget_bindings"
        private const val bindings_key = "bindings"
    }

    private val shared_preferences = app_context.applicationContext.getSharedPreferences(prefs_name, Context.MODE_PRIVATE)
    private var bindings = parse_widget_bindings(shared_preferences.getString(bindings_key, null))

    fun set_binding(app_widget_id: Int, action_id: String) {
        bindings = bindings + (app_widget_id to action_id)
        persist()
    }

    fun get_binding(app_widget_id: Int) = bindings[app_widget_id]

    fun remove_bindings(app_widget_ids: IntArray) {
        if (app_widget_ids.isEmpty()) return
        bindings = bindings.filterKeys { it !in app_widget_ids }
        persist()
    }

    fun clear_all_bindings() {
        bindings = emptyMap()
        persist()
    }

    private fun persist() {
        shared_preferences.edit().putString(bindings_key, serialize_widget_bindings(bindings)).apply()
    }
}

fun serialize_widget_bindings(bindings: Map<Int, String>): String {
    val json_array = JSONArray()

    bindings.toSortedMap().forEach { (app_widget_id, action_id) ->
        json_array.put(
            JSONObject()
                .put("app_widget_id", app_widget_id)
                .put("action_id", action_id)
        )
    }

    return json_array.toString()
}

fun parse_widget_bindings(serialized_bindings: String?): Map<Int, String> {
    if (serialized_bindings.isNullOrBlank()) return emptyMap()
    val json_array = JSONArray(serialized_bindings)
    val bindings = mutableMapOf<Int, String>()

    for (index in 0 until json_array.length()) {
        val json_object = json_array.getJSONObject(index)
        bindings[json_object.getInt("app_widget_id")] = json_object.getString("action_id")
    }

    return bindings
}
