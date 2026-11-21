/* Regia dell'HUD: riceve gli eventi da Android e aggiorna l'interfaccia. */
(function () {
  const chip = document.getElementById("chip"), stateEl = document.getElementById("state");
  const partialEl = document.getElementById("partial"), mic = document.getElementById("mic");
  const form = document.getElementById("form"), input = document.getElementById("text");
  let mode = "off";

  const LABELS = {
    off: ["SPENTO", "SISTEMA SPENTO", "off"],
    loading: ["AVVIO", "CARICAMENTO SISTEMI", ""],
    idle: ["IN ATTESA", "IN ATTESA DI CONFIGURAZIONE", "off"],
    wake: ["ATTIVO", "IN ASCOLTO · «HEY JARVIS»", ""],
    command: ["ASCOLTO", "LA ASCOLTO", "gold"],
    thinking: ["ELABORO", "ELABORAZIONE", "gold"],
    speaking: ["RISPONDO", "RISPOSTA IN CORSO", "gold"]
  };

  function setMode(m, detail) {
    mode = m;
    const [c, s, cls] = LABELS[m] || LABELS.off;
    chip.textContent = c;
    chip.className = "chip " + cls;
    let label = detail || s;
    if (m === "wake") {
      const st = J.status();
      label = (st.settings && st.settings.wakeEngine === "vosk") ? "IN ASCOLTO · «JARVIS»" : s;
    }
    stateEl.textContent = label;
    stateEl.classList.toggle("gold", cls === "gold");
    mic.classList.toggle("live", m === "command");
    Core.setMode(m);
    if (m !== "command") partialEl.textContent = "";
  }

  // eventi dal servizio Android
  window.JarvisHUD = {
    onEvent(e) {
      switch (e.type) {
        case "state": setMode(e.mode, e.detail); break;
        case "log": Log.add(e.kind, e.text); break;
        case "card": Log.card(e); break;
        case "partial": partialEl.textContent = e.text ? "« " + e.text + " »" : ""; break;
        case "level": Core.setLevel(e.v); break;
        case "progress": Sheet.progress(e); if (e.error) Log.add("err", e.label + ": " + e.error); break;
        case "llm": case "status": Sheet.refresh(); break;
      }
    }
  };

  // pulsanti
  mic.onclick = () => {
    if (mode === "speaking" || mode === "thinking") { J.call("hush"); return; }
    const st = J.status();
    if (!st.models.vosk) { Sheet.open("setup"); return; }
    J.call("listen");
  };
  form.onsubmit = ev => {
    ev.preventDefault();
    const t = input.value.trim();
    if (!t) return;
    input.value = ""; input.blur();
    J.call("sendText", t);
  };
  document.querySelectorAll("[data-open]").forEach(b => b.onclick = () => Sheet.open(b.dataset.open));
  document.querySelector('[data-act="web"]').onclick = () => J.call("openBrowser", "");

  // orologio
  const clock = document.getElementById("clock");
  const tick = () => { const d = new Date(); clock.textContent = d.toLocaleTimeString("it-IT", { hour: "2-digit", minute: "2-digit" }); };
  tick(); setInterval(tick, 10000);

  // avvio
  const st = J.status();
  document.getElementById("version").textContent = "FASE 2 · v" + (st.version || "");
  setMode(st.running ? st.mode : "off");
  if (!st.perms.mic || !st.models.vosk) {
    Log.add("sys", "Benvenuto, signore. Completiamo la configurazione: servono pochi minuti.");
    setTimeout(() => Sheet.open("setup"), 600);
  }
  J.call("ready");
})();
