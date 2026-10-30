# 📺 Télécommande universelle pour TV Sharp (Android TV / Google TV)

Votre télécommande Sharp est en panne ? Ce projet transforme **votre téléphone
(ou n'importe quel navigateur)** en télécommande complète, via le Wi-Fi.

Il utilise le protocole officiel **Android TV Remote v2** (celui de
l'application Google TV) : aucun réglage développeur ni ADB n'est nécessaire,
et il fonctionne avec toutes les TV Sharp sous Android TV ou Google TV
(séries FN, FL, GL, EQ, etc.), ainsi qu'avec les autres Android TV
(Philips, Sony, TCL, Hisense, Xiaomi…).

## Fonctionnalités

- Marche/arrêt, source (HDMI…), réglages
- Pavé directionnel + OK, Retour, Accueil, Menu
- Volume (+/−, muet) et chaînes, avec répétition en maintenant le bouton
- Lecture / pause / stop / avance et retour rapides
- Pavé numérique, touches couleur, sous-titres, guide, info
- Lancement direct d'applications (Netflix, YouTube, Prime Video, Disney+…)
- **Saisie de texte** depuis le clavier du téléphone (recherches, mots de passe Wi-Fi…)
- Recherche automatique de la TV sur le réseau
- Reconnexion automatique, appairage mémorisé
- Raccourcis clavier quand on l'utilise depuis un ordinateur (flèches, Entrée, Échap, +/−, espace)
- Installable sur l'écran d'accueil du téléphone (PWA)

## Comment ça marche

```
 Téléphone (navigateur)  ──HTTP──▶  server.py (PC, Raspberry Pi…)  ──TLS 6466/6467──▶  TV Sharp
```

Un navigateur ne peut pas parler directement au protocole chiffré de la TV :
un petit serveur Python fait le relais. Il tourne sur un ordinateur (Windows,
macOS, Linux, Raspberry Pi) **connecté au même réseau que la TV**.

## Installation

Prérequis : [Python 3.10 ou plus récent](https://www.python.org/downloads/)
(sous Windows, cochez « Add Python to PATH » pendant l'installation).

```bash
git clone https://github.com/ITLearner-0/sharp-tv-remote.git
cd sharp-tv-remote
```

Puis lancez :

| Système               | Commande       |
|-----------------------|----------------|
| Windows               | double-cliquez sur `start.bat` |
| macOS / Linux / Pi    | `./start.sh`   |

Le premier lancement installe les dépendances. Le terminal affiche ensuite :

```
  Télécommande prête : ouvrez http://192.168.1.20:8080 sur votre téléphone
```

## Première connexion (une seule fois)

1. **Allumez la TV** avec le bouton physique de la TV (souvent sous l'écran,
   au centre ou à l'arrière à droite).
2. Sur le téléphone, ouvrez l'adresse affichée par le serveur.
3. Touchez **« Rechercher ma TV »**, ou saisissez son adresse IP
   (sur la TV : *Paramètres → Réseau et Internet → votre réseau*).
4. Un **code à 6 caractères** s'affiche sur la TV : saisissez-le. C'est fini.

L'appairage est mémorisé dans le dossier `data/` : aux lancements suivants,
la connexion est automatique.

> Astuce : dans le navigateur du téléphone, utilisez « Ajouter à l'écran
> d'accueil » pour l'ouvrir comme une vraie application.

## Dépannage

| Problème | Solution |
|---|---|
| « Impossible de joindre la TV » | Vérifiez que la TV est allumée et sur le même Wi-Fi. Réessayez avec l'IP exacte. |
| La TV n'est pas trouvée par la recherche | Certains box bloquent la découverte mDNS : saisissez l'IP manuellement. |
| La TV en veille ne répond plus | Activez sur la TV *Paramètres → Système → Alimentation → « Réveil par le réseau / Wake on LAN »* (ou « Mise en veille rapide »). |
| Le téléphone n'ouvre pas la page | Le pare-feu de l'ordinateur bloque peut-être le port 8080 : autorisez Python sur les réseaux privés. |
| Le code est refusé | Touchez « Annuler » puis reconnectez-vous : un nouveau code s'affiche. |
| Changer de TV | Bouton ⚙ en haut de la télécommande. |

Options du serveur : `python server.py --port 9000` pour changer de port.

## Solution de dépannage immédiate

En attendant, l'application officielle **Google TV**
([Android](https://play.google.com/store/apps/details?id=com.google.android.videos) /
[iPhone](https://apps.apple.com/app/google-tv/id1193350206)) contient aussi
une télécommande intégrée (bouton « Télécommande » en bas à droite), basée
sur le même protocole.

## Structure du projet

```
server.py          serveur web + connexion à la TV (androidtvremote2)
static/index.html  interface de la télécommande
static/app.js      logique (touches, répétition, appairage, texte)
static/style.css   apparence
start.sh / .bat    lanceurs
data/              certificat d'appairage et IP de la TV (créé au besoin, non versionné)
```

## Licence

MIT
