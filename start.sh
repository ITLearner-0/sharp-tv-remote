#!/usr/bin/env sh
# Lance la télécommande (macOS / Linux / Raspberry Pi / Termux)
cd "$(dirname "$0")"
if [ ! -d .venv ]; then
  python3 -m venv .venv
  .venv/bin/pip install -r requirements.txt
fi
exec .venv/bin/python server.py "$@"
