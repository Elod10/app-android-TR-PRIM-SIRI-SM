# Temps réel IDF — widget Android (Tracéo / PRIM)

Application Android (APK) fournissant un **widget d'écran d'accueil** qui affiche les prochains
passages d'une ligne à un arrêt d'Île-de-France. Il fonctionne **sans clé ni compte**, avec deux sources
de données :

| | Sans clé (par défaut) | Avec une clé API PRIM |
|---|---|---|
| Lignes et arrêts | [Tracéo](https://gotraceo.fr) | Open data IDFM |
| Passages | Relevé SIRI ET de Tracéo, renouvelé environ toutes les 2 minutes | PRIM en SIRI *StopMonitoring*, appelé par le téléphone |
| Heure affichée | Heure du relevé, avec la mention « Tracéo » | Heure de la dernière actualisation |
| Actualisation la plus rapide | 2 minutes | 1 minute |
| Voie, câble et funiculaire | Non | Oui |

## Fonctionnalités

- **Clé API PRIM facultative**, saisie dans l'application (écran principal ou lien sous la liste des
  modes), avec bouton « Tester ». Elle est stockée uniquement sur le téléphone (sauvegarde Android
  désactivée) et n'est jamais envoyée à Tracéo. Vider le champ et enregistrer la retire : les widgets
  reviennent sur Tracéo.
- **Choix de l'arrêt via un menu en 4 étapes** : mode de transport → ligne → arrêt → sens.
  - Modes : Métro, RER, Train/Transilien, Tramway, Bus/Noctilien, et Câble/Funiculaire avec une clé.
  - Lignes et arrêts de Tracéo ou, avec une clé, des référentiels open data IDFM
    (`referentiel-des-lignes`, `arrets-lignes`), avec recherche, pastilles aux couleurs officielles et
    cache local de 7 jours.
  - **Sens** : l'application lit les prochains passages de l'arrêt choisi et propose les sens observés
    (« Vers Porte de Clignancourt »…), ou « Tous les sens ». Plusieurs sens/destinations peuvent être
    cochés (utile pour les branches du RER), sauf sur les widgets qui n'affichent qu'un sens : Pastille,
    Un sens, Grand chiffre, Frise et Train et voie.
- **Dix widgets au choix**, depuis la liste des widgets du lanceur ou l'application, tous
  redimensionnables :

  | Widget | Taille de départ | Contenu |
  |---|---|---|
  | Pastille | 1×1 | Le prochain passage, en grand |
  | Un sens | 2×1 | Le prochain passage et le suivant |
  | Grand chiffre | 2×2 | Le prochain passage en très grand, les suivants sur une frise |
  | Train et voie | 2×2 | Le prochain train et sa voie |
  | Frise | 4×1 | Les prochains passages d'un sens sur une frise |
  | Deux sens compact | 4×1 | Les deux sens sur deux lignes |
  | Deux sens | 2×2 | Les deux sens l'un au-dessus de l'autre |
  | Deux sens avec frise | 4×2 | Les deux sens côte à côte, avec leur frise |
  | Liste des départs | 4×2 | Mission, voie et heure ; s'allonge pour afficher plus de départs |
  | Automatique | 4×2 | Choisit l'un de ces formats selon la taille du widget et le sens suivi |

  Le temps d'attente indique « 3 min », « Imminent », « À quai » ou « Supprimé » ; les retards et
  les échecs d'actualisation sont signalés en couleur. La liste affiche aussi l'heure, la voie (avec
  une clé) et le code mission des trains.
- **Relevé daté** : sans clé, le widget indique l'heure du relevé et la mention « Tracéo », pas l'heure
  du téléphone, et signale un relevé ancien (plus de 150 s, ce qui est normal entre 2 h et 5 h). Le
  format Pastille manque de place : il n'indique que l'heure, et la mention reste dans sa description
  pour les lecteurs d'écran.
- **Couleur du widget**, au choix : automatique (couleur de la ligne pour les formats à grand chiffre,
  fond sombre pour les autres), sombre, clair ou couleur de la ligne. La liste reste en sombre ou en
  clair.
- **Actualisation** : toucher le widget l'actualise immédiatement ; actualisation automatique
  configurable (manuelle, 1, 2, 5, 10, 15 ou 30 min) **uniquement écran allumé** (alarme exacte mais
  non réveillante : aucune requête quand le téléphone est en veille). Sans clé, « 1 min » vaut 2 min,
  car le relevé de Tracéo ne change pas plus vite. Les temps d'attente sont recalculés chaque minute
  sans requête réseau.
- Plusieurs widgets possibles (un arrêt/sens différent par widget), modifiables via l'écran principal
  ou l'option « Reconfigurer » du lanceur (Android 12+).

## Installation

1. Télécharger l'APK :
   - onglet **Actions** du dépôt → dernier build « Build APK » → artefact `TempsReelIDF-apk` ;
   - ou, pour une version taguée `vX.Y.Z`, la page **Releases**.
2. Sur le téléphone, autoriser l'installation d'applications de sources inconnues puis ouvrir l'APK.
3. Android 8.0 (API 26) minimum.

## Obtenir une clé API PRIM (facultatif)

La clé n'est pas nécessaire. Elle fait interroger PRIM directement par le téléphone, ajoute la voie, le
câble et le funiculaire, et permet une actualisation chaque minute.

1. Créer un compte gratuit sur <https://prim.iledefrance-mobilites.fr>.
2. Dans votre espace, générer un **jeton d'authentification** (clé API) et le copier.
3. Le coller dans l'application (« Clé API PRIM » → Enregistrer → Tester).

Chaque actualisation d'un widget consomme **une requête par point d'arrêt** retenu (en général une
seule une fois le sens choisi). Adaptez la fréquence d'actualisation à votre quota PRIM.

Un widget créé avec une clé porte sur un arrêt du référentiel IDFM, inconnu de Tracéo : si la clé est
retirée, il demande à être reconfiguré. Un widget créé sans clé passe sur PRIM dès qu'une clé est
ajoutée et garde son sens.

## Utilisation

1. Ajouter le format de son choix sur l'écran d'accueil : appui long → Widgets → Temps réel IDF, ou
   bouton « Ajouter un widget » de l'application.
2. Choisir le mode, la ligne, l'arrêt puis le ou les sens, et enregistrer.

## Confidentialité

- **Sans clé**, le téléphone envoie à gotraceo.fr la ligne et l'arrêt demandés, et son adresse IP figure
  dans les journaux du serveur, conservés 14 jours. Pas de compte ni d'identifiant d'appareil.
- **Avec une clé**, le téléphone interroge directement PRIM et l'open data IDFM. La clé n'est jamais
  envoyée à Tracéo.

## Détails techniques

| Élément | Sans clé | Avec une clé |
| --- | --- | --- |
| Passages | `GET https://gotraceo.fr/api/widget/passages?line=line:IDFM:Cxxxxx&stop=stop_area:IDFM:xxxxx` | `GET https://prim.iledefrance-mobilites.fr/marketplace/stop-monitoring?MonitoringRef=…&LineRef=STIF:Line::Cxxxxx:` avec l'en-tête `apikey` |
| Lignes | `GET https://gotraceo.fr/api/lines` | `https://data.iledefrance-mobilites.fr/api/explore/v2.1/catalog/datasets/referentiel-des-lignes/exports/json` |
| Arrêts d'une ligne | `GET https://gotraceo.fr/api/stations?line=line:IDFM:Cxxxxx`, à la configuration seulement | `https://data.iledefrance-mobilites.fr/api/explore/v2.1/catalog/datasets/arrets-lignes/exports/json` |

- Les identifiants du référentiel sont convertis en `MonitoringRef` SIRI :
  `IDFM:monomodalStopPlace:58879` (zone d'arrêt, lignes ferrées) → `STIF:StopArea:SP:58879:`,
  `IDFM:463158` (arrêt/quai) → `STIF:StopPoint:Q:463158:`. Si PRIM refuse un identifiant ou le couple
  MonitoringRef/LineRef, le client essaie l'autre format puis la requête sans `LineRef` (filtrage de
  la ligne en local). Une zone d'arrêt de Tracéo, `stop_area:IDFM:71348`, devient
  `STIF:StopArea:SP:71348:`.
- Sur la source Tracéo, les arrivées au terminus sont retirées. Un 429 ou un 503 garde les dernières
  données ; un 503 redemande au plus une fois par ligne et par processus les arrêts de la ligne, puis
  réessaie une fois. Un 404 demande de reconfigurer le widget.
- Les arrêts de même nom dans la même commune (un quai par sens) sont regroupés ; le choix du sens
  détermine le(s) quai(s) interrogé(s) et, pour les zones d'arrêt ferroviaires, les directions
  (`DirectionName`, sinon `DestinationName`) retenues.
- Application 100 % Kotlin, sans AndroidX : widget dessiné en image (`Canvas`, car Android ignore les
  polices de l'application dans le texte natif d'un widget) et transmis via `RemoteViews`, interface
  en vues Android natives, coroutines Kotlin, `HttpURLConnection` + `org.json`.

```
app/src/main/java/fr/trprim/siri/
├── data/     modèles, clients Tracéo et PRIM (SIRI), choix de la source, parseur SIRI Lite, référentiel IDFM, sens, préférences
├── widget/   AppWidgetProvider, choix du format et des couleurs, dessin, actualisation et planification
└── ui/       écran principal (clé, fréquence, widgets) et assistant de configuration
```

## Compiler

Prérequis : JDK 17 et SDK Android (plateforme 35).

```bash
./gradlew testDebugUnitTest   # tests unitaires (parseur SIRI, sens, référentiel, clients PRIM et Tracéo)
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

Données © Île-de-France Mobilités, via Tracéo (gotraceo.fr).

Selon la page Licences de PRIM, le temps réel relève de la Licence Mobilité, les lignes et arrêts de la
licence ouverte Etalab ou de l'ODbL. Avec une clé, les données viennent directement de l'API PRIM et des
référentiels open data IDFM. Cette application n'est affiliée ni à Île-de-France Mobilités ni à Tracéo.
