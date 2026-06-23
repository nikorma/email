# Ottenere l'APK senza Android Studio (build in cloud)

Questo progetto include un workflow GitHub Actions che compila l'APK
automaticamente sui server di GitHub. Tu non installi nulla: scarichi solo
il file `.apk` finito.

## Passaggi

1. Crea un account su https://github.com (gratuito) se non ne hai uno.
2. Crea un nuovo repository (può essere **privato**): pulsante **New**.
3. Carica il contenuto della cartella `LiberoMail` nel repository:
   - Via web: nella pagina del repo, **Add file > Upload files**, trascina
     tutti i file/cartelle del progetto e fai **Commit**.
   - Oppure via git da terminale, se preferisci.
   Importante: carica i file **mantenendo le cartelle** (deve restare la
   struttura con `app/`, `gradle/`, `build.gradle.kts`, `.github/`, ecc.).
4. Vai sulla scheda **Actions** del repository. Vedrai il workflow
   "Compila APK" partire da solo dopo il caricamento (oppure premi
   **Run workflow**).
5. Attendi il pallino verde (circa 3–6 minuti la prima volta).
6. Apri la run completata, scorri in fondo alla pagina fino a **Artifacts**
   e scarica **LiberoMail-debug-apk** (è uno zip che contiene `app-debug.apk`).

## Installare l'APK sul telefono

1. Trasferisci `app-debug.apk` sul telefono Android.
2. Aprilo: Android chiederà di consentire l'installazione da
   "origini sconosciute" per il file manager/browser usato. Concedilo.
3. Conferma l'installazione.

> È un APK **debug**, perfetto per uso personale (sideload). Non è firmato
> per il Play Store, ma si installa e funziona senza problemi sul tuo
> telefono.

## In alternativa: Android Studio (un clic)

Se preferisci, apri la cartella in Android Studio e premi **Run ▶** con il
telefono collegato, oppure `Build > Build APK(s)`. È il metodo più diretto se
hai già l'IDE installato. Vedi `README.md`.
