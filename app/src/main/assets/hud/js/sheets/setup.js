/* Pannello "Setup": permessi, modelli offline, batteria, IA locale, avvio. */
window.Sheets = window.Sheets || {};
Sheets.setup = {
  title: "CONFIGURAZIONE",
  render(body, s) {
    const m = s.models, p = s.perms;
    const step = (ok, title, desc, actions) => `
      <div class="step ${ok ? "ok" : ""}">
        <div class="step-dot">${ok ? "✓" : ""}</div>
        <div class="step-main">
          <div class="step-title">${title}</div>
          <div class="step-desc">${desc}</div>
          ${actions ? `<div class="step-actions">${actions}</div>` : ""}
        </div>
      </div>`;
    const llmState = { none: "non caricata", loading: "caricamento…", ready: "attiva", error: "errore" }[s.llm.state] || s.llm.state;

    body.innerHTML = `
      <div class="space">SPAZIO LIBERO: ${(m.freeMb / 1024).toFixed(1)} GB · VERSIONE ${s.version || ""}</div>

      ${step(p.mic && p.notif, "1 · Permessi",
        "Microfono e notifiche sono indispensabili. Contatti e telefono servono per «chiama…» e «scrivi a…».",
        `<button class="btn" data-a="requestPerms">Concedi permessi</button>`)}

      ${step(m.vosk && m.oww, "2 · Voce offline (circa 50 MB)",
        `Riconoscimento vocale italiano (Vosk) e parola «Hey Jarvis». Occupano circa 95 MB. ${m.vosk ? "Installata: " + m.voskMb + " MB." : "Meglio con il Wi-Fi."}`,
        `${m.vosk && m.oww ? "" : `<button class="btn" data-a="downloadModels" ${s.downloading ? "disabled" : ""}>Scarica</button>`}
         <div style="width:100%"><div class="bar"><i id="dlbar"></i></div><div class="bar-label" id="dllabel"></div></div>`)}

      ${step(p.battery, "3 · Batteria senza restrizioni",
        "Così il Samsung non spegne Jarvis a schermo spento. Scegli «Consenti». Poi in Impostazioni › Batteria › Limiti utilizzo in background, togli Jarvis dalle app in sospensione.",
        `<button class="btn" data-a="openBattery">Apri</button>`)}

      ${step(p.overlay, "4 · Mostra sopra le altre app",
        "Permette a Jarvis di aprire app e browser anche mentre stai usando altro o dalla home.",
        `<button class="btn" data-a="openOverlay">Apri</button>`)}

      ${step(m.llm, "5 · IA locale (facoltativa, circa 550 MB)",
        m.llm
          ? `Modello: <b>${m.llmName || "modello"}</b> (${m.llmMb} MB) · stato: ${llmState}${s.llm.error ? " — " + s.llm.error : ""}`
          : `Per conversare e riassumere il web. Su Hugging Face accedi, accetta la licenza di Gemma e scarica il file <b>.task</b> «Gemma3-1B-IT … q4» (circa 550 MB). Poi premi «Importa» e sceglilo dai Download. Dopo l'importazione cancella il file da Download per recuperare spazio.`,
        `${m.llm ? "" : `<button class="btn gold" data-a="hf">Apri Hugging Face</button>`}
         <button class="btn gold" data-a="importLlm">${m.llm ? "Sostituisci" : "Importa .task"}</button>
         ${m.llm ? `<button class="btn red" data-a="deleteLlm">Elimina</button>` : ""}`)}

      <button class="btn big ${s.running ? "red" : "gold"}" data-a="${s.running ? "stop" : "start"}">
        ${s.running ? "Spegni Jarvis" : "Avvia Jarvis"}
      </button>
      <p class="note">Quando Jarvis è attivo vedi una notifica fissa: è obbligatoria su Android per usare il microfono in background. Dopo un riavvio del telefono basta toccare la notifica «Riattiva Jarvis».</p>
    `;
    body.querySelectorAll("[data-a]").forEach(b => b.onclick = () => {
      const a = b.dataset.a;
      if (a === "hf") return J.call("openExternal", "https://huggingface.co/litert-community/Gemma3-1B-IT");
      if (a === "deleteLlm" && !confirm("Eliminare il modello IA?")) return;
      J.call(a);
      if (a === "downloadModels") b.disabled = true;
      setTimeout(() => Sheet.refresh(), 1200);
    });
  },
  progress(e) {
    const bar = document.getElementById("dlbar"), lab = document.getElementById("dllabel");
    if (!bar || !lab) return;
    bar.style.width = (e.pct || 0) + "%";
    lab.textContent = e.error ? e.label + ": " + e.error : e.label + (e.done ? "" : " · " + e.pct + "%");
    lab.classList.toggle("err", !!e.error);
    if (e.done) setTimeout(() => Sheet.refresh(), 600);
  }
};
