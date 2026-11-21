/* Pannello "Comandi": esempi di cosa si può dire. */
window.Sheets = window.Sheets || {};
Sheets.help = {
  title: "COMANDI",
  render(body) {
    const groups = [
      ["TELEFONO", ["Apri WhatsApp / YouTube / Spotify", "Chiama mamma", "Scrivi su WhatsApp a Marco che arrivo tra 10 minuti", "Manda un messaggio a Luca dicendo che ritardo", "Accendi la torcia", "Quanta batteria ho?", "Alza il volume · Volume al 30 percento", "Apri il Wi-Fi · Apri la fotocamera"]],
      ["TEMPO", ["Che ore sono? · Che giorno è?", "Imposta un timer di 5 minuti", "Svegliami alle 7 e mezza", "Mostra le sveglie"]],
      ["NOTE", ["Prendi nota: comprare il latte", "Leggi le note", "Cancella l'ultima nota"]],
      ["WEB", ["Cerca su internet ricette con le zucchine", "Cerca le ultime notizie", "Apri il risultato · Aprilo", "Apri il sito repubblica punto it", "Mostrami foto di Civitavecchia", "Nel browser: scorri giù · indietro · leggi la pagina · riassumi · apri il secondo risultato · chiudi il browser"]],
      ["INFORMAZIONI", ["Che tempo fa a Milano domani?", "Chi è Leonardo da Vinci?", "Quanto fa 15 per 23?", "Il 20 percento di 80"]],
      ["IA LOCALE", ["Cosa posso cucinare con uova e patate?", "Dammi un consiglio per dormire meglio", "Chiedi all'intelligenza artificiale…", "Nuova conversazione"]],
      ["CASA", ["Accendi la luce del salotto", "Spegni la presa della cucina", "Chiudi la tapparella"]],
      ["JARVIS", ["Stop · Basta", "Ripeti", "Cosa sai fare?", "Spegniti"]]
    ];
    body.innerHTML = `<p class="note">Di' <b>«Hey Jarvis»</b>, aspetta «Eccomi, signore», poi il comando. Oppure tocca il microfono o scrivi.</p>` +
      groups.map(([t, items]) => `<div class="help-group"><h3>${t}</h3><ul>${items.map(i => `<li>${i}</li>`).join("")}</ul></div>`).join("");
  }
};
