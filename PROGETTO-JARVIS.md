# PROGETTO JARVIS — riepilogo (da incollare all'inizio di ogni nuova chat)

**Obiettivo:** assistente vocale personale "Jarvis", gratuito, senza IA a pagamento né crediti consumati durante l'uso.
Si attiva con «Hey Jarvis» / «Jarvis», risponde subito «Eccomi, signore» e poi esegue i comandi.
Interfaccia HUD olografica (ciano/oro, Orbitron + Rajdhani), tutta in italiano, codice modulare.
**Telefono:** Samsung Galaxy A34 5G (circa 4 GB liberi). Lavoro tutto dal telefono: compilo con GitHub Actions e scarico l'APK da Releases.

## Stato
- [x] **Fase 1 — PWA**: v1.0 (Web Speech API, funziona solo con il sito aperto)
- [~] **Fase 2 — App Android nativa**: v2.0 scritta, **da compilare e provare sul telefono**
      Servizio in primo piano con microfono sempre attivo (anche a schermo spento), wake word offline
      openWakeWord «hey jarvis» (ONNX) oppure Vosk «Jarvis» libero, comandi offline con Vosk italiano,
      voce TTS di Android, IA locale MediaPipe (Gemma 3 1B .task) con risposte frase per frase,
      navigazione web propria (ricerca DuckDuckGo + lettura delle pagine + browser a comando vocale),
      apertura diretta delle app, chiamate/SMS/WhatsApp, timer/sveglie, note, meteo, Wikipedia, calcoli, Home Assistant.
- [ ] **Fase 3 — Casa**: Home Assistant su mini PC / Raspberry Pi (il modulo SmartHomeCmd è già pronto)
- [ ] **Fase 4 — Occhiali smart**

## Struttura codice (Fase 2) — package it.samuele.jarvis
- `JarvisApp` · `core/` {Config, Prefs, Bus, Mode} · `util/` {Text, Http}
- `audio/` {AudioInput, Sfx} · `voice/` {Speaker, wake/{WakeEngine, OpenWakeWordEngine, VoskWakeEngine}, stt/CommandListener}
- `models/ModelManager` (download Vosk/oww, importazione .task) · `ai/LocalLlm` (MediaPipe)
- `brain/` {Brain, Reply(+Utterance, Command)} + `brain/commands/` {System, Time, Timer, Notes, Device, Call, Web, SmartHome, Apps, Weather, Math, Fun, Knowledge}Cmd
- `web/` {WebAgent (cerca e legge da solo), WebController (comandi vocali del browser), BrowserActivity}
- `service/` {JarvisService (regia: WAKE→COMMAND→THINKING→SPEAKING), Notifications, BootReceiver}
- `ui/` {MainActivity (WebView HUD), HudBridge (funzioni JS→Android)}
- HUD: `assets/hud/` index.html · css/{base,hud,panels} · js/{bridge, core-visual, log, sheet, main, sheets/{setup,settings,help}}
- Build: Gradle (AGP 8.7.3, Kotlin 2.0.21, compileSdk 35, solo arm64) · `.github/workflows/build.yml` · firma fissa `app/jarvis.keystore`

## Regole di lavoro
- Codice sempre modulare: un file per responsabilità.
- Per una modifica: inviare SOLO i file coinvolti (in uno zip da caricare su GitHub), non tutto il progetto.
- Nessuna chiamata a IA a pagamento dentro Jarvis.

## Note / problemi aperti
- Primo test: verificare compilazione, «Hey Jarvis» con accento italiano (regolare la sensibilità), velocità di Gemma 1B su CPU/GPU dell'A34.
- Limiti Android: dopo un riavvio serve un tocco sulla notifica per riattivare il microfono; a telefono bloccato le app si aprono solo dopo lo sblocco.
- (scrivere qui cosa non funziona)
