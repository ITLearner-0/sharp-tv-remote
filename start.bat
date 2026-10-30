@echo off
REM Lance la telecommande (Windows)
cd /d "%~dp0"
if not exist server.py (
  echo.
  echo ERREUR : server.py introuvable dans %cd%
  echo start.bat doit rester dans le meme dossier que server.py, requirements.txt et static.
  echo Ouvrez le dossier qui contient tous ces fichiers et relancez start.bat depuis celui-ci.
  echo.
  pause
  exit /b 1
)
if not exist .venv\Scripts\python.exe python -m venv .venv
.venv\Scripts\python -c "import androidtvremote2, aiohttp, zeroconf" 2>nul || .venv\Scripts\python -m pip install --disable-pip-version-check -r requirements.txt
.venv\Scripts\python server.py %*
pause
