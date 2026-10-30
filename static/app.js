"use strict";

const $ = (sel) => document.querySelector(sel);
const setup = $("#setup");
const remote = $("#remote");

async function api(path, body) {
  const opts = body === undefined
    ? {}
    : { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) };
  const res = await fetch(path, opts);
  if (!res.ok) throw new Error((await res.text()) || res.statusText);
  return res.json();
}

let toastTimer;
function toast(msg) {
  const el = $("#toast");
  el.textContent = msg;
  el.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove("show"), 2200);
}

let lastStatus = {};
function render(status) {
  lastStatus = status;
  const connected = status.state === "connected";
  const offline = status.state === "offline";
  remote.hidden = !(connected || offline);
  setup.hidden = !remote.hidden;
  $("#step-connect").hidden = status.state === "pairing";
  $("#step-pair").hidden = status.state !== "pairing";
  $("#setup-error").textContent = status.error || "";
  if (status.host && !$("#host").value) $("#host").value = status.host;
  remote.classList.toggle("offline", offline);
  if (offline) {
    $("#status-dot").classList.add("off");
    $("#status-text").textContent = "TV éteinte · ⏻ pour l'allumer";
  }
  if (connected) {
    $("#status-dot").classList.toggle("off", status.is_on === false);
    const app = status.is_on === false ? " · en veille" : status.current_app ? ` · ${status.current_app}` : "";
    $("#status-text").textContent = `${status.device?.manufacturer || "TV"} ${status.device?.model || ""}${app}`.trim();
  }
}

async function refresh() {
  try {
    render(await api("/api/status"));
  } catch {
    $("#status-text").textContent = "Serveur injoignable";
    $("#status-dot").classList.add("off");
  }
}

function vibrate() {
  if (navigator.vibrate) navigator.vibrate(15);
}

async function sendKey(key, direction = "SHORT") {
  try {
    await api("/api/key", { key, direction });
  } catch (err) {
    toast(err.message);
    refresh();
  }
}

// Touches : appui court, ou répétition si maintenue (volume, flèches…)
document.querySelectorAll("[data-key]").forEach((btn) => {
  const key = btn.dataset.key;
  let repeatTimer, delayTimer;
  const stop = () => { clearTimeout(delayTimer); clearInterval(repeatTimer); };

  btn.addEventListener("pointerdown", (e) => {
    e.preventDefault();
    vibrate();
    btn.classList.add("pressed");
    sendKey(key);
    if (btn.hasAttribute("data-repeat")) {
      delayTimer = setTimeout(() => { repeatTimer = setInterval(() => sendKey(key), 120); }, 400);
    }
  });
  ["pointerup", "pointerleave", "pointercancel"].forEach((ev) =>
    btn.addEventListener(ev, () => { stop(); btn.classList.remove("pressed"); }));
  btn.addEventListener("contextmenu", (e) => e.preventDefault());
});

// Applications : liste lue sur la TV (ADB) ou retenue au fil de l'utilisation
function renderApps({ apps, complete }) {
  const grid = $("#apps");
  grid.replaceChildren(...apps.map(({ package: pkg, label }) => {
    const b = document.createElement("button");
    b.className = "app";
    b.dataset.app = pkg;
    b.title = pkg;
    const badge = document.createElement("span");
    badge.className = "badge";
    badge.textContent = label.charAt(0).toUpperCase();
    badge.style.background = `hsl(${[...pkg].reduce((h, c) => h + c.charCodeAt(0), 0) % 360} 55% 42%)`;
    const name = document.createElement("span");
    name.textContent = label;
    b.append(badge, name);
    return b;
  }));
  $("#apps-hint").textContent = complete
    ? `${apps.length} applications trouvées sur la TV.`
    : "Seules les applications courantes et celles déjà ouvertes sur la TV s'affichent. "
      + "Pour toutes les voir, activez le débogage USB sur la TV (voir le README), puis touchez le bouton ci-dessus.";
}

async function loadApps() {
  try { renderApps(await api("/api/apps")); } catch { /* serveur injoignable */ }
}

$("#apps").addEventListener("click", async (e) => {
  const btn = e.target.closest("[data-app]");
  if (!btn) return;
  vibrate();
  try {
    await api("/api/app", { link: btn.dataset.app });
    toast(`Ouverture de ${btn.textContent.slice(1)}…`);
  } catch (err) { toast(err.message); }
});

$("#apps-drawer").addEventListener("toggle", (e) => { if (e.target.open) loadApps(); });

$("#btn-scan-apps").addEventListener("click", async () => {
  const btn = $("#btn-scan-apps");
  btn.disabled = true;
  btn.textContent = "Lecture en cours…";
  $("#apps-hint").textContent = "Si la TV demande « Autoriser le débogage ? », cochez « Toujours autoriser » "
    + "puis choisissez OK avec les flèches de cette télécommande (vous avez 1 minute).";
  try {
    renderApps(await api("/api/apps/scan", {}));
  } catch (err) {
    $("#apps-hint").textContent = err.message;
  } finally {
    btn.disabled = false;
    btn.textContent = "🔄 Lister toutes les applications de la TV";
  }
});

$("#text-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const text = $("#text").value;
  if (!text) return;
  try {
    await api("/api/text", { text });
    $("#text").value = "";
  } catch (err) { toast(err.message); }
});

// Connexion / appairage
$("#btn-discover").addEventListener("click", async () => {
  const list = $("#tv-list");
  list.innerHTML = "<li class='hint'>Recherche en cours…</li>";
  try {
    const tvs = await api("/api/discover");
    list.innerHTML = "";
    if (!tvs.length) list.innerHTML = "<li class='hint'>Aucune TV trouvée. Saisissez l'IP ci-dessous.</li>";
    tvs.forEach((tv) => {
      const li = document.createElement("li");
      const b = document.createElement("button");
      b.className = "wide";
      b.textContent = `📺 ${tv.name} (${tv.host})`;
      b.addEventListener("click", () => { $("#host").value = tv.host; connect(); });
      li.appendChild(b);
      list.appendChild(li);
    });
  } catch (err) { list.innerHTML = ""; toast(err.message); }
});

async function connect() {
  const host = $("#host").value.trim();
  if (!host) return toast("Saisissez l'adresse IP de la TV");
  $("#btn-connect").disabled = true;
  $("#btn-connect").textContent = "Connexion…";
  try {
    render(await api("/api/connect", { host }));
  } catch (err) {
    $("#setup-error").textContent = err.message;
  } finally {
    $("#btn-connect").disabled = false;
    $("#btn-connect").textContent = "Se connecter";
  }
}
$("#btn-connect").addEventListener("click", connect);
$("#host").addEventListener("keydown", (e) => { if (e.key === "Enter") connect(); });

$("#btn-pair").addEventListener("click", async () => {
  try {
    render(await api("/api/pair", { code: $("#code").value }));
    $("#code").value = "";
  } catch (err) { $("#setup-error").textContent = err.message; }
});

$("#btn-cancel").addEventListener("click", async () => render(await api("/api/disconnect", {})));

// Marche / arrêt : réveille la TV (Wake-on-LAN) si elle est éteinte
$("#btn-power").addEventListener("click", async () => {
  vibrate();
  try {
    const { action } = await api("/api/power", {});
    if (action === "on") toast("Allumage de la TV…");
  } catch (err) { toast(err.message); }
  setTimeout(refresh, 1500);
});

// Réglages : adresse MAC et changement de TV
const settings = $("#settings");
$("#btn-settings").addEventListener("click", () => {
  $("#settings-tv").textContent = lastStatus.host ? `TV : ${lastStatus.host}` : "";
  $("#mac").value = lastStatus.mac || "";
  settings.showModal();
});

$("#btn-save-mac").addEventListener("click", async () => {
  try {
    render(await api("/api/mac", { mac: $("#mac").value }));
    settings.close();
    toast("Adresse MAC enregistrée");
  } catch (err) { toast(err.message); }
});

$("#btn-forget").addEventListener("click", async () => {
  if (confirm("Oublier cette TV pour en choisir une autre ?")) {
    settings.close();
    render(await api("/api/disconnect", {}));
  }
});

// Clavier physique (ordinateur) → touches de la télécommande
const keyboardMap = {
  ArrowUp: "KEYCODE_DPAD_UP", ArrowDown: "KEYCODE_DPAD_DOWN",
  ArrowLeft: "KEYCODE_DPAD_LEFT", ArrowRight: "KEYCODE_DPAD_RIGHT",
  Enter: "KEYCODE_DPAD_CENTER", Escape: "KEYCODE_BACK", Backspace: "KEYCODE_BACK",
  h: "KEYCODE_HOME", "+": "KEYCODE_VOLUME_UP", "-": "KEYCODE_VOLUME_DOWN",
  m: "KEYCODE_VOLUME_MUTE", " ": "KEYCODE_MEDIA_PLAY_PAUSE",
};
document.addEventListener("keydown", (e) => {
  if (remote.hidden || e.target.tagName === "INPUT") return;
  const key = keyboardMap[e.key];
  if (key) { e.preventDefault(); sendKey(key); }
});

refresh();
setInterval(refresh, 5000);
