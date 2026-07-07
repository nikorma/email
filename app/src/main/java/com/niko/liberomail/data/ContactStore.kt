package com.niko.liberomail.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Rubrica interna salvata in SharedPreferences (formato JSON). */
class ContactStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("mail_contacts", Context.MODE_PRIVATE)

    fun getContacts(): List<Contact> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Contact(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.optString("name", ""),
                    email = o.getString("email")
                )
            }.sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addContact(name: String, email: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty()) return
        val list = getContacts().toMutableList()
        list.add(Contact(UUID.randomUUID().toString(), name.trim(), cleanEmail))
        save(list)
    }

    fun removeContact(id: String) {
        save(getContacts().filterNot { it.id == id })
    }

    private fun save(list: List<Contact>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("email", c.email)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "contacts_json"
    }
}
