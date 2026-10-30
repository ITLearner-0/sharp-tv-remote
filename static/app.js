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

function render(status) {
  const connected = status.state === "connected";
  remote.hidden = !connected;
  setup.hidden = connected;
  $("#step-connect").hidden = status.state === "pairing";
  $("#step-pair").hidden = status.state !== "pairing";
  $("#setup-error").textContent = status.error || "";
  if (status.host && !$("#host").value) $("#host").value = status.host;
  if (connected) {
    $("#status-dot").classList.toggle("off", status.is_on === false);
    const app = status.current_app ? ` · ${status.current_app}` : "";
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

document.querySelectorAll("[data-app]").forEach((btn) =>
  btn.addEventListener("click", async () => {
    vibrate();
    try {
      await api("/api/app", { link: btn.dataset.app });
      toast(`Ouverture de ${btn.textContent}…`);
    } catch (err) { toast(err.message); }
  }));

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

$("#btn-settings").addEventListener("click", async () => {
  if (confirm("Se déconnecter de la TV pour en choisir une autre ?")) {
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
