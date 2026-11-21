/* Pannello "Opzioni": tutte le impostazioni di Jarvis. */
window.Sheets = window.Sheets || {};
Sheets.settings = {
  title: "OPZIONI",
  render(body, s) {
    const v = s.settings || {};
    const esc = x => String(x ?? "").replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
    const text = (k, label, hint, type = "text") => `
      <div class="field"><label>${label}</label>${hint ? `<small>${hint}</small>` : ""}
      <input type="${type}" data-k="${k}" value="${esc(v[k])}"></div>`;
    const sel = (k, label, hint, opts) => `
      <div class="field"><label>${label}</label>${hint ? `<small>${hint}</small>` : ""}
      <select data-k="${k}">${opts.map(([val, txt]) => `<option value="${val}" ${String(v[k]) === val ? "selected" : ""}>${txt}</option>`).join("")}</select></div>`;
    const tog = (k, label, hint) => `
      <div class="field"><div class="toggle"><div><label>${label}</label>${hint ? `<small>${hint}</small>` : ""}</div>
      <input type="checkbox" data-k="${k}" ${v[k] ? "checked" : ""}></div></div>`;
    const range = (k, label, hint, min, max, step) => `
      <div class="field"><label>${label}: <span id="rv-${k}">${Number(v[k]).toFixed(2)}</span></label>${hint ? `<small>${hint}</small>` : ""}
      <input type="range" data-k="${k}" min="${min}" max="${max}" step="${step}" value="${v[k]}"></div>`;

    body.innerHTML = `
      <div class="group-title">ATTIVAZIONE</div>
      ${sel("wakeEngine", "Parola di attivazione", "«Hey Jarvis» consuma pochissimo. «Jarvis» libero capisce anche «Jarvis apri YouTube» tutto di fila, ma consuma più batteria.",
        [["oww", "Hey Jarvis (consigliato)"], ["vosk", "Jarvis (ascolto libero)"]])}
      ${range("wakeThreshold", "Sensibilità «Hey Jarvis»", "Più basso = ti sente più facilmente (ma più falsi allarmi). Prova 0.35–0.5.", 0.15, 0.9, 0.05)}
      <div class="field"><label>Varianti di «Jarvis»</label><small>Per l'ascolto libero: come Vosk potrebbe scrivere il nome, separate da virgola.</small>
        <textarea data-k="wakeVariants">${esc(v.wakeVariants)}</textarea></div>
      ${sel("greeting", "Risposta all'attivazione", "", [["voce", "Voce («Eccomi, signore»)"], ["bip", "Solo un bip (più veloce)"]])}
      ${text("greetingText", "Frase di risposta", "")}
      ${tog("followUp", "Conversazione continua", "Dopo una risposta resta in ascolto per una domanda successiva senza ridire «Jarvis».")}
      ${tog("screenOnWake", "Accendi lo schermo", "Mostra l'HUD quando sente la parola di attivazione.")}

      <div class="group-title">VOCE E PERSONALITÀ</div>
      ${text("userTitle", "Come ti chiama", "es. signore, Samuele, capo")}
      ${range("ttsRate", "Velocità della voce", "", 0.6, 1.6, 0.05)}
      ${range("ttsPitch", "Tono della voce", "", 0.5, 1.5, 0.05)}
      ${text("city", "Città per il meteo", "")}

      <div class="group-title">INTELLIGENZA ARTIFICIALE LOCALE</div>
      ${tog("llmEnabled", "Usa l'IA locale", "Risponde alle domande libere e riassume le pagine web. Occupa circa 1 GB di RAM.")}
      ${sel("llmBackend", "Processore", "Prova GPU se la CPU è lenta; se l'IA non parte torna su CPU.", [["cpu", "CPU (più sicuro)"], ["gpu", "GPU (più veloce, se supportata)"]])}
      ${sel("llmFormat", "Formato del modello", "Lascia «automatico» se il file si chiama Gemma o Qwen.", [["auto", "Automatico"], ["gemma", "Gemma"], ["chatml", "Qwen / ChatML"], ["raw", "Semplice"]])}
      ${text("llmMaxTokens", "Memoria di lavoro (token)", "Deve essere ≤ al valore «ekv» nel nome del file (es. ekv1280 → 1280).", "number")}
      ${tog("webAnswers", "Cerca sul web se non sa rispondere", "")}

      <div class="group-title">CASA (HOME ASSISTANT)</div>
      ${text("haUrl", "Indirizzo", "es. http://192.168.1.50:8123", "url")}
      ${text("haToken", "Token di accesso", "Profilo › Sicurezza › Token di accesso a lunga durata", "password")}

      <button class="btn big gold" id="applyBtn">Applica</button>
    `;
    body.querySelectorAll("[data-k]").forEach(el => {
      const save = () => {
        const val = el.type === "checkbox" ? String(el.checked) : el.value;
        J.call("setSetting", el.dataset.k, val);
        const rv = document.getElementById("rv-" + el.dataset.k);
        if (rv) rv.textContent = Number(el.value).toFixed(2);
      };
      el.addEventListener(el.type === "range" ? "input" : "change", save);
    });
    body.querySelector("#applyBtn").onclick = () => { J.call("applySettings"); Sheet.close(); Log.add("sys", "Impostazioni salvate."); };
  }
};
