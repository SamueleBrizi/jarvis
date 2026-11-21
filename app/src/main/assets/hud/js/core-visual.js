/* Il nucleo olografico animato: cambia colore e movimento in base allo stato di Jarvis. */
(function () {
  const cv = document.getElementById("core");
  const g = cv.getContext("2d");
  const W = cv.width, C = W / 2;
  const COL = {
    cyan: [61, 232, 255], gold: [255, 194, 74], grey: [110, 140, 150]
  };
  let mode = "off", level = 0, target = 0, t = 0;
  let col = COL.grey.slice(), colTarget = COL.grey;

  function rgba(c, a) { return `rgba(${c[0] | 0},${c[1] | 0},${c[2] | 0},${a})`; }

  function setMode(m) {
    mode = m;
    colTarget = (m === "thinking" || m === "speaking") ? COL.gold : (m === "off" ? COL.grey : COL.cyan);
  }
  function setLevel(v) { target = Math.max(target, v); }

  function ring(r, w, a, start, end) {
    g.beginPath(); g.lineWidth = w; g.strokeStyle = rgba(col, a);
    g.arc(C, C, r, start, end); g.stroke();
  }

  function frame() {
    t += 1 / 60;
    level += (target - level) * 0.25; target *= 0.9;
    for (let i = 0; i < 3; i++) col[i] += (colTarget[i] - col[i]) * 0.08;

    const speed = mode === "thinking" ? 3.2 : mode === "speaking" ? 1.4 : mode === "command" ? 1.0 : mode === "off" ? 0.08 : 0.35;
    let pulse = 0;
    if (mode === "speaking") pulse = 0.5 + 0.5 * Math.sin(t * 9);
    if (mode === "command") pulse = Math.min(1, level * 2.2);
    if (mode === "wake") pulse = Math.min(1, level * 1.4) * 0.6;
    const alive = mode === "off" ? 0.35 : 1;

    g.clearRect(0, 0, W, W);

    // alone
    const glow = g.createRadialGradient(C, C, 20, C, C, C);
    glow.addColorStop(0, rgba(col, 0.22 * alive + pulse * 0.15));
    glow.addColorStop(1, rgba(col, 0));
    g.fillStyle = glow; g.fillRect(0, 0, W, W);

    // tacche esterne
    g.save(); g.translate(C, C); g.rotate(t * 0.15 * speed);
    for (let i = 0; i < 90; i++) {
      const a = (i / 90) * Math.PI * 2, long = i % 5 === 0;
      g.beginPath(); g.lineWidth = long ? 3 : 1.4; g.strokeStyle = rgba(col, (long ? 0.8 : 0.35) * alive);
      g.moveTo(Math.cos(a) * 262, Math.sin(a) * 262);
      g.lineTo(Math.cos(a) * (long ? 244 : 252), Math.sin(a) * (long ? 244 : 252));
      g.stroke();
    }
    g.restore();

    // archi segmentati in rotazione
    const r1 = t * 0.6 * speed, r2 = -t * 0.9 * speed;
    for (let k = 0; k < 3; k++) ring(222, 7, 0.75 * alive, r1 + k * 2.094, r1 + k * 2.094 + 1.3);
    for (let k = 0; k < 6; k++) ring(196, 2.5, 0.55 * alive, r2 + k * 1.047, r2 + k * 1.047 + 0.6);
    ring(178, 1, 0.3 * alive, 0, Math.PI * 2);

    // anello oro interno
    const ra = t * 1.3 * speed;
    g.setLineDash([10, 8]);
    ring(150, 4, (mode === "thinking" || mode === "speaking" ? 0.95 : 0.45) * alive, ra, ra + Math.PI * 2);
    g.setLineDash([]);

    // onda del microfono
    if (mode === "command" || mode === "wake") {
      g.beginPath(); g.lineWidth = 2; g.strokeStyle = rgba(col, 0.7);
      for (let i = 0; i <= 120; i++) {
        const a = (i / 120) * Math.PI * 2;
        const r = 128 + Math.sin(a * 8 + t * 6) * 10 * level * 3 + Math.sin(a * 13 - t * 4) * 6 * level * 3;
        const x = C + Math.cos(a) * r, y = C + Math.sin(a) * r;
        i ? g.lineTo(x, y) : g.moveTo(x, y);
      }
      g.closePath(); g.stroke();
    }

    // nucleo centrale
    const core = 70 + pulse * 22;
    const cg = g.createRadialGradient(C, C, 4, C, C, core);
    cg.addColorStop(0, `rgba(255,255,255,${0.9 * alive})`);
    cg.addColorStop(0.25, rgba(col, 0.85 * alive));
    cg.addColorStop(1, rgba(col, 0));
    g.fillStyle = cg; g.beginPath(); g.arc(C, C, core, 0, Math.PI * 2); g.fill();

    // triangolo stile reattore
    g.save(); g.translate(C, C); g.rotate(-t * 0.2 * speed);
    g.beginPath(); g.lineWidth = 2; g.strokeStyle = rgba(col, 0.6 * alive);
    for (let i = 0; i < 3; i++) {
      const a = i * 2.094 - Math.PI / 2;
      i ? g.lineTo(Math.cos(a) * 98, Math.sin(a) * 98) : g.moveTo(Math.cos(a) * 98, Math.sin(a) * 98);
    }
    g.closePath(); g.stroke(); g.restore();

    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);

  window.Core = { setMode, setLevel };
})();
