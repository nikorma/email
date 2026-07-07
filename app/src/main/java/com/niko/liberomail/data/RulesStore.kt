package com.niko.liberomail.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Salva le regole di filtro in SharedPreferences (formato JSON). */
class RulesStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("mail_rules", Context.MODE_PRIVATE)

    fun getRules(): List<MailRule> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val field = runCatching {
                    MailRule.Field.valueOf(o.getString("field"))
                }.getOrNull() ?: return@mapNotNull null
                MailRule(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    field = field,
                    value = o.getString("value")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addRule(field: MailRule.Field, value: String) {
        val clean = value.trim()
        if (clean.isEmpty()) return
        val list = getRules().toMutableList()
        list.add(MailRule(UUID.randomUUID().toString(), field, clean))
        save(list)
    }

    fun removeRule(id: String) {
        save(getRules().filterNot { it.id == id })
    }

    private fun save(list: List<MailRule>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("field", r.field.name)
                put("value", r.value)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "rules_json"
    }
}
