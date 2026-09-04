package com.niko.liberomail.data

import java.io.Serializable

/** Rappresenta un'email mostrata nella lista o aperta in dettaglio. */
data class EmailMessage(
    val uid: Long,
    val contact: String,      // mittente (in arrivo) o destinatario (inviata)
    val address: String = "", // indirizzo grezzo, es. "info@example.com"
    val subject: String,
    val dateMillis: Long,
    val seen: Boolean,
    val outgoing: Boolean = false,
    val body: String = "",
    val isHtml: Boolean = false
) : Serializable {

    /** Dominio del mittente/destinatario, es. "example.com". */
    val domain: String
        get() {
            val at = address.lastIndexOf('@')
            return if (at >= 0 && at < address.length - 1) {
                address.substring(at + 1).lowercase().trim()
            } else "(sconosciuto)"
        }
}

/** Riepilogo dei messaggi raggruppati per dominio. */
data class DomainStat(
    val domain: String,
    val total: Int,
    val unread: Int
) : Serializable
