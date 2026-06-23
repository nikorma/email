package com.niko.liberomail.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.niko.liberomail.data.CredentialStore
import com.niko.liberomail.mail.MailClient
import com.niko.liberomail.util.NotificationHelper
import java.util.concurrent.TimeUnit

/**
 * Controlla periodicamente la presenza di nuove email e invia una notifica
 * per ognuna. WorkManager garantisce un intervallo minimo di 15 minuti.
 */
class MailCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = CredentialStore(applicationContext)
        if (!store.isLoggedIn) return Result.success()

        return try {
            val client = MailClient(
                host = store.imapHost,
                port = store.imapPort,
                user = store.email,
                password = store.password
            )

            val lastUid = store.lastNotifiedUid
            val newer = client.fetchNewerThan(lastUid, limit = 30)

            if (newer.isNotEmpty()) {
                // Alla prima esecuzione (lastUid == 0) impostiamo solo la baseline,
                // senza inondare l'utente di notifiche per la posta già esistente.
                if (lastUid != 0L) {
                    newer.filter { !it.seen }.forEach { msg ->
                        NotificationHelper.notifyNewEmail(applicationContext, msg)
                    }
                }
                store.lastNotifiedUid = newer.maxOf { it.uid }
            }
            Result.success()
        } catch (_: Exception) {
            // Errore di rete/temporaneo: riprova al prossimo ciclo
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "controllo_email_periodico"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<MailCheckWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
