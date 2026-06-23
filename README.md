# Libero Mail – App Android (lettura, cancellazione, notifiche)

App Android nativa (Kotlin) per leggere la posta in arrivo di un account **Libero**
via IMAP, **cancellare** i messaggi (spostandoli nel Cestino) e ricevere
**notifiche** dei nuovi messaggi.

> Perché nativa e non HTML single-file come gli altri progetti?
> Una WebView non può aprire connessioni TCP grezze verso un server IMAP.
> Per parlare IMAP serve codice nativo: qui si usa la porta Android di
> JavaMail/Jakarta Mail (`com.sun.mail:android-mail`).

---

## Come compilare l'APK

1. Apri **Android Studio** (versione recente, es. Koala/Ladybug o successive).
2. `File > Open` e seleziona la cartella `LiberoMail`.
3. Attendi la sincronizzazione Gradle (scarica le dipendenze da Google Maven
   e Maven Central: serve connessione internet).
4. Collega un telefono (o usa un emulatore) e premi **Run ▶**.
   In alternativa: `Build > Build Bundle(s)/APK(s) > Build APK(s)`.

Requisiti: JDK 17 (incluso in Android Studio), Gradle 8.7 (gestito dall'IDE),
Android SDK 34. `minSdk` = 26 (Android 8.0+).

> Nota: nello zip non è incluso il file binario `gradle-wrapper.jar`.
> Android Studio usa il proprio Gradle e lo rigenera da solo.
> Se vuoi usare `./gradlew` da terminale, esegui prima una volta
> `gradle wrapper` (con Gradle installato) nella cartella del progetto.

---

## Parametri Libero usati (preimpostati)

| Parametro      | Valore                  |
|----------------|-------------------------|
| Server IMAP    | `imapmail.libero.it`    |
| Porta IMAP     | `993` (SSL)             |
| Username       | indirizzo completo, es. `nome@libero.it` |
| Password       | password dell'account Libero |

Nella schermata di login il server e la porta sono modificabili (sezione
"Parametri avanzati"), così l'app funziona anche con caselle
**Mail Personal/Business** (server `imap-biz.libero.it`).

### Se l'accesso non riesce
- Verifica di poter accedere alla **webmail** Libero con la stessa password.
- Se hai attivo il **secondo fattore (verifica in due passaggi)**, può servire
  una **password per app** generata dalle impostazioni di Libero, da usare al
  posto della password normale.
- Controlla che la casella non sia piena e che la rete funzioni.

---

## Funzionalità

- **Login** con credenziali salvate in modo cifrato
  (`EncryptedSharedPreferences`): la password resta sul telefono.
- **Posta in arrivo**: ultime 50 email, più recenti per prime, con indicatore
  "non letta" e pull-to-refresh.
- **Dettaglio messaggio**: mittente, oggetto, data e corpo (testo o HTML);
  apre marcando il messaggio come letto.
- **Elimina**: sposta il messaggio nel **Cestino** di Libero e lo toglie
  dall'inbox (fallback a cancellazione definitiva se il Cestino non è
  raggiungibile). C'è una conferma prima di eliminare.
- **Notifiche** dei nuovi messaggi tramite controllo periodico in background
  (`WorkManager`, intervallo minimo 15 minuti imposto dal sistema Android).

---

## Note tecniche e possibili evoluzioni

- **Notifiche più rapide**: WorkManager non scende sotto i 15 minuti. Per un
  push quasi istantaneo servirebbe un *foreground service* che tiene aperta una
  connessione IMAP IDLE (più impegnativo per la batteria). È l'upgrade
  naturale se 15 minuti non bastano.
- **Solo lettura/gestione**: non c'è ancora l'invio (SMTP). I parametri sono
  pronti se vuoi aggiungerlo: `smtp.libero.it`, porta `465`, SSL.
- **Risparmio energetico**: alcuni produttori (Xiaomi, Huawei, Samsung, ecc.)
  limitano i task in background. Se le notifiche non arrivano, escludi l'app
  dall'ottimizzazione batteria nelle impostazioni del telefono.
- **Cartelle**: l'app legge solo `INBOX`. Aggiungere altre cartelle è semplice
  estendendo `MailClient`.

---

## Struttura del progetto

```
app/src/main/java/com/niko/liberomail/
  LoginActivity.kt          schermata di accesso + test connessione
  InboxActivity.kt          lista posta in arrivo
  MessageActivity.kt        dettaglio + elimina
  data/CredentialStore.kt   credenziali cifrate
  data/EmailMessage.kt      modello messaggio
  mail/MailClient.kt        logica IMAP (lista, lettura, elimina)
  ui/InboxAdapter.kt        adapter RecyclerView
  work/MailCheckWorker.kt   controllo periodico + notifiche
  util/NotificationHelper.kt canale e invio notifiche
```
