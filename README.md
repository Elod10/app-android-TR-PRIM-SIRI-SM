# Temps réel IDF — widget Android (PRIM / SIRI StopMonitoring)

Application Android (APK) fournissant un **widget d'écran d'accueil** qui affiche en temps réel les
prochains passages d'une ligne à un arrêt d'Île-de-France, à partir de l'API
**PRIM d'Île-de-France Mobilités** (requête unitaire « Prochains passages », format SIRI Lite
*StopMonitoring*).

## Fonctionnalités

- **Clé API PRIM** saisie dans l'application (écran principal ou première étape de configuration),
  avec bouton « Tester ». Elle est stockée uniquement sur le téléphone (sauvegarde Android désactivée).
- **Choix de l'arrêt via un menu en 4 étapes** : mode de transport → ligne → arrêt → sens.
  - Modes : Métro, RER, Train/Transilien, Tramway, Bus/Noctilien, Câble/Funiculaire.
  - Lignes et arrêts issus des référentiels open data IDFM (`referentiel-des-lignes`,
    `arrets-lignes`), avec recherche, pastilles aux couleurs officielles et cache local de 7 jours.
  - **Sens** : l'application interroge PRIM en temps réel sur chaque point d'arrêt (quai) de l'arrêt
    choisi et propose les sens observés (« Vers Porte de Clignancourt »…), ou « Tous les sens ».
    Plusieurs sens/destinations peuvent être cochés (utile pour les branches du RER).
- **Widget redimensionnable, du 1×1 au 4×3** : le format s'adapte à la taille du widget, au type de
  ligne et au sens choisi.

  | Taille | Un sens | Tous les sens |
  |---|---|---|
  | 1×1 | Pastille | Pastille (prochain passage) |
  | 2×1 | Prochain passage et suivant | Prochain passage et suivant |
  | 2×2 | Grand chiffre et frise ; voie en grand pour les trains | Les deux sens empilés |
  | 4×1 | Frise des prochains passages | Les deux sens, en compact |
  | 4×2 | Liste des prochains passages | Les deux sens avec frise ; liste pour les trains |
  | 4×3 et plus | Liste | Liste |

  Le temps d'attente indique « 3 min », « Imminent », « À quai » ou « Supprimé » ; les retards et
  les échecs d'actualisation sont signalés en couleur. La liste affiche aussi l'heure, la voie et le
  code mission des trains.
- **Couleur du widget**, au choix : automatique (couleur de la ligne pour les formats à grand chiffre,
  fond sombre pour les autres), sombre, clair ou couleur de la ligne. La liste reste en sombre ou en
  clair.
- **Actualisation** : toucher le widget l'actualise immédiatement ; actualisation automatique
  configurable (manuelle, 1, 2, 5, 10, 15 ou 30 min) **uniquement écran allumé** (alarme non
  réveillante : aucune requête quand le téléphone est en veille). Les temps d'attente sont
  recalculés chaque minute sans requête réseau.
- Plusieurs widgets possibles (un arrêt/sens différent par widget), modifiables via l'écran principal
  ou l'option « Reconfigurer » du lanceur (Android 12+).

## Installation

1. Télécharger l'APK :
   - onglet **Actions** du dépôt → dernier build « Build APK » → artefact `TempsReelIDF-apk` ;
   - ou, pour une version taguée `vX.Y.Z`, la page **Releases**.
2. Sur le téléphone, autoriser l'installation d'applications de sources inconnues puis ouvrir l'APK.
3. Android 8.0 (API 26) minimum.

## Obtenir une clé API PRIM

1. Créer un compte gratuit sur <https://prim.iledefrance-mobilites.fr>.
2. Dans votre espace, générer un **jeton d'authentification** (clé API) et le copier.
3. Le coller dans l'application (« Clé API PRIM » → Enregistrer → Tester).

Chaque actualisation d'un widget consomme **une requête par point d'arrêt** retenu (en général une
seule une fois le sens choisi). Adaptez la fréquence d'actualisation à votre quota PRIM.

## Utilisation

1. Ouvrir l'application et enregistrer la clé API.
2. Ajouter le widget « Prochains passages » sur l'écran d'accueil (appui long → Widgets → Temps réel
   IDF, ou bouton « Ajouter un widget » de l'application).
3. Choisir le mode, la ligne, l'arrêt puis le ou les sens, et enregistrer.

## Détails techniques

| Élément | Source |
| --- | --- |
| Temps réel | `GET https://prim.iledefrance-mobilites.fr/marketplace/stop-monitoring?MonitoringRef=…&LineRef=STIF:Line::Cxxxxx:` avec l'en-tête `apikey` |
| Lignes | `https://data.iledefrance-mobilites.fr/api/explore/v2.1/catalog/datasets/referentiel-des-lignes/exports/json` |
| Arrêts d'une ligne | `https://data.iledefrance-mobilites.fr/api/explore/v2.1/catalog/datasets/arrets-lignes/exports/json` |

- Les identifiants du référentiel sont convertis en `MonitoringRef` SIRI :
  `IDFM:monomodalStopPlace:58879` (zone d'arrêt, lignes ferrées) → `STIF:StopArea:SP:58879:`,
  `IDFM:463158` (arrêt/quai) → `STIF:StopPoint:Q:463158:`. Si PRIM refuse un identifiant ou le couple
  MonitoringRef/LineRef, le client essaie l'autre format puis la requête sans `LineRef` (filtrage de
  la ligne en local).
- Les arrêts de même nom dans la même commune (un quai par sens) sont regroupés ; le choix du sens
  détermine le(s) quai(s) interrogé(s) et, pour les zones d'arrêt ferroviaires, les directions
  (`DirectionName`, sinon `DestinationName`) retenues.
- Application 100 % Kotlin, sans AndroidX : widget dessiné en image (`Canvas`, car Android ignore les
  polices de l'application dans le texte natif d'un widget) et transmis via `RemoteViews`, interface
  en vues Android natives, coroutines Kotlin, `HttpURLConnection` + `org.json`.

```
app/src/main/java/fr/trprim/siri/
├── data/     modèles, client PRIM (SIRI), parseur SIRI Lite, référentiel IDFM, sens, préférences
├── widget/   AppWidgetProvider, choix du format et des couleurs, dessin, actualisation et planification
└── ui/       écran principal (clé, fréquence, widgets) et assistant de configuration
```

## Compiler

Prérequis : JDK 17 et SDK Android (plateforme 35).

```bash
./gradlew testDebugUnitTest   # tests unitaires (parseur SIRI, sens, référentiel, client PRIM)
./gradlew assembleRelease     # APK : app/build/outputs/apk/release/app-release.apk
```

### Signature

Les APK de release sont signés avec une **clé privée qui n'est jamais stockée dans le dépôt**.
En local, définir avant le build `RELEASE_KEYSTORE_FILE`, `RELEASE_KEYSTORE_PASSWORD`,
`RELEASE_KEY_ALIAS` et `RELEASE_KEY_PASSWORD` (sinon l'APK de release est produit non signé).

En CI, la clé est lue depuis quatre secrets du dépôt (Settings → Secrets and variables → Actions) :

| Secret | Contenu |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | le fichier keystore encodé en base64 (`base64 -w0 release.keystore`) |
| `RELEASE_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `RELEASE_KEY_ALIAS` | alias de la clé |
| `RELEASE_KEY_PASSWORD` | mot de passe de la clé |

Créer la clé (une seule fois, à conserver précieusement : sans elle, impossible de publier des mises à
jour de l'application) :

```bash
keytool -genkeypair -v -keystore release.keystore -storetype PKCS12 \
  -alias tempsreelidf -keyalg RSA -keysize 4096 -validity 10000
```

Sans ces secrets (par exemple pour une pull request venant d'un fork), la CI produit seulement un APK
de debug ; un tag `v*` échoue volontairement plutôt que de publier un APK non signé.

Le workflow GitHub Actions `.github/workflows/android.yml` exécute les tests, construit l'APK à chaque
push (artefact téléchargeable) et publie une release pour chaque tag `v*`.

## Licence

Code sous licence [MIT](LICENSE). Les contributions sont les bienvenues (issues et pull requests).

Le widget utilise la police [Barlow](https://github.com/jpt/barlow), sous licence SIL Open Font
License 1.1 ([texte de la licence](app/src/main/assets/licenses/barlow-OFL.txt)).

## Données

Données © Île-de-France Mobilités : API PRIM (licence Mobilités) et référentiels open data
(licence ODbL). Cette application n'est pas affiliée à Île-de-France Mobilités.
