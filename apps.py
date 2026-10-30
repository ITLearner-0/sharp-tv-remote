"""Catalogue des applications de la TV.

Le protocole de télécommande ne sait pas lister les applications installées.
Deux sources les alimentent donc :

* la liste complète, lue par ADB (débogage) quand il est activé sur la TV ;
* les applications ouvertes sur la TV, retenues automatiquement au fil de l'eau.

Une application se lance ensuite par son nom de paquet via la télécommande.
"""

from __future__ import annotations

import json
import logging
import re
from pathlib import Path

LOGGER = logging.getLogger("sharp-remote")

# Noms lisibles des applications courantes (le reste est déduit du paquet).
KNOWN_APPS = {
    "com.netflix.ninja": "Netflix",
    "com.google.android.youtube.tv": "YouTube",
    "com.google.android.youtube.tvkids": "YouTube Kids",
    "com.google.android.youtube.tvmusic": "YouTube Music",
    "com.amazon.amazonvideo.livingroom": "Prime Video",
    "com.disney.disneyplus": "Disney+",
    "com.spotify.tv.android": "Spotify",
    "deezer.android.tv": "Deezer",
    "com.canal.android.canal": "myCANAL",
    "fr.francetv.pluzz": "france.tv",
    "fr.tf1.mytf1": "TF1+",
    "fr.m6.m6replay.by": "M6+",
    "tv.arte.plus7": "ARTE",
    "tv.molotov.app": "Molotov",
    "com.apple.atve.androidtv.appletv": "Apple TV",
    "com.cbs.ca": "Paramount+",
    "com.wbd.stream": "Max",
    "com.plexapp.android": "Plex",
    "org.xbmc.kodi": "Kodi",
    "org.videolan.vlc": "VLC",
    "tv.twitch.android.app": "Twitch",
    "com.google.android.videos": "Google TV",
    "com.google.android.tvlauncher": "Accueil",
    "com.google.android.apps.tv.launcherx": "Accueil Google TV",
    "com.android.vending": "Play Store",
    "com.android.tv.settings": "Paramètres",
    "com.google.android.apps.mediashell": "Chromecast",
    "com.google.android.play.games": "Play Jeux",
    "com.android.chrome": "Chrome",
    "com.sharp.tvsettings": "Réglages Sharp",
    "com.mediatek.wwtv.tvcenter": "TV en direct",
    "com.android.tv": "TV en direct",
    "com.google.android.tv": "Chaînes en direct",
}

# Applications proposées tant qu'aucune liste n'a été lue sur la TV.
DEFAULT_APPS = [
    "com.netflix.ninja",
    "com.google.android.youtube.tv",
    "com.amazon.amazonvideo.livingroom",
    "com.disney.disneyplus",
    "com.google.android.youtube.tvmusic",
    "com.spotify.tv.android",
]

# Paquets qui ne sont pas de vraies applications à proposer.
HIDDEN_PREFIXES = (
    "com.google.android.tvlauncher",
    "com.google.android.apps.tv.launcherx",
    "com.android.systemui",
    "com.google.android.katniss",
    "com.google.android.tungsten",
    "com.google.android.backdrop",
    "com.google.android.inputmethod",
    "com.android.providers",
)

_NOISE = {
    "com", "org", "net", "fr", "tv", "android", "androidtv", "app", "apps", "google",
    "leanback", "atv", "client", "mobile", "player", "ninja", "livingroom", "main",
}

_GENERIC = {"connect", "play", "plus", "live", "stream", "go", "now", "video", "videos", "media"}


def label_for(package: str) -> str:
    if package in KNOWN_APPS:
        return KNOWN_APPS[package]
    words = [w for w in re.split(r"[._]", package) if w and w.lower() not in _NOISE]
    if not words:
        return package
    # Le dernier segment significatif est souvent le nom de la marque.
    name = words[-1]
    if len(words) > 1 and (len(name) <= 2 or name.lower() in _GENERIC):
        name = f"{words[-2]} {name}"
    return name.replace("-", " ").title()


class AppCatalog:
    def __init__(self, path: Path) -> None:
        self.path = path
        data = self._load()
        self.installed: list[str] = data.get("installed", [])
        self.seen: list[str] = data.get("seen", [])

    def _load(self) -> dict:
        try:
            return json.loads(self.path.read_text("utf-8"))
        except (FileNotFoundError, json.JSONDecodeError):
            return {}

    def _save(self) -> None:
        self.path.parent.mkdir(exist_ok=True)
        data = {"installed": self.installed, "seen": self.seen}
        self.path.write_text(json.dumps(data, indent=2), "utf-8")

    def remember(self, package: str | None) -> None:
        """Retient une application qui vient d'être ouverte sur la TV."""
        if not package or package in self.seen or package.startswith(HIDDEN_PREFIXES):
            return
        self.seen.append(package)
        self._save()

    def set_installed(self, packages: list[str]) -> None:
        self.installed = sorted(set(packages))
        self._save()

    def listing(self) -> dict:
        packages = set(self.installed or DEFAULT_APPS) | set(self.seen)
        apps = [
            {"package": p, "label": label_for(p)}
            for p in packages
            if not p.startswith(HIDDEN_PREFIXES)
        ]
        apps.sort(key=lambda a: a["label"].casefold())
        return {"apps": apps, "complete": bool(self.installed)}


async def scan_installed_apps(host: str, key_path: Path) -> list[str]:
    """Lit la liste des applications de la TV par ADB (débogage réseau, port 5555).

    À la première connexion, la TV demande d'autoriser cet ordinateur.
    """
    from adb_shell.adb_device_async import AdbDeviceTcpAsync
    from adb_shell.auth.keygen import keygen
    from adb_shell.auth.sign_pythonrsa import PythonRSASigner

    if not key_path.exists():
        key_path.parent.mkdir(exist_ok=True)
        keygen(str(key_path))
    signer = PythonRSASigner.FromRSAKeyPath(str(key_path))

    device = AdbDeviceTcpAsync(host, 5555, default_transport_timeout_s=9.0)
    try:
        # Laisse le temps d'accepter la demande d'autorisation sur la TV.
        await device.connect(rsa_keys=[signer], auth_timeout_s=60.0)
        output = await device.shell(
            "cmd package query-activities --brief "
            "-a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER"
        )
        packages = re.findall(r"^\s*([\w.]+)/", output, re.MULTILINE)
        if not packages:
            # Anciennes versions d'Android : applications ajoutées par
            # l'utilisateur, plus les applications préinstallées connues.
            output = await device.shell("pm list packages")
            every = set(re.findall(r"^package:([\w.]+)", output, re.MULTILINE))
            output = await device.shell("pm list packages -3")
            packages = re.findall(r"^package:([\w.]+)", output, re.MULTILINE)
            packages += [p for p in KNOWN_APPS if p in every]
    finally:
        await device.close()
    LOGGER.info("%d applications trouvées sur la TV", len(packages))
    return packages
