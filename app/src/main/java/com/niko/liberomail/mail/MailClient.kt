package com.niko.liberomail.mail

import android.util.Base64
import com.niko.liberomail.data.EmailMessage
import com.niko.liberomail.data.MailFolder
import com.niko.liberomail.data.MailRule
import java.util.Date
import java.util.Properties
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeUtility

/**
 * Connessione IMAP/SMTP verso Libero. Metodi bloccanti: chiamare da un dispatcher IO.
 */
class MailClient(
    private val host: String,
    private val port: Int,
    private val user: String,
    private val password: String,
    private val smtpHost: String = "smtp.libero.it",
    private val smtpPort: Int = 465
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
            put("mail.imaps.ssl.protocols", "TLSv1.2 TLSv1.3")
        }
        return Session.getInstance(props)
    }

    private fun openStore(): Store {
        val store = session().getStore("imaps")
        store.connect(host, port, user, password)
        return store
    }

    fun testConnection() {
        openStore().close()
    }

    /** Elenco delle cartelle del server (Posta in arrivo per prima). */
    fun listFolders(): List<MailFolder> {
        val store = openStore()
        try {
            val all = store.defaultFolder.list("*")
            val result = ArrayList<MailFolder>()
            var hasInbox = false
            for (f in all) {
                try {
                    if ((f.type and Folder.HOLDS_MESSAGES) == 0) continue
                    val full = f.fullName
                    val lower = full.lowercase()
                    val isInbox = full.equals("INBOX", true)
                    if (isInbox) hasInbox = true
                    val isSent = lower.contains("sent") || lower.contains("inviat")
                    result.add(MailFolder(full, displayNameFor(full, f.name), isSent, isInbox))
                } catch (_: Exception) {
                    // cartella non selezionabile: ignora
                }
            }
            if (!hasInbox) result.add(MailFolder.INBOX)
            return result.sortedWith(
                compareByDescending<MailFolder> { it.isInbox }
                    .thenByDescending { it.isSent }
                    .thenBy { it.displayName.lowercase() }
            )
        } finally {
            store.close()
        }
    }

    private fun displayNameFor(full: String, name: String): String {
        val l = full.lowercase()
        return when {
            l == "inbox" -> "Posta in arrivo"
            l.contains("sent") || l.contains("inviat") -> "Posta inviata"
            l.contains("draft") || l.contains("bozze") -> "Bozze"
            l.contains("trash") || l.contains("cestino") || l.contains("deleted") ||
                l.contains("eliminat") -> "Cestino"
            l.contains("junk") || l.contains("spam") || l.contains("indesiderat") -> "Posta indesiderata"
            else -> name
        }
    }

    /**
     * Ultimi [limit] messaggi della cartella [folderName]. Se [rules] non è vuota e la
     * cartella è INBOX, i messaggi che corrispondono a una regola vengono spostati nel
     * Cestino e non compaiono nella lista.
     */
    fun fetchMessages(
        folderName: String,
        isSent: Boolean,
        limit: Int = 60,
        rules: List<MailRule> = emptyList()
    ): List<EmailMessage> {
        val store = openStore()
        try {
            val applyRules = folderName.equals("INBOX", true) && rules.isNotEmpty()
            val mode = if (applyRules) Folder.READ_WRITE else Folder.READ_ONLY
            val folder = store.getFolder(folderName)
            if (!folder.exists()) return emptyList()
            folder.open(mode)
            try {
                val total = folder.messageCount
                if (total == 0) return emptyList()

                val start = (total - limit + 1).coerceAtLeast(1)
                val messages = folder.getMessages(start, total)

                val fp = javax.mail.FetchProfile().apply {
                    add(javax.mail.FetchProfile.Item.ENVELOPE)
                    add(javax.mail.FetchProfile.Item.FLAGS)
                    add(javax.mail.UIDFolder.FetchProfileItem.UID)
                }
                folder.fetch(messages, fp)

                val uidFolder = folder as javax.mail.UIDFolder
                val result = ArrayList<EmailMessage>(messages.size)
                val toTrash = ArrayList<Message>()

                for (msg in messages) {
                    val display = if (isSent) formatRecipient(msg) else formatFrom(msg)
                    val subject = decode(msg.subject) ?: "(senza oggetto)"

                    if (applyRules) {
                        val address = fromRawAddress(msg)
                        if (rules.any { it.matches(display, address, subject) }) {
                            toTrash.add(msg)
                            continue
                        }
                    }

                    result.add(
                        EmailMessage(
                            uid = uidFolder.getUID(msg),
                            contact = display,
                            subject = subject,
                            dateMillis = (msg.receivedDate ?: msg.sentDate)?.time ?: 0L,
                            seen = msg.isSet(Flags.Flag.SEEN),
                            outgoing = isSent
                        )
                    )
                }

                if (toTrash.isNotEmpty()) moveToTrash(store, folder, toTrash.toTypedArray())

                return result.sortedByDescending { it.dateMillis }
            } finally {
                folder.close(applyRules)
            }
        } finally {
            store.close()
        }
    }

    fun fetchBody(folderName: String, isSent: Boolean, uid: Long): EmailMessage? {
        val store = openStore()
        try {
            val folder = store.getFolder(folderName)
            if (!folder.exists()) return null
            folder.open(Folder.READ_WRITE)
            try {
                val uidFolder = folder as javax.mail.UIDFolder
                val msg = uidFolder.getMessageByUID(uid) ?: return null

                val (html, isHtml) = extractRenderableBody(msg)
                if (!isSent) msg.setFlag(Flags.Flag.SEEN, true)

                return EmailMessage(
                    uid = uid,
                    contact = if (isSent) formatRecipient(msg) else formatFrom(msg),
                    subject = decode(msg.subject) ?: "(senza oggetto)",
                    dateMillis = (msg.receivedDate ?: msg.sentDate)?.time ?: 0L,
                    seen = true,
                    outgoing = isSent,
                    body = html,
                    isHtml = isHtml
                )
            } finally {
                folder.close(false)
            }
        } finally {
            store.close()
        }
    }

    fun deleteMessages(folderName: String, uids: List<Long>): Boolean {
        if (uids.isEmpty()) return true
        val store = openStore()
        try {
            val folder = store.getFolder(folderName)
            if (!folder.exists()) return false
            folder.open(Folder.READ_WRITE)
            try {
                val uidFolder = folder as javax.mail.UIDFolder
                val toDelete = uids.mapNotNull { uidFolder.getMessageByUID(it) }.toTypedArray()
                if (toDelete.isEmpty()) return false

                val isTrash = TRASH_CANDIDATES.any { folderName.equals(it, true) }
                if (!isTrash) moveToTrash(store, folder, toDelete)
                else for (m in toDelete) m.setFlag(Flags.Flag.DELETED, true)

                folder.expunge()
                return true
            } finally {
                folder.close(true)
            }
        } finally {
            store.close()
        }
    }

    fun fetchNewerThan(
        sinceUid: Long,
        limit: Int = 30,
        rules: List<MailRule> = emptyList()
    ): List<EmailMessage> =
        fetchMessages("INBOX", isSent = false, limit = limit, rules = rules)
            .filter { it.uid > sinceUid }

    /** Invia un'email via SMTP (smtp.libero.it:465, SSL). [to] può contenere più indirizzi separati da virgola. */
    fun sendEmail(to: String, subject: String, body: String) {
        val props = Properties().apply {
            put("mail.transport.protocol", "smtps")
            put("mail.smtps.host", smtpHost)
            put("mail.smtps.port", smtpPort.toString())
            put("mail.smtps.auth", "true")
            put("mail.smtps.ssl.enable", "true")
            put("mail.smtps.ssl.protocols", "TLSv1.2 TLSv1.3")
            put("mail.smtps.connectiontimeout", "15000")
            put("mail.smtps.timeout", "20000")
        }
        val session = Session.getInstance(props)
        val msg = MimeMessage(session)
        msg.setFrom(InternetAddress(user))
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false))
        msg.subject = MimeUtility.encodeText(subject, "UTF-8", null)
        msg.setText(body, "UTF-8")
        msg.sentDate = Date()

        val transport = session.getTransport("smtps")
        try {
            transport.connect(smtpHost, smtpPort, user, password)
            transport.sendMessage(msg, msg.allRecipients)
        } finally {
            transport.close()
        }
    }

    // ---------- Helper ----------

    private fun moveToTrash(store: Store, source: Folder, msgs: Array<Message>) {
        if (msgs.isEmpty()) return
        val trash = findFolder(store, TRASH_CANDIDATES)
        if (trash != null && trash.fullName != source.fullName) {
            try {
                source.copyMessages(msgs, trash)
            } catch (_: Exception) {
            }
        }
        for (m in msgs) m.setFlag(Flags.Flag.DELETED, true)
    }

    private fun findFolder(store: Store, candidates: List<String>): Folder? {
        for (name in candidates) {
            try {
                val f = store.getFolder(name)
                if (f.exists()) return f
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun formatFrom(msg: Message): String {
        val froms = msg.from ?: return "(mittente sconosciuto)"
        return addressLabel(froms.firstOrNull()) ?: "(mittente sconosciuto)"
    }

    private fun formatRecipient(msg: Message): String {
        val to = try { msg.getRecipients(Message.RecipientType.TO) } catch (_: Exception) { null }
        return addressLabel(to?.firstOrNull()) ?: "(destinatario sconosciuto)"
    }

    private fun fromRawAddress(msg: Message): String {
        val a = (msg.from ?: return "").firstOrNull() ?: return ""
        return if (a is InternetAddress) a.address ?: "" else a.toString()
    }

    private fun addressLabel(addr: javax.mail.Address?): String? {
        if (addr == null) return null
        return if (addr is InternetAddress) {
            addr.personal?.let { decode(it) } ?: addr.address
        } else {
            decode(addr.toString())
        }
    }

    private fun decode(value: String?): String? {
        if (value == null) return null
        return try { MimeUtility.decodeText(value) } catch (_: Exception) { value }
    }

    private fun extractRenderableBody(part: Part): Pair<String, Boolean> {
        val cidImages = HashMap<String, String>()
        collectInlineImages(part, cidImages)

        val (text, isHtml) = findBody(part)
        if (text.isBlank()) return "(messaggio senza testo)" to false

        var html = if (isHtml) text
        else "<pre style=\"white-space:pre-wrap;word-wrap:break-word\">${escapeHtml(text)}</pre>"

        if (cidImages.isNotEmpty()) {
            for ((cid, dataUri) in cidImages) {
                html = html.replace("cid:$cid", dataUri, ignoreCase = true)
            }
        }
        return html to true
    }

    private fun findBody(part: Part): Pair<String, Boolean> {
        try {
            if (part.isMimeType("text/plain")) return (part.content?.toString() ?: "") to false
            if (part.isMimeType("text/html")) return (part.content?.toString() ?: "") to true
            if (part.isMimeType("multipart/*")) {
                val mp = part.content as? Multipart ?: return "" to false
                var htmlFallback: String? = null
                var plainFallback: String? = null
                for (i in 0 until mp.count) {
                    val bp = mp.getBodyPart(i)
                    val disp = bp.disposition
                    if (disp != null && disp.equals(Part.ATTACHMENT, true)) continue
                    val (t, isHtml) = findBody(bp)
                    if (t.isBlank()) continue
                    if (isHtml && htmlFallback == null) htmlFallback = t
                    if (!isHtml && plainFallback == null) plainFallback = t
                }
                if (htmlFallback != null) return htmlFallback to true
                if (plainFallback != null) return plainFallback to false
            }
        } catch (_: Exception) {
        }
        return "" to false
    }

    private fun collectInlineImages(part: Part, out: HashMap<String, String>) {
        try {
            if (part.isMimeType("multipart/*")) {
                val mp = part.content as? Multipart ?: return
                for (i in 0 until mp.count) collectInlineImages(mp.getBodyPart(i), out)
                return
            }
            val contentType = part.contentType?.lowercase() ?: ""
            if (contentType.startsWith("image/")) {
                val cidHeader = part.getHeader("Content-ID")?.firstOrNull()
                    ?: part.getHeader("Content-Id")?.firstOrNull()
                if (cidHeader != null) {
                    val cid = cidHeader.trim().removePrefix("<").removeSuffix(">")
                    val mime = contentType.substringBefore(';').trim()
                    val bytes = part.inputStream.use { it.readBytes() }
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    out[cid] = "data:$mime;base64,$b64"
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    companion object {
        private val TRASH_CANDIDATES = listOf(
            "Cestino", "Trash", "INBOX.Trash", "Deleted", "Posta eliminata", "Deleted Messages"
        )
    }
}
