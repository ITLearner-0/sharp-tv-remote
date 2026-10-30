"""Télécommande universelle pour TV Sharp Android TV / Google TV.

Lance un petit serveur web sur le réseau local. Ouvrez l'adresse affichée
sur votre téléphone pour obtenir une télécommande complète. Le serveur
dialogue avec la TV via le protocole officiel « Android TV Remote v2 »
(le même que l'application Google TV), ports 6466/6467.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import logging
import socket
from pathlib import Path

from aiohttp import web
from androidtvremote2 import (
    AndroidTVRemote,
    CannotConnect,
    ConnectionClosed,
    InvalidAuth,
)

BASE_DIR = Path(__file__).resolve().parent
STATIC_DIR = BASE_DIR / "static"
DATA_DIR = BASE_DIR / "data"
CONFIG_FILE = DATA_DIR / "config.json"
CERT_FILE = DATA_DIR / "cert.pem"
KEY_FILE = DATA_DIR / "key.pem"
CLIENT_NAME = "Telecommande Sharp"
SERVICE_TYPE = "_androidtvremote2._tcp.local."

LOGGER = logging.getLogger("sharp-remote")


def load_config() -> dict:
    try:
        return json.loads(CONFIG_FILE.read_text("utf-8"))
    except (FileNotFoundError, json.JSONDecodeError):
        return {}


def save_config(config: dict) -> None:
    DATA_DIR.mkdir(exist_ok=True)
    CONFIG_FILE.write_text(json.dumps(config, indent=2), "utf-8")


class TVController:
    """Garde une connexion unique vers la TV et expose son état."""

    def __init__(self) -> None:
        self.remote: AndroidTVRemote | None = None
        self.host: str | None = None
        self.state = "disconnected"  # disconnected | pairing | connected
        self.error: str | None = None

    def _new_remote(self, host: str) -> AndroidTVRemote:
        return AndroidTVRemote(
            client_name=CLIENT_NAME,
            certfile=str(CERT_FILE),
            keyfile=str(KEY_FILE),
            host=host,
        )

    def status(self) -> dict:
        info: dict = {"state": self.state, "host": self.host, "error": self.error}
        if self.remote and self.state == "connected":
            info["is_on"] = self.remote.is_on
            info["current_app"] = self.remote.current_app
            info["volume"] = self.remote.volume_info
            info["device"] = self.remote.device_info
        return info

    async def connect(self, host: str) -> dict:
        """Se connecte à la TV ; démarre l'appairage si nécessaire."""
        self.disconnect()
        DATA_DIR.mkdir(exist_ok=True)
        self.host = host
        self.error = None
        self.remote = self._new_remote(host)
        await self.remote.async_generate_cert_if_missing()
        try:
            await self.remote.async_connect()
        except InvalidAuth:
            # Pas encore appairé : la TV va afficher un code à 6 caractères.
            await self.remote.async_start_pairing()
            self.state = "pairing"
            return self.status()
        except (CannotConnect, ConnectionClosed, OSError) as exc:
            self.state = "disconnected"
            self.error = f"Impossible de joindre la TV ({exc})"
            return self.status()
        self._on_connected()
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
        self._on_connected()
        return self.status()

    def _on_connected(self) -> None:
        assert self.remote and self.host
        self.state = "connected"
        self.error = None
        self.remote.keep_reconnecting(invalid_auth_callback=self._on_invalid_auth)
        save_config({"host": self.host})
        LOGGER.info("Connecté à la TV %s", self.host)

    def _on_invalid_auth(self) -> None:
        self.state = "disconnected"
        self.error = "La TV a refusé la connexion : relancez l'appairage"

    def disconnect(self) -> None:
        if self.remote:
            self.remote.disconnect()
        self.remote = None
        self.state = "disconnected"

    def _require(self) -> AndroidTVRemote:
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
    request.app["tv"].disconnect()
    save_config({})
    return web.json_response({"state": "disconnected"})


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
    host = load_config().get("host")
    if host:
        LOGGER.info("Reconnexion automatique à %s…", host)
        await app["tv"].connect(host)


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
