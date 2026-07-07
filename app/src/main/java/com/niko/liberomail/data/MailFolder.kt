package com.niko.liberomail.data

/** Una cartella IMAP sul server (Posta in arrivo, Inviata, Cestino, Spam, ...). */
data class MailFolder(
    val fullName: String,     // nome tecnico lato server, es. "INBOX" o "Posta inviata"
    val displayName: String,  // nome mostrato all'utente
    val isSent: Boolean,      // se true mostriamo il destinatario invece del mittente
    val isInbox: Boolean
) {
    companion object {
        val INBOX = MailFolder("INBOX", "Posta in arrivo", isSent = false, isInbox = true)
    }
}

/** Criteri di ordinamento della lista messaggi. */
enum class SortOrder(val label: String) {
    DATE_DESC("Data (più recenti)"),
    DATE_ASC("Data (più vecchie)"),
    SENDER_AZ("Mittente (A→Z)"),
    UNREAD_FIRST("Non lette prima")
}
