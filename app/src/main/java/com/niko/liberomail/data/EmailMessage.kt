package com.niko.liberomail.data

import java.io.Serializable

/** Rappresenta un'email mostrata nella lista o aperta in dettaglio. */
data class EmailMessage(
    val uid: Long,
    val from: String,
    val subject: String,
    val dateMillis: Long,
    val seen: Boolean,
    val preview: String = "",
    val body: String = "",
    val isHtml: Boolean = false
) : Serializable
