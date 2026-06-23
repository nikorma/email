package com.niko.liberomail.mail

import com.niko.liberomail.data.EmailMessage
import java.util.Properties
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeUtility

/**
 * Gestisce la connessione IMAP verso Libero (imapmail.libero.it:993, SSL).
 * Tutti i metodi sono bloccanti: vanno chiamati da un dispatcher IO.
 */
class MailClient(
    private val host: String,
    private val port: Int,
    private val user: String,
    private val password: String
) {

    private fun session(): Session {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", host)
            put("mail.imaps.port", port.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "20000")
            put("mail.imaps.writetimeout", "20000")
            // Compatibilità protocolli TLS moderni
            put("mail.imaps.ssl.protocols", "TLSv1.2 TLSv1.3")
        }
        return Session.getInstance(props)
    }

    private fun openStore(): Store {
        val store = session().getStore("imaps")
        store.connect(host, port, user, password)
        return store
    }

    /** Verifica login: lancia un'eccezione se le credenziali/parametri sono errati. */
    fun testConnection() {
        val store = openStore()
        store.close()
    }

    /** Restituisce le ultime [limit] email della Posta in arrivo (più recenti per prime). */
    fun fetchInbox(limit: Int = 50): List<EmailMessage> {
        val store = openStore()
        try {
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            try {
                val total = inbox.messageCount
                if (total == 0) return emptyList()

                val start = (total - limit + 1).coerceAtLeast(1)
                val messages = inbox.getMessages(start, total)

                val fp = javax.mail.FetchProfile().apply {
                    add(javax.mail.FetchProfile.Item.ENVELOPE)
                    add(javax.mail.FetchProfile.Item.FLAGS)
                    add(javax.mail.UIDFolder.FetchProfileItem.UID)
                }
                inbox.fetch(messages, fp)

                val uidFolder = inbox as javax.mail.UIDFolder
                val result = ArrayList<EmailMessage>(messages.size)
                for (msg in messages) {
                    result.add(
                        EmailMessage(
                            uid = uidFolder.getUID(msg),
                            from = formatFrom(msg),
                            subject = decode(msg.subject) ?: "(senza oggetto)",
                            dateMillis = (msg.receivedDate ?: msg.sentDate)?.time ?: 0L,
                            seen = msg.isSet(Flags.Flag.SEEN)
                        )
                    )
                }
                // Più recenti per primi
                return result.sortedByDescending { it.dateMillis }
            } finally {
                inbox.close(false)
            }
        } finally {
            store.close()
        }
    }

    /** Scarica il corpo completo di un messaggio dato il suo UID e lo marca come letto. */
    fun fetchBody(uid: Long): EmailMessage? {
        val store = openStore()
        try {
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)
            try {
                val uidFolder = inbox as javax.mail.UIDFolder
                val msg = uidFolder.getMessageByUID(uid) ?: return null

                val (text, isHtml) = extractBody(msg)
                msg.setFlag(Flags.Flag.SEEN, true)

                return EmailMessage(
                    uid = uid,
                    from = formatFrom(msg),
                    subject = decode(msg.subject) ?: "(senza oggetto)",
                    dateMillis = (msg.receivedDate ?: msg.sentDate)?.time ?: 0L,
                    seen = true,
                    body = text,
                    isHtml = isHtml
                )
            } finally {
                inbox.close(false)
            }
        } finally {
            store.close()
        }
    }

    /**
     * Cancella un messaggio: prova a spostarlo nel Cestino, poi lo rimuove dall'INBOX.
     * Se il Cestino non è disponibile, esegue una cancellazione definitiva.
     */
    fun deleteMessage(uid: Long): Boolean {
        val store = openStore()
        try {
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)
            try {
                val uidFolder = inbox as javax.mail.UIDFolder
                val msg = uidFolder.getMessageByUID(uid) ?: return false

                // Tentativo: copia nel Cestino prima di rimuovere
                val trash = findTrashFolder(store)
                if (trash != null) {
                    try {
                        inbox.copyMessages(arrayOf(msg), trash)
                    } catch (_: Exception) {
                        // se la copia fallisce, procediamo comunque con la rimozione
                    }
                }

                msg.setFlag(Flags.Flag.DELETED, true)
                inbox.expunge()
                return true
            } finally {
                inbox.close(true)
            }
        } finally {
            store.close()
        }
    }

    /** Per il worker: restituisce i messaggi con UID maggiore di [sinceUid]. */
    fun fetchNewerThan(sinceUid: Long, limit: Int = 30): List<EmailMessage> {
        val all = fetchInbox(limit)
        return all.filter { it.uid > sinceUid }
    }

    // ---------- Helper privati ----------

    private fun findTrashFolder(store: Store): Folder? {
        val candidates = listOf("Cestino", "Trash", "INBOX.Trash", "Deleted", "Posta eliminata")
        for (name in candidates) {
            try {
                val f = store.getFolder(name)
                if (f.exists()) return f
            } catch (_: Exception) {
                // ignora e prova il successivo
            }
        }
        return null
    }

    private fun formatFrom(msg: Message): String {
        val froms = msg.from ?: return "(mittente sconosciuto)"
        if (froms.isEmpty()) return "(mittente sconosciuto)"
        val a = froms[0]
        return if (a is InternetAddress) {
            a.personal?.let { decode(it) } ?: a.address ?: "(mittente sconosciuto)"
        } else {
            decode(a.toString()) ?: "(mittente sconosciuto)"
        }
    }

    private fun decode(value: String?): String? {
        if (value == null) return null
        return try {
            MimeUtility.decodeText(value)
        } catch (_: Exception) {
            value
        }
    }

    /** Estrae il testo del messaggio. Ritorna (contenuto, isHtml). */
    private fun extractBody(part: Part): Pair<String, Boolean> {
        try {
            if (part.isMimeType("text/plain")) {
                return (part.content?.toString() ?: "") to false
            }
            if (part.isMimeType("text/html")) {
                return (part.content?.toString() ?: "") to true
            }
            if (part.isMimeType("multipart/*")) {
                val mp = part.content as? Multipart ?: return "" to false
                var htmlFallback: String? = null
                for (i in 0 until mp.count) {
                    val bp = mp.getBodyPart(i)
                    val disposition = bp.disposition
                    if (disposition != null &&
                        disposition.equals(Part.ATTACHMENT, ignoreCase = true)
                    ) {
                        continue
                    }
                    val (text, isHtml) = extractBody(bp)
                    if (text.isNotBlank()) {
                        if (!isHtml) return text to false // text/plain ha priorità
                        if (htmlFallback == null) htmlFallback = text
                    }
                }
                if (htmlFallback != null) return htmlFallback to true
            }
        } catch (_: Exception) {
            // contenuto non leggibile
        }
        return "" to false
    }
}
