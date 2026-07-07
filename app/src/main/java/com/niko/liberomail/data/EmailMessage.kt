package com.niko.liberomail.data

import java.io.Serializable

/** Rappresenta un'email mostrata nella lista o aperta in dettaglio. */
data class EmailMessage(
    val uid: Long,
    val contact: String,      // mittente (in arrivo) o destinatario (inviata)
    val subject: String,
    val dateMillis: Long,
    val seen: Boolean,
    val outgoing: Boolean = false,
    val body: String = "",
    val isHtml: Boolean = false
) : Serializable
