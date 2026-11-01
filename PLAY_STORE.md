# Publier OmniZap sur le Play Store

Ce qui est prêt dans le dépôt :

| Élément | Où |
|---|---|
| App Bundle signé, sans le déblocage de test | Action GitHub **« Version Play Store »** (`.github/workflows/play-release.yml`) |
| Politique de confidentialité (FR + EN) | `PRIVACY.md` → <https://github.com/ITLearner-0/sharp-tv-remote/blob/main/PRIVACY.md> |
| Textes de la fiche (9 langues) | `fastlane/metadata/android/<langue>/` (titre, description courte, description complète) |
| Icône 512 × 512 et image d'en-tête 1024 × 500 | `fastlane/metadata/android/<langue>/images/` |

Identifiant de l'application : **app.omnizap.remote** · produit intégré : **omnizap_pro** (9,99 €).

---

## 1. Clé d'envoi dans GitHub (une seule fois)

La clé d'envoi signe chaque version envoyée à Google. Elle est **privée** : jamais
dans le dépôt. Gardez le fichier `omnizap-upload.keystore` et son mot de passe en
lieu sûr (gestionnaire de mots de passe, clé USB). En cas de perte, Google permet
de la remplacer (Play Console → Intégrité de l'application).

Sur GitHub : dépôt → **Settings → Secrets and variables → Actions → New repository secret**, deux fois :

| Nom du secret | Valeur |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | tout le contenu du fichier `UPLOAD_KEYSTORE_BASE64.txt` |
| `UPLOAD_STORE_PASSWORD` | le contenu du fichier `UPLOAD_STORE_PASSWORD.txt` |

## 2. Produire l'App Bundle

Dépôt → onglet **Actions → Version Play Store → Run workflow**, numéro de version
`1.0.0` (puis `1.0.1`, `1.0.2`…). Au bout de 5 minutes environ : ouvrir l'exécution →
**Artifacts → omnizap-play-1.0.0** → fichier `omnizap-1.0.0.aab`.

L'Action vérifie que le fichier est signé avec la clé d'envoi ; sinon elle s'arrête en erreur.

## 3. Compte développeur

<https://play.google.com/console/signup> : compte **personnel**, 25 $ une fois,
pièce d'identité. Un compte personnel récent doit faire un **test fermé avec au moins
12 testeurs pendant 14 jours** avant de pouvoir publier pour tout le monde.

## 4. Créer l'application

Play Console → **Créer une application** : nom « OmniZap – Télécommande TV », langue
par défaut Français, **Application**, **Gratuite**. Accepter les déclarations.

### Fiche Play Store (Présence sur le Play Store → Fiche principale)
- Titre, description courte, description complète : copier depuis `fastlane/metadata/android/fr-FR/`.
  Ajouter les traductions (Gérer les traductions) depuis les autres dossiers.
- Icône : `images/icon.png` · Image de présentation : `images/featureGraphic.png`.
- **Captures d'écran du téléphone : à prendre vous-même** (au moins 2, idéalement 4 à 8) :
  la télécommande, les applications, le pavé tactile, l'écran Pro, « Voir la TV ».
- Catégorie : **Outils** (ou Lecteurs et éditeurs vidéo) · E-mail de contact.

### Contenu de l'application (Règles → Contenu de l'application)

| Question | Réponse |
|---|---|
| Règles de confidentialité | `https://github.com/ITLearner-0/sharp-tv-remote/blob/main/PRIVACY.md` |
| Accès à l'application | Toutes les fonctionnalités sont accessibles sans identifiants |
| Annonces | **Non**, l'application ne contient pas d'annonces |
| Classification du contenu | Catégorie « Utilitaire / productivité » ; répondre **Non** à toutes les questions (violence, sexualité, langage, jeux d'argent…) ; **Oui** à « achats numériques » → PEGI 3 / Tout public |
| Public cible | **13 ans et plus** (ne pas cocher les tranches plus jeunes) |
| Application d'actualités, santé, finance, VPN… | Non |
| Sécurité des données | voir ci-dessous |
| Autorisations de service de premier plan | voir ci-dessous |

### Sécurité des données
- *Votre application collecte-t-elle ou partage-t-elle des données utilisateur ?* → **Non**.
  OmniZap n'envoie rien à un serveur : les TV enregistrées restent sur le téléphone ; le son
  du micro, les photos et l'image de l'écran vont **directement à la TV de l'utilisateur**,
  sur son réseau local, à sa demande. Le paiement est traité par Google Play.
- *Suppression des données* : les données restent sur l'appareil ; désinstaller les efface.
- ⚠ À vérifier au moment de remplir : la bibliothèque **Google Cast** (services Google Play)
  peut avoir ses propres données à déclarer ; consulter la page « Google Play's Data safety
  section » de la documentation Google Cast pour développeurs et suivre ses indications.

### Autorisations de service de premier plan
La duplication d'écran utilise `FOREGROUND_SERVICE_MEDIA_PROJECTION`. Déclarer :
- Type : **Projection de contenus multimédias**.
- Description : « L'utilisateur duplique l'écran de son téléphone (image et son) sur sa TV
  Android, sur son réseau local. La duplication démarre uniquement à sa demande, une
  notification reste affichée pendant toute la durée, et un bouton l'arrête. »
- Vidéo : enregistrer l'écran du téléphone pendant une duplication (30 s), la mettre sur
  YouTube en **non répertoriée**, coller le lien.

## 5. Test fermé (12 testeurs, 14 jours)

Tests → **Test fermé** → créer un canal → **Créer une version** → envoyer le fichier `.aab`.
Accepter la **Signature d'application par Google Play** (recommandé). Notes de version :
« Première version de test ». Ajouter une liste de testeurs (adresses Gmail), puis
leur envoyer le lien d'inscription : chacun doit **accepter le test et installer
l'application**, et rester inscrit 14 jours.

## 6. Produit OmniZap Pro

Possible une fois le premier `.aab` envoyé (il déclare Google Play Billing).
**Monétiser → Produits → Produits intégrés à l'application → Créer** :
- ID : `omnizap_pro` (exactement) · Nom : « OmniZap Pro »
- Description : « Plusieurs TV, applications, voix, photos et vidéos, duplication, Voir la TV, Bluetooth, widget. Achat unique. »
- Prix : **9,99 €** (conversion automatique pour les autres pays) → **Activer**.

Il faut aussi un **profil de paiement** (Paramètres → Profil de paiement) pour vendre.
Pour tester l'achat sans payer : Paramètres → **Test des licences** → ajouter son adresse Gmail.

## 7. Production

Après 14 jours : Tableau de bord → **Demander l'accès à la production** (quelques questions
sur le test), puis Production → Créer une version → envoyer le dernier `.aab` → publier.
L'examen par Google prend en général de quelques heures à quelques jours.

---

## Points d'attention

- **Traductions africaines et darija** : faire relire par des locuteurs natifs avant la production.
- **Marques** : la fiche cite Samsung, LG, Sony… seulement pour la compatibilité, avec la
  mention « application indépendante » ; ne pas utiliser leurs logos dans les captures.
- **Versions de test** (APK GitHub) : elles gardent le bouton « Débloquer (test) » et la clé
  de test publique ; ne pas les distribuer au public une fois l'application sur le Play Store.
