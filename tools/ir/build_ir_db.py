#!/usr/bin/env python3
"""Construit la base de codes infrarouges de l'application Android.

Sources :
  * flipper-cc0/*.ir : fichiers Flipper-IRDB placés sous licence CC0 (voir SOURCES.md) ;
  * handwritten.json : jeux de codes écrits à la main d'après les protocoles publics.

Sortie : android/app/src/main/assets/ir_codes.json

    python3 tools/ir/build_ir_db.py
"""

from __future__ import annotations

import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUTPUT = HERE.parent.parent / "android" / "app" / "src" / "main" / "assets" / "ir_codes.json"

SUPPORTED_PROTOCOLS = {
    "NEC", "NECext", "Samsung32", "RC5", "RC5X", "RC6", "SIRC", "SIRC15", "SIRC20", "Panasonic", "Sharp",
}

# Noms de dossiers Flipper → marque affichée (None : ignoré).
BRANDS = {
    "ContinentalEdison": "Continental Edison",
    "DuraBrand": "DuraBrand",
    "Hotels": None,
}

# Noms de touches Flipper (normalisés) → touche de l'application.
KEY_ALIASES = {
    "power": "POWER", "power_off": "POWER_OFF", "power_on": "POWER_ON",
    "vol_up": "VOL_UP", "volup": "VOL_UP", "vol_+": "VOL_UP", "volume_up": "VOL_UP",
    "vol_dn": "VOL_DOWN", "vol_down": "VOL_DOWN", "voldown": "VOL_DOWN", "volume_down": "VOL_DOWN",
    "mute": "MUTE",
    "ch_next": "CH_UP", "ch_up": "CH_UP", "chup": "CH_UP",
    "ch_prev": "CH_DOWN", "ch_dn": "CH_DOWN", "ch_down": "CH_DOWN", "chdown": "CH_DOWN",
    "up": "UP", "down": "DOWN", "left": "LEFT", "right": "RIGHT",
    "ok": "OK", "enter": "OK", "select": "OK",
    "back": "BACK", "return": "BACK", "exit": "EXIT",
    "home": "HOME", "menu": "MENU", "settings": "SETTINGS", "set-up": "SETTINGS", "quick_menu": "SETTINGS",
    "input": "INPUT", "source": "INPUT", "av": "INPUT",
    "info": "INFO", "display": "INFO",
    "guide": "GUIDE", "epg": "GUIDE",
    "subtitle": "SUBTITLE", "caption": "SUBTITLE", "captions": "SUBTITLE", "ad_subt": "SUBTITLE",
    "pre_ch": "PREV_CH", "recall": "PREV_CH",
    "play": "PLAY", "pause": "PAUSE", "play_pause": "PLAY_PAUSE", "stop": "STOP",
    "rew": "REWIND", "rewind": "REWIND", "fast_ba": "REWIND", "rw": "REWIND",
    "ff": "FFWD", "fwd": "FFWD", "fast_fwd": "FFWD", "fast_fo": "FFWD",
    "red": "RED", "green": "GREEN", "yellow": "YELLOW", "blue": "BLUE",
}

# En cas de doublon, la première forme rencontrée dans cette liste l'emporte.
PREFERRED = ["ok", "back", "ch_next", "ch_prev", "input"]


def normalize(name: str) -> str | None:
    key = re.sub(r"\s+", "_", name.strip().lower())
    if re.fullmatch(r"\d", key):
        return f"DIGIT_{key}"
    return KEY_ALIASES.get(key)


def parse_flipper(path: Path) -> list[dict]:
    """Lit un fichier .ir Flipper : liste de signaux {name, protocol, address, command} ou bruts."""
    signals, current = [], {}
    for line in path.read_text("utf-8", errors="replace").splitlines():
        line = line.strip()
        if line.startswith("#") or ":" not in line:
            continue
        field, value = (part.strip() for part in line.split(":", 1))
        if field == "name":
            if current:
                signals.append(current)
            current = {"name": value}
        elif field in ("type", "protocol", "frequency", "duty_cycle"):
            current[field] = value
        elif field in ("address", "command"):
            current[field] = [int(b, 16) for b in value.split()]
        elif field == "data":
            current["data"] = [int(v) for v in value.split()]
    if current:
        signals.append(current)
    return signals


def le_int(data: list[int], size: int) -> int:
    return sum(b << (8 * i) for i, b in enumerate(data[:size]))


def to_code(signal: dict) -> dict | None:
    if signal.get("type") == "raw":
        data = signal.get("data")
        if not data:
            return None
        return {"raw": data, "frequency": int(signal.get("frequency", 38000))}
    protocol = signal.get("protocol")
    if protocol not in SUPPORTED_PROTOCOLS:
        return None
    wide = protocol in ("NECext", "SIRC20")
    return {
        "protocol": protocol,
        "address": le_int(signal["address"], 2 if wide else 1),
        "command": le_int(signal["command"], 2 if protocol == "NECext" else 1),
    }


def model_name(folder: str, model: str) -> str:
    """« ContinentalEdison_CELD55 » → « CELD55 » : le nom de marque est déjà affiché."""
    name = re.sub(rf"^{re.escape(folder)}[_ ]*", "", model, flags=re.IGNORECASE)
    return name.replace("_", " ") or model


def flipper_codesets() -> list[dict]:
    codesets = []
    for path in sorted((HERE / "flipper-cc0").glob("*.ir")):
        folder, _, model = path.stem.partition("__")
        brand = BRANDS.get(folder, folder)
        if brand is None:
            continue
        keys, priority = {}, {}
        for signal in parse_flipper(path):
            key = normalize(signal["name"])
            code = to_code(signal)
            if key is None or code is None:
                continue
            rank = PREFERRED.index(signal["name"].lower()) if signal["name"].lower() in PREFERRED else 99
            if key not in keys or rank < priority[key]:
                keys[key], priority[key] = code, rank
        if "POWER" in keys or "VOL_UP" in keys:
            codesets.append({
                "brand": brand,
                "name": f"{brand} ({model_name(folder, model)})",
                "source": "Flipper-IRDB (CC0)",
                "verified": True,
                "keys": keys,
            })
    return codesets


def handwritten_codesets() -> list[dict]:
    data = json.loads((HERE / "handwritten.json").read_text("utf-8"))
    codesets = []
    for entry in data["codesets"]:
        keys = {
            name: {"protocol": entry["protocol"], "address": entry["address"], "command": command}
            for name, command in entry["keys"].items()
        }
        keys.update(entry.get("extra", {}))
        codesets.append({
            "brand": entry["brand"],
            "name": entry["name"],
            "source": "Écrit à la main",
            "verified": entry.get("verified", False),
            "keys": keys,
        })
    return codesets


def main() -> None:
    # Jeux écrits à la main d'abord : ils couvrent le plus de touches.
    codesets = handwritten_codesets() + flipper_codesets()
    for i, codeset in enumerate(codesets):
        codeset["id"] = re.sub(r"[^a-z0-9]+", "-", codeset["name"].lower()).strip("-") + f"-{i}"
    brands = sorted({c["brand"] for c in codesets}, key=str.lower)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps({"brands": brands, "codesets": codesets}, ensure_ascii=False, indent=1), "utf-8")
    print(f"{len(codesets)} jeux de codes, {len(brands)} marques → {OUTPUT}")
    for c in codesets:
        print(f"  {c['name']:<48} {len(c['keys']):>3} touches  {c['source']}")


if __name__ == "__main__":
    main()
