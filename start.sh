#!/usr/bin/env sh
# Lance la télécommande (macOS / Linux / Raspberry Pi / Termux)
cd "$(dirname "$0")"
if [ ! -f server.py ]; then
  echo "ERREUR : server.py introuvable dans $(pwd)."
  echo "start.sh doit rester dans le même dossier que server.py, requirements.txt et static."
  exit 1
fi
[ -x .venv/bin/python ] || python3 -m venv .venv
.venv/bin/python -c "import androidtvremote2, aiohttp, zeroconf" 2>/dev/null \
  || .venv/bin/pip install --disable-pip-version-check -r requirements.txt
exec .venv/bin/python server.py "$@"
