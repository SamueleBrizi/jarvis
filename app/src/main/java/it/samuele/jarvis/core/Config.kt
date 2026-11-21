package it.samuele.jarvis.core

/** Costanti fisse del progetto (indirizzi dei modelli, audio, ecc.). */
object Config {
    const val SAMPLE_RATE = 16000
    const val FRAME = 1280 // 80 ms di audio: il passo di openWakeWord

    // Riconoscimento vocale offline italiano (Vosk small, ~48 MB zip, ~90 MB installato)
    const val VOSK_URL = "https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip"
    const val VOSK_DIR = "vosk-it"

    // Wake word offline openWakeWord (~3 MB in tutto)
    const val OWW_BASE = "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/"
    val OWW_FILES = listOf("melspectrogram.onnx", "embedding_model.onnx", "hey_jarvis_v0.1.onnx")
    const val OWW_DIR = "oww"

    // IA locale: il file .task scelto dall'utente viene copiato qui
    const val LLM_DIR = "llm"
    const val LLM_FILE = "model.task"

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; SM-A346B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
}
