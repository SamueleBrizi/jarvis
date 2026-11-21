/* Gestione dei pannelli che salgono dal basso. */
(function () {
  const sheet = document.getElementById("sheet"), bg = document.getElementById("sheetBg");
  const title = document.getElementById("sheetTitle"), body = document.getElementById("sheetBody");
  let current = null;

  function open(name) {
    const s = Sheets[name]; if (!s) return;
    current = name;
    title.textContent = s.title;
    s.render(body, J.status());
    body.scrollTop = 0;
    sheet.classList.add("open"); bg.classList.add("open");
    sheet.setAttribute("aria-hidden", "false");
  }
  function close() {
    current = null;
    sheet.classList.remove("open"); bg.classList.remove("open");
    sheet.setAttribute("aria-hidden", "true");
  }
  /** ridisegna il pannello aperto (es. dopo un download) senza perdere la posizione */
  function refresh() {
    if (!current || current === "settings") return;
    const y = body.scrollTop;
    Sheets[current].render(body, J.status());
    body.scrollTop = y;
  }
  function progress(e) { if (current === "setup") Sheets.setup.progress(e); }

  bg.onclick = close;
  document.getElementById("sheetClose").onclick = close;
  window.Sheet = { open, close, refresh, progress, get current() { return current; } };
})();
