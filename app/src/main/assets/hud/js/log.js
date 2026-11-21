/* Registro della conversazione: messaggi e schede (risultati web, Wikipedia). */
(function () {
  const box = document.getElementById("log");
  const MAX = 60;

  function trim() { while (box.children.length > MAX) box.removeChild(box.firstChild); }
  function scroll() { box.scrollTop = box.scrollHeight; }

  function add(kind, text) {
    if (!text) return;
    const last = box.lastElementChild;
    if (last && last.dataset.kind === kind && last.dataset.text === text) return; // niente doppioni
    const d = document.createElement("div");
    d.className = "msg " + kind;
    d.dataset.kind = kind; d.dataset.text = text;
    d.textContent = text;
    box.appendChild(d); trim(); scroll();
  }

  function card(c) {
    const d = document.createElement("div");
    d.className = "card";
    const h = document.createElement("h4"); h.textContent = c.title || "";
    const p = document.createElement("p"); p.textContent = c.body || "";
    d.append(h, p);
    if (c.url) {
      const b = document.createElement("button");
      b.textContent = "APRI NEL BROWSER";
      b.onclick = () => J.call("openBrowser", c.url);
      d.appendChild(b);
    }
    box.appendChild(d); trim(); scroll();
  }

  function clear() { box.innerHTML = ""; }

  window.Log = { add, card, clear };
})();
