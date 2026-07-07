package com.niko.liberomail.data

/** Le caselle gestite dall'app e i possibili nomi cartella lato server. */
enum class Mailbox(val displayName: String, val candidates: List<String>) {
    INBOX("Posta in arrivo", listOf("INBOX")),
    SENT(
        "Posta inviata",
        listOf(
            "Posta inviata", "Sent", "INBOX.Sent", "Inviata",
            "Sent Messages", "Posta Inviata", "INBOX.Posta inviata"
        )
    )
}

/** Criteri di ordinamento della lista messaggi. */
enum class SortOrder(val label: String) {
    DATE_DESC("Data (più recenti)"),
    DATE_ASC("Data (più vecchie)"),
    SENDER_AZ("Mittente (A→Z)"),
    UNREAD_FIRST("Non lette prima")
}
