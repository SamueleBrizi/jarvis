/* Ponte con l'app Android (window.Android). Nel browser del PC usa una finta versione per provare la grafica. */
(function () {
  const mock = {
    ready() {}, getStatus() {
      return JSON.stringify({
        running: false, mode: "off", downloading: false, version: "anteprima",
        models: { vosk: false, oww: false, llm: false, llmName: "", llmMb: 0, voskMb: 0, freeMb: 3900 },
        perms: { mic: false, contacts: false, call: false, notif: false, overlay: false, battery: false },
        llm: { state: "none", error: "" }, settings: {}
      });
    },
    setSetting() {}, applySettings() {}, start() {}, stop() {}, listen() {}, sendText(t) {
      setTimeout(() => JarvisHUD.onEvent({ type: "log", kind: "jarvis", text: "Anteprima: «" + t + "»" }), 300);
    },
    hush() {}, downloadModels() {}, importLlm() {}, deleteLlm() {}, requestPerms() {}, openOverlay() {},
    openBattery() {}, openBrowser() {}, openExternal(u) { window.open(u); }, notes() { return "[]"; }
  };
  const A = window.Android || mock;

  window.J = {
    call(name, ...args) {
      try { return A[name] ? A[name](...args) : undefined; } catch (e) { console.error(name, e); }
    },
    status() {
      try { return JSON.parse(A.getStatus()); } catch (e) { return JSON.parse(mock.getStatus()); }
    }
  };
})();
