package com.niko.liberomail.data

/** Parametri IMAP/SMTP preconfigurati per i provider più comuni. */
data class Provider(
    val name: String,
    val imapHost: String,
    val imapPort: Int,
    val smtpHost: String,
    val smtpPort: Int,
    /** Domini che attivano la selezione automatica in base all'indirizzo digitato. */
    val domains: List<String> = emptyList(),
    val note: String? = null
) {
    companion object {
        val LIBERO = Provider(
            "Libero", "imapmail.libero.it", 993, "smtp.libero.it", 465,
            listOf("libero.it", "inwind.it", "iol.it", "blu.it", "giallo.it")
        )

        val ALL: List<Provider> = listOf(
            LIBERO,
            Provider(
                "Libero Mail Business", "imap-biz.libero.it", 993, "smtp-biz.libero.it", 465
            ),
            Provider(
                "Gmail", "imap.gmail.com", 993, "smtp.gmail.com", 465,
                listOf("gmail.com", "googlemail.com"),
                "Gmail richiede una \"password per le app\": generala nelle impostazioni di sicurezza del tuo account Google e usala al posto della password normale."
            ),
            Provider(
                "Outlook / Hotmail", "outlook.office365.com", 993, "smtp.office365.com", 587,
                listOf("outlook.it", "outlook.com", "hotmail.it", "hotmail.com", "live.it", "live.com", "msn.com"),
                "Microsoft sta disattivando l'accesso con sola password: potrebbe servire una password per le app, o l'accesso IMAP potrebbe non essere disponibile sul tuo account."
            ),
            Provider(
                "Yahoo", "imap.mail.yahoo.com", 993, "smtp.mail.yahoo.com", 465,
                listOf("yahoo.it", "yahoo.com"),
                "Yahoo richiede una \"password per le app\" generata dalle impostazioni account."
            ),
            Provider(
                "Aruba", "imaps.aruba.it", 993, "smtps.aruba.it", 465,
                listOf("aruba.it", "pec.it")
            ),
            Provider(
                "Tiscali", "imap.tiscali.it", 993, "smtp.tiscali.it", 465,
                listOf("tiscali.it")
            ),
            Provider(
                "Virgilio / Alice", "in.virgilio.it", 993, "out.virgilio.it", 465,
                listOf("virgilio.it", "alice.it", "tin.it")
            ),
            Provider(
                "Fastweb", "imap.fastwebnet.it", 993, "smtp.fastwebnet.it", 465,
                listOf("fastwebnet.it")
            ),
            Provider(
                "iCloud", "imap.mail.me.com", 993, "smtp.mail.me.com", 587,
                listOf("icloud.com", "me.com", "mac.com"),
                "iCloud richiede una \"password per le app\" generata da appleid.apple.com."
            ),
            Provider(
                "Altro (inserisco i parametri)", "", 993, "", 465
            )
        )

        /** Trova il provider in base al dominio dell'indirizzo, se riconosciuto. */
        fun forEmail(email: String): Provider? {
            val at = email.lastIndexOf('@')
            if (at < 0 || at >= email.length - 1) return null
            val domain = email.substring(at + 1).lowercase().trim()
            return ALL.firstOrNull { p -> p.domains.any { it == domain } }
        }
    }
}
