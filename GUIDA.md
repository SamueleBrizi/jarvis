# Jarvis Fase 2 · come installarlo solo dal telefono

## A. Prima volta (10 minuti)
1. Vai su **github.com** dal browser del telefono, crea un account (gratis) e accedi.
2. Tocca **+ → New repository**. Nome: `jarvis`, scegli **Private**, poi **Create repository**.
3. Nel repository vuoto tocca **creating a new file**, oppure **Add file → Create new file**.
   - Come nome scrivi esattamente: `.github/workflows/build.yml`
   - Incolla tutto il contenuto del file **build.yml** e premi **Commit changes**.
4. Tocca **Add file → Upload files**, scegli **jarvis-android.zip** e premi **Commit changes**.
5. Apri la scheda **Actions**: parte "Compila Jarvis" (circa 6-8 minuti). Quando finisce compare la spunta verde ✓.
6. Torna alla pagina del repository, apri **Releases** sulla destra (oppure in fondo alla pagina) e scarica **Jarvis-2.0.x.apk**.
7. Apri l'APK. Samsung ti chiede di autorizzare le app sconosciute dal browser: accetta e installa.

## B. Nell'app
Il pannello **Setup** ti guida passo per passo:
1. Permessi → Consenti (microfono, notifiche, contatti, telefono)
2. Voce offline → Scarica (circa 50 MB)
3. Batteria → Consenti. Poi vai in *Impostazioni › Batteria › Limiti utilizzo in background*: Jarvis non deve stare tra le app in sospensione.
4. Mostra sopra le altre app → attiva Jarvis
5. IA locale (facoltativa): su Hugging Face accetti la licenza di Gemma, scarichi il file `.task` q4 (~550 MB) e lo importi.
   Dopo l'importazione cancella il file da *Download*.
6. **Avvia Jarvis** → di' «Hey Jarvis».

## C. Aggiornamenti
Quando ti mando una modifica ricevi uno zip con **solo i file cambiati**.
Caricalo con *Add file → Upload files*: il workflow lo spacchetta da solo e pubblica una nuova Release.
Scarica il nuovo APK e installalo sopra quello vecchio: impostazioni e modelli restano.

## D. Se la compilazione fallisce
Apri *Actions → l'esecuzione rossa → apk → Compila APK*, copia le ultime righe rosse e incollale nella chat.
