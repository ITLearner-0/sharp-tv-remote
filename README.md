# 📺 Télécommande universelle pour TV Sharp (Android TV / Google TV)

Votre télécommande Sharp est en panne ? Ce projet transforme **votre téléphone
(ou n'importe quel navigateur)** en télécommande complète, via le Wi-Fi.

Il utilise le protocole officiel **Android TV Remote v2** (celui de
l'application Google TV) : aucun réglage développeur ni ADB n'est nécessaire,
et il fonctionne avec toutes les TV Sharp sous Android TV ou Google TV
(séries FN, FL, GL, EQ, etc.), ainsi qu'avec les autres Android TV
(Philips, Sony, TCL, Hisense, Xiaomi…).

## Fonctionnalités

- Marche/arrêt, **y compris pour allumer la TV éteinte** (Wake-on-LAN), source (HDMI…), réglages
- Pavé directionnel + OK, Retour, Accueil, Menu
- Volume (+/−, muet) et chaînes, avec répétition en maintenant le bouton
- Lecture / pause / stop / avance et retour rapides
- Pavé numérique, touches couleur, sous-titres, guide, info
- **Toutes les applications de la TV** listées et lançables d'un geste
- **Saisie de texte** depuis le clavier du téléphone (recherches, mots de passe Wi-Fi…)
- Recherche automatique de la TV sur le réseau
- Reconnexion automatique, appairage mémorisé
- Raccourcis clavier quand on l'utilise depuis un ordinateur (flèches, Entrée, Échap, +/−, espace)
- Installable sur l'écran d'accueil du téléphone (PWA)

## 📱 Application Android (version de test)

Le dossier `android/` contient une **application Android autonome** : le
téléphone pilote directement la TV, **sans ordinateur ni serveur**. Elle reprend
toutes les fonctions (appairage, touches, texte, allumage par le réseau,
liste des applications) et ajoute les **boutons de volume du téléphone**.

**Installer la dernière version sur le téléphone :**

1. Sur le téléphone, connecté à votre compte GitHub, ouvrez la page
   [Releases du dépôt](../../releases/tag/android-latest) et téléchargez
   **`telecommande-tv.apk`**.
2. Ouvrez le fichier. Android demande d'autoriser l'installation
   d'applications « de sources inconnues » pour votre navigateur ou
   gestionnaire de fichiers : acceptez.
3. Si Play Protect affiche un avertissement, choisissez « Installer quand même »
   (l'application n'est pas encore publiée sur le Play Store).

Chaque modification du dossier `android/` produit automatiquement une nouvelle
version (GitHub Actions), qui s'installe par-dessus la précédente.

> ⚠️ L'APK est signé avec une **clé de test publique** (`android/app/test-signing.keystore`).
> Elle convient pour les essais, pas pour le Play Store : il faudra une clé
> privée avant toute publication.

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

## Allumer la TV quand elle est éteinte

Le bouton ⏻ fonctionne aussi quand la TV est éteinte : la télécommande
envoie un « paquet de réveil » réseau (Wake-on-LAN) à l'adresse MAC de la TV.
Cette adresse est détectée automatiquement lors de la première connexion
(visible et modifiable dans ⚙).

Pour que la TV puisse être réveillée, elle doit garder le réseau actif en
veille. Selon le modèle Sharp, activez **une** de ces options :

- *Paramètres → Réseau et Internet → « Wake on LAN » / « Réveil par le réseau »*
- *Paramètres → Système → Alimentation et énergie → « Veille réseau » / « Démarrage rapide »*
- *Paramètres → Préférences de l'appareil → Alimentation → « Mise en veille rapide »*

Le réveil est plus fiable si la TV est branchée **en câble Ethernet** :
certaines TV ne se réveillent pas par le Wi-Fi.

Quand la TV est éteinte, la télécommande l'indique en haut
(« TV éteinte ») et grise les autres touches. Elle se reconnecte toute seule
dès que la TV est allumée.

## Lister toutes les applications de la TV

Le menu **Applications** affiche d'abord les applications courantes et
retient automatiquement toutes celles que vous ouvrez sur la TV.

Pour afficher **d'un coup toutes les applications installées**, la TV doit
autoriser le « débogage » (ADB). C'est à faire une seule fois :

1. Sur la TV : *Paramètres → Préférences de l'appareil → À propos*
   (ou *Paramètres → Système → À propos* sur Google TV).
2. Sélectionnez **« Build » / « Version de l'OS Android TV »** et appuyez
   **7 fois sur OK** : le message « Vous êtes désormais développeur » s'affiche.
3. Revenez en arrière : *Préférences de l'appareil → Options pour les développeurs*
   (ou *Système → Options pour les développeurs*) et activez **« Débogage USB »**
   (et **« Débogage réseau / ADB sur le réseau »** si l'option existe).
4. Dans la télécommande : *Applications → 🔄 Lister toutes les applications de la TV*.
5. La TV demande « Autoriser le débogage ? » : cochez **« Toujours autoriser »**
   et validez **OK** avec les flèches de la télécommande web (1 minute pour le faire).

La liste est ensuite mémorisée ; touchez à nouveau le bouton après avoir
installé de nouvelles applications.

## Dépannage

| Problème | Solution |
|---|---|
| « Impossible de joindre la TV » | Vérifiez que la TV est allumée et sur le même Wi-Fi. Réessayez avec l'IP exacte. |
| La TV n'est pas trouvée par la recherche | Certains box bloquent la découverte mDNS : saisissez l'IP manuellement. |
| La TV ne s'allume pas avec ⏻ | Vérifiez l'adresse MAC dans ⚙ et activez le réveil par le réseau (voir « Allumer la TV quand elle est éteinte »). Essayez en Ethernet. |
| « Lecture impossible » des applications | Vérifiez que le débogage USB est activé et acceptez la demande sur la TV. Si rien ne s'affiche, désactivez puis réactivez le débogage USB. |
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
server.py          serveur web + connexion à la TV (androidtvremote2) + Wake-on-LAN
apps.py            liste des applications (ADB + applications déjà ouvertes)
android/           application Android autonome (Kotlin, Jetpack Compose)
static/index.html  interface de la télécommande
static/app.js      logique (touches, répétition, appairage, texte)
static/style.css   apparence
start.sh / .bat    lanceurs
data/              appairage, IP/MAC de la TV, clé ADB, applications (créé au besoin, non versionné)
```

## Licence

MIT
