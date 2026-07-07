package com.niko.liberomail.data

/**
 * Regola di filtro: se un'email in arrivo corrisponde, viene spostata nel Cestino.
 * Il confronto è "contiene", senza distinzione tra maiuscole e minuscole.
 */
data class MailRule(
    val id: String,
    val field: Field,
    val value: String
) {
    enum class Field(val label: String) {
        SENDER("Mittente"),
        SUBJECT("Oggetto")
    }

    fun matches(fromDisplay: String, fromAddress: String, subject: String): Boolean {
        val v = value.trim().lowercase()
        if (v.isEmpty()) return false
        return when (field) {
            Field.SENDER ->
                fromDisplay.lowercase().contains(v) || fromAddress.lowercase().contains(v)
            Field.SUBJECT ->
                subject.lowercase().contains(v)
        }
    }

    fun describe(): String = "${field.label} contiene \"$value\""
}
