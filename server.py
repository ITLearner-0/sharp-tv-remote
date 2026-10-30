"""Télécommande universelle pour TV Sharp Android TV / Google TV.

Lance un petit serveur web sur le réseau local. Ouvrez l'adresse affichée
sur votre téléphone pour obtenir une télécommande complète. Le serveur
dialogue avec la TV via le protocole officiel « Android TV Remote v2 »
(le même que l'application Google TV), ports 6466/6467, et la réveille
par Wake-on-LAN quand elle est éteinte.
"""

from __future__ import annotations

import argparse
import asyncio
import contextlib
import ipaddress
import json
import logging
import re
import socket
from pathlib import Path

from aiohttp import web
from androidtvremote2 import (
    AndroidTVRemote,
    CannotConnect,
    ConnectionClosed,
    InvalidAuth,
)

from apps import AppCatalog, scan_installed_apps

BASE_DIR = Path(__file__).resolve().parent
STATIC_DIR = BASE_DIR / "static"
DATA_DIR = BASE_DIR / "data"
CONFIG_FILE = DATA_DIR / "config.json"
CERT_FILE = DATA_DIR / "cert.pem"
KEY_FILE = DATA_DIR / "key.pem"
ADB_KEY_FILE = DATA_DIR / "adbkey"
APPS_FILE = DATA_DIR / "apps.json"
CLIENT_NAME = "Telecommande Sharp"
SERVICE_TYPE = "_androidtvremote2._tcp.local."
RETRY_SECONDS = 10

LOGGER = logging.getLogger("sharp-remote")


def load_config() -> dict:
    try:
        return json.loads(CONFIG_FILE.read_text("utf-8"))
    except (FileNotFoundError, json.JSONDecodeError):
        return {}


def save_config(**changes: str | None) -> None:
    config = load_config()
    for key, value in changes.items():
        if value is None:
            config.pop(key, None)
        else:
            config[key] = value
    DATA_DIR.mkdir(exist_ok=True)
    CONFIG_FILE.write_text(json.dumps(config, indent=2), "utf-8")


def normalize_mac(mac: str) -> str:
    digits = re.sub(r"[^0-9A-Fa-f]", "", mac)
    if len(digits) != 12:
        raise ValueError("Adresse MAC invalide (exemple : A4:B1:C2:D3:E4:F5)")
    return ":".join(digits[i:i + 2] for i in range(0, 12, 2)).upper()


def send_magic_packet(mac: str, host: str | None) -> None:
    """Envoie un paquet Wake-on-LAN, sur plusieurs adresses pour plus de fiabilité."""
    packet = b"\xff" * 6 + bytes.fromhex(normalize_mac(mac).replace(":", "")) * 16
    targets = {"255.255.255.255"}
    if host:
        with contextlib.suppress(ValueError):
            targets.add(str(ipaddress.ip_network(f"{host}/24", strict=False).broadcast_address))
            targets.add(host)
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        for target in targets:
            for port in (9, 7):
                with contextlib.suppress(OSError):
                    sock.sendto(packet, (target, port))


class TVController:
    """Garde une connexion unique vers la TV et expose son état.

    États : disconnected (aucune TV choisie), pairing (code attendu),
    offline (TV appairée mais éteinte ou injoignable), connected.
    """

    def __init__(self) -> None:
        self.remote: AndroidTVRemote | None = None
        self.host: str | None = None
        self.mac: str | None = None
        self.state = "disconnected"
        self.error: str | None = None
        self._watcher: asyncio.Task | None = None
        self._lock = asyncio.Lock()
        self.apps = AppCatalog(APPS_FILE)

    def _new_remote(self, host: str) -> AndroidTVRemote:
        return AndroidTVRemote(
            client_name=CLIENT_NAME,
            certfile=str(CERT_FILE),
            keyfile=str(KEY_FILE),
            host=host,
        )

    def status(self) -> dict:
        info: dict = {
            "state": self.state,
            "host": self.host,
            "mac": self.mac,
            "error": self.error,
        }
        if self.remote and self.state == "connected":
            info["is_on"] = self.remote.is_on
            info["current_app"] = self.remote.current_app
            info["volume"] = self.remote.volume_info
            info["device"] = self.remote.device_info
        return info

    def restore(self) -> None:
        """Au démarrage : reprend la TV déjà appairée et tente de s'y connecter."""
        config = load_config()
        self.host = config.get("host")
        with contextlib.suppress(TypeError, ValueError):
            self.mac = normalize_mac(config["mac"]) if config.get("mac") else None
        if self.host:
            self.state = "offline"
            self._start_watcher()

    async def connect(self, host: str) -> dict:
        """Choisit une TV : se connecte, ou démarre l'appairage si nécessaire."""
        self.disconnect()
        if host != load_config().get("host"):
            self.mac = None
        self.host = host
        self.error = None
        DATA_DIR.mkdir(exist_ok=True)
        remote = self._new_remote(host)
        await remote.async_generate_cert_if_missing()
        try:
            await remote.async_connect()
        except InvalidAuth:
            # Pas encore appairé : la TV va afficher un code à 6 caractères.
            self.remote = remote
            await remote.async_start_pairing()
            self.state = "pairing"
            return self.status()
        except (CannotConnect, ConnectionClosed, OSError) as exc:
            self.state = "disconnected"
            self.error = f"Impossible de joindre la TV ({exc})"
            return self.status()
        await self._on_connected(remote)
        return self.status()

    async def finish_pairing(self, code: str) -> dict:
        if not self.remote or self.state != "pairing":
            raise web.HTTPBadRequest(text="Aucun appairage en cours")
        try:
            await self.remote.async_finish_pairing(code.strip().upper())
        except InvalidAuth:
            self.error = "Code incorrect, réessayez"
            await self.remote.async_start_pairing()
            return self.status()
        except ConnectionClosed:
            self.error = "Appairage interrompu, relancez la connexion"
            self.state = "disconnected"
            return self.status()
        try:
            await self.remote.async_connect()
        except (CannotConnect, ConnectionClosed, InvalidAuth) as exc:
            self.state = "disconnected"
            self.error = f"Connexion après appairage échouée ({exc})"
            return self.status()
        await self._on_connected(self.remote)
        return self.status()

    async def _on_connected(self, remote: AndroidTVRemote) -> None:
        assert self.host
        self.remote = remote
        self.state = "connected"
        self.error = None
        remote.add_is_available_updated_callback(self._on_available)
        remote.add_current_app_updated_callback(self.apps.remember)
        self.apps.remember(remote.current_app)
        remote.keep_reconnecting(invalid_auth_callback=self._on_invalid_auth)
        save_config(host=self.host)
        LOGGER.info("Connecté à la TV %s", self.host)
        if not self.mac:
            # L'adresse MAC (lue dans le certificat de la TV) sert à la réveiller.
            with contextlib.suppress(CannotConnect, OSError, ValueError):
                _, mac = await remote.async_get_name_and_mac()
                self.set_mac(mac)

    def set_mac(self, mac: str | None) -> None:
        self.mac = normalize_mac(mac) if mac else None
        save_config(mac=self.mac)
        LOGGER.info("Adresse MAC de la TV : %s", self.mac)

    def _on_available(self, available: bool) -> None:
        if self.state in ("connected", "offline"):
            self.state = "connected" if available else "offline"

    def _on_invalid_auth(self) -> None:
        self.state = "disconnected"
        self.error = "La TV a refusé la connexion : relancez l'appairage"

    def _start_watcher(self) -> None:
        if not self._watcher or self._watcher.done():
            self._watcher = asyncio.get_running_loop().create_task(self._watch())

    async def _watch(self) -> None:
        """Retente la connexion tant que la TV appairée reste injoignable."""
        while self.state == "offline" and not self.remote:
            if await self._try_connect_paired():
                return
            await asyncio.sleep(RETRY_SECONDS)

    async def _try_connect_paired(self) -> bool:
        async with self._lock:
            if self.remote or not self.host:
                return bool(self.remote)
            remote = self._new_remote(self.host)
            await remote.async_generate_cert_if_missing()
            try:
                await remote.async_connect()
            except InvalidAuth:
                self.state = "disconnected"
                self.error = "La TV ne reconnaît plus cet appareil : relancez l'appairage"
                return True
            except (CannotConnect, ConnectionClosed, OSError):
                return False
            await self._on_connected(remote)
            return True

    async def power(self) -> str:
        """Allume la TV si elle est éteinte, l'éteint si elle est allumée."""
        if self.state == "connected" and self.remote and self.remote.is_on:
            self.send_key("KEYCODE_POWER")
            return "off"
        if self.state == "connected" and self.remote:
            # En veille mais toujours joignable : la touche Marche suffit.
            self.send_key("KEYCODE_POWER")
        if self.mac:
            for _ in range(3):
                send_magic_packet(self.mac, self.host)
                await asyncio.sleep(0.3)
        elif self.state != "connected":
            raise web.HTTPConflict(
                text="TV injoignable et adresse MAC inconnue : renseignez-la dans ⚙"
            )
        if self.state == "offline" and not self.remote:
            self._start_watcher()
        return "on"

    async def scan_apps(self) -> dict:
        if not self.host or self.state != "connected":
            raise web.HTTPConflict(text="TV non connectée")
        try:
            packages = await scan_installed_apps(self.host, ADB_KEY_FILE)
        except ImportError as exc:
            raise web.HTTPServiceUnavailable(
                text="Module adb-shell manquant : relancez start.bat pour l'installer"
            ) from exc
        except Exception as exc:  # noqa: BLE001 - erreurs réseau/ADB très variées
            LOGGER.info("Lecture ADB impossible : %r", exc)
            raise web.HTTPServiceUnavailable(
                text="Lecture impossible : activez le débogage USB sur la TV "
                "et acceptez la demande d'autorisation (voir l'aide)"
            ) from exc
        self.apps.set_installed(packages)
        return self.apps.listing()

    def disconnect(self) -> None:
        if self._watcher:
            self._watcher.cancel()
            self._watcher = None
        if self.remote:
            self.remote.disconnect()
        self.remote = None
        self.state = "disconnected"

    def forget(self) -> None:
        self.disconnect()
        self.host = self.mac = None
        save_config(host=None, mac=None)

    def _require(self) -> AndroidTVRemote:
        if self.state == "offline":
            raise web.HTTPConflict(text="TV éteinte : appuyez d'abord sur ⏻")
        if not self.remote or self.state != "connected":
            raise web.HTTPConflict(text="TV non connectée")
        return self.remote

    def send_key(self, key: str, direction: str = "SHORT") -> None:
        try:
            self._require().send_key_command(key, direction)
        except ValueError as exc:
            raise web.HTTPBadRequest(text=str(exc)) from exc
        except ConnectionClosed as exc:
            raise web.HTTPServiceUnavailable(text="Connexion perdue, reconnexion…") from exc

    def send_text(self, text: str) -> None:
        try:
            self._require().send_text(text)
        except ConnectionClosed as exc:
            raise web.HTTPServiceUnavailable(text="Connexion perdue, reconnexion…") from exc

    def launch(self, link: str) -> None:
        try:
            self._require().send_launch_app_command(link)
        except ConnectionClosed as exc:
            raise web.HTTPServiceUnavailable(text="Connexion perdue, reconnexion…") from exc


async def discover_tvs(timeout: float = 3.0) -> list[dict]:
    """Recherche les Android TV du réseau local via mDNS."""
    try:
        from zeroconf import ServiceStateChange
        from zeroconf.asyncio import AsyncServiceBrowser, AsyncServiceInfo, AsyncZeroconf
    except ImportError:
        return []

    found: dict[str, dict] = {}
    names: set[str] = set()

    def on_change(zeroconf, service_type, name, state_change):  # noqa: ANN001
        if state_change is ServiceStateChange.Added:
            names.add(name)

    azc = AsyncZeroconf()
    browser = AsyncServiceBrowser(azc.zeroconf, [SERVICE_TYPE], handlers=[on_change])
    await asyncio.sleep(timeout)
    for name in names:
        info = AsyncServiceInfo(SERVICE_TYPE, name)
        if await info.async_request(azc.zeroconf, 1500):
            addresses = info.parsed_addresses()
            if addresses:
                found[addresses[0]] = {
                    "name": name.removesuffix("." + SERVICE_TYPE),
                    "host": addresses[0],
                }
    await browser.async_cancel()
    await azc.async_close()
    return list(found.values())


routes = web.RouteTableDef()


@routes.get("/")
async def index(request: web.Request) -> web.FileResponse:
    return web.FileResponse(STATIC_DIR / "index.html")


@routes.get("/api/status")
async def api_status(request: web.Request) -> web.Response:
    return web.json_response(request.app["tv"].status(), dumps=_dumps)


@routes.get("/api/discover")
async def api_discover(request: web.Request) -> web.Response:
    return web.json_response(await discover_tvs())


@routes.post("/api/connect")
async def api_connect(request: web.Request) -> web.Response:
    body = await request.json()
    host = str(body.get("host", "")).strip()
    if not host:
        raise web.HTTPBadRequest(text="Adresse IP manquante")
    return web.json_response(await request.app["tv"].connect(host), dumps=_dumps)


@routes.post("/api/pair")
async def api_pair(request: web.Request) -> web.Response:
    body = await request.json()
    status = await request.app["tv"].finish_pairing(str(body.get("code", "")))
    return web.json_response(status, dumps=_dumps)


@routes.post("/api/disconnect")
async def api_disconnect(request: web.Request) -> web.Response:
    request.app["tv"].forget()
    return web.json_response({"state": "disconnected"})


@routes.post("/api/power")
async def api_power(request: web.Request) -> web.Response:
    return web.json_response({"ok": True, "action": await request.app["tv"].power()})


@routes.post("/api/mac")
async def api_mac(request: web.Request) -> web.Response:
    body = await request.json()
    try:
        request.app["tv"].set_mac(str(body.get("mac", "")).strip() or None)
    except ValueError as exc:
        raise web.HTTPBadRequest(text=str(exc)) from exc
    return web.json_response(request.app["tv"].status(), dumps=_dumps)


@routes.get("/api/apps")
async def api_apps(request: web.Request) -> web.Response:
    return web.json_response(request.app["tv"].apps.listing())


@routes.post("/api/apps/scan")
async def api_apps_scan(request: web.Request) -> web.Response:
    return web.json_response(await request.app["tv"].scan_apps())


@routes.post("/api/key")
async def api_key(request: web.Request) -> web.Response:
    body = await request.json()
    request.app["tv"].send_key(str(body["key"]), str(body.get("direction", "SHORT")))
    return web.json_response({"ok": True})


@routes.post("/api/text")
async def api_text(request: web.Request) -> web.Response:
    body = await request.json()
    request.app["tv"].send_text(str(body.get("text", "")))
    return web.json_response({"ok": True})


@routes.post("/api/app")
async def api_app(request: web.Request) -> web.Response:
    body = await request.json()
    request.app["tv"].launch(str(body["link"]))
    return web.json_response({"ok": True})


def _dumps(obj: object) -> str:
    return json.dumps(obj, default=str)


async def on_startup(app: web.Application) -> None:
    app["tv"].restore()


async def on_cleanup(app: web.Application) -> None:
    app["tv"].disconnect()


def local_ip() -> str:
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        try:
            sock.connect(("8.8.8.8", 80))
            return sock.getsockname()[0]
        except OSError:
            return "127.0.0.1"


def create_app() -> web.Application:
    app = web.Application()
    app["tv"] = TVController()
    app.add_routes(routes)
    app.router.add_static("/static", STATIC_DIR)
    app.on_startup.append(on_startup)
    app.on_cleanup.append(on_cleanup)
    return app


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8080, help="port web (défaut : 8080)")
    parser.add_argument("--bind", default="0.0.0.0", help="interface d'écoute")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    print(f"\n  Télécommande prête : ouvrez http://{local_ip()}:{args.port} sur votre téléphone\n")
    web.run_app(create_app(), host=args.bind, port=args.port, print=None)


if __name__ == "__main__":
    main()
