# Temps réel IDF — widget Android (Tracéo / PRIM)

Application Android (APK) fournissant un **widget d'écran d'accueil** qui affiche les prochains
passages d'une ligne à un arrêt d'Île-de-France. Il fonctionne **sans clé ni compte**. Les passages
viennent toujours de **PRIM**, la plateforme d'Île-de-France Mobilités, par l'un de ces deux accès :

| | Sans clé (par défaut) | Avec une clé API PRIM |
|---|---|---|
| Lignes et arrêts | [Tracéo](https://gotraceo.fr) | Open data IDFM |
| Passages | PRIM via le cache de Tracéo : Tracéo interroge PRIM en SIRI ET et met ses réponses en cache, relevé renouvelé environ toutes les 2 minutes | PRIM en direct, en SIRI *StopMonitoring*, appelé par le téléphone |
| Heure affichée | Heure du relevé, avec « PRIM via Tracéo » si la place le permet, sinon « Tracéo » | Heure de la dernière actualisation |
| Actualisation la plus rapide | 2 minutes | 1 minute |
| Voie, câble et funiculaire | Non | Oui |

## Fonctionnalités

- **Clé API PRIM facultative**, saisie dans l'application (Réglages ou lien sous la liste des
  modes), avec bouton « Tester ». Elle est stockée uniquement sur le téléphone (sauvegarde Android
  désactivée) et n'est jamais envoyée à Tracéo. Vider le champ et enregistrer la retire : les widgets
  reviennent sur le cache de Tracéo. Les réglages indiquent la source active.
- **Mode automatique, selon la position** (première ligne de la liste des modes), compatible avec tous
  les formats et utilisable sans clé :
  - toucher le widget relit la position et affiche la ligne en tête du classement ;
  - s'il y a d'autres lignes, le widget l'indique pendant 4 secondes (« +3 lignes ») ;
  - deux touchers en moins de 1,5 seconde ouvrent la liste des lignes proches, où l'on choisit une ligne,
    mémorisée pour ce lieu, et où l'on marque ses favoris ;
  - à moins de 100 m de l'endroit où l'arrêt a été choisi, ou dans la marge d'erreur du GPS, l'arrêt ne
    change pas et seuls les passages sont actualisés ;
  - **classement** : le choix fait ici, puis les lignes favorites à moins de 800 m, puis la distance, où le
    métro, le RER et le train comptent 150 m de moins et le tram 75 m. Chaque ligne n'apparaît qu'une fois,
    à son arrêt le plus proche. On garde ce qui reste à moins de 400 m de plus que l'arrêt le plus proche,
    sans dépasser 2 km. Les Noctiliens ne comptent que de 0 h 30 à 5 h 30. En tête, une ligne sans passage
    dans l'heure cède sa place à la suivante (3 lignes interrogées au plus) ;
  - la position n'est jamais lue en arrière-plan : les actualisations automatiques gardent le dernier arrêt.
- **Choix de l'arrêt via un menu en 4 étapes** : mode de transport → ligne → arrêt → sens.
  - Modes : Métro, RER, Train/Transilien, Tramway, Bus/Noctilien, et Câble/Funiculaire avec une clé.
  - Lignes et arrêts de Tracéo ou, avec une clé, des référentiels open data IDFM
    (`referentiel-des-lignes`, `arrets-lignes`), avec recherche, pastilles aux couleurs officielles et
    cache local de 7 jours.
  - **Sens** : l'application lit les prochains passages de l'arrêt choisi et propose les sens observés
    (« Vers Porte de Clignancourt »…), ou « Tous les sens ». Plusieurs sens/destinations peuvent être
    cochés (utile pour les branches du RER), sauf sur les widgets qui n'affichent qu'un sens : Pastille,
    Un sens, Grand chiffre, Frise et Train et voie.
- **Onze widgets au choix**, depuis la liste des widgets du lanceur ou l'application, tous
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
  | À proximité | 4×2 | Les lignes près de vous, avec le prochain passage et le suivant dans les deux sens ; s'allonge pour afficher plus de lignes |

  Le temps d'attente indique « 3 min », « Imminent », « À quai » ou « Supprimé » ; les retards et
  les échecs d'actualisation sont signalés en couleur. La liste affiche aussi l'heure, la voie (avec
  une clé) et le code mission des trains.
- **Relevé daté** : sans clé, le widget indique l'heure du relevé de Tracéo, pas celle du téléphone, avec
  la mention « PRIM via Tracéo » si la place le permet, sinon « Tracéo ». Il signale un relevé ancien
  (plus de 150 s, ce qui est normal entre 2 h et 5 h). Le format Pastille manque de place : il n'indique
  que l'heure, et la mention reste dans sa description pour les lecteurs d'écran.
- **Couleur du widget** : couleur de la ligne par défaut, ou au choix automatique (sombre ou clair selon
  le thème du système), sombre ou clair, y compris en mode automatique, où la couleur suit la ligne
  affichée. La liste reste en sombre ou en clair et démarre en sombre. Le widget À proximité suit le thème
  clair ou sombre du système.
- **Widget À proximité** : toujours automatique, il n'y a rien à configurer en dehors de la permission. Il
  garde le classement du mode automatique et les mêmes gestes : un toucher relit la position, deux touchers
  en moins de 1,5 seconde ouvrent la liste complète. Deux lignes tiennent en 4×2, trois en 4×3. Une ligne
  sans passage dans l'heure laisse sa place à la suivante, sauf si elle est favorite ou choisie à cet endroit.
- **Actualisation** : toucher le widget l'actualise immédiatement ; actualisation automatique
  configurable (manuelle, 1, 2, 5, 10, 15 ou 30 min) **uniquement écran allumé** (alarme exacte mais
  non réveillante : aucune requête quand le téléphone est en veille). Sans clé, le choix de 1 min n'est
  pas proposé, car le cache de Tracéo ne change pas plus vite ; il revient avec une clé. Les temps
  d'attente sont recalculés chaque minute sans requête réseau.
- Plusieurs widgets possibles (un arrêt/sens différent par widget), affichés sur l'accueil de
  l'application et modifiables depuis celui-ci ou par l'option « Reconfigurer » du lanceur (Android 12+).

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
3. Le coller dans l'application (Réglages → « Ajouter une clé API PRIM » → Enregistrer → Tester).

Chaque actualisation d'un widget consomme **une requête par point d'arrêt** retenu (en général une
seule une fois le sens choisi). Adaptez la fréquence d'actualisation à votre quota PRIM.

Un widget créé avec une clé porte sur un arrêt du référentiel IDFM, inconnu de Tracéo : si la clé est
retirée, il demande à être reconfiguré. Un widget créé sans clé passe sur PRIM dès qu'une clé est
ajoutée et garde son sens.

## Utilisation

1. Ajouter le format de son choix sur l'écran d'accueil : appui long → Widgets → Temps réel IDF, ou
   bouton « Ajouter un widget » de l'application. Dans les deux cas, la configuration s'ouvre ensuite.
2. Choisir le mode, la ligne, l'arrêt, le ou les sens et la couleur, puis enregistrer. En mode
   automatique : choisir la ligne proche puis la couleur, tous les sens étant affichés.

## Confidentialité

- **Sans clé**, le téléphone envoie à gotraceo.fr la ligne et l'arrêt demandés, et son adresse IP figure
  dans les journaux du serveur, conservés 14 jours. Pas de compte ni d'identifiant d'appareil.
- **Avec une clé**, le téléphone interroge directement PRIM et l'open data IDFM. La clé n'est jamais
  envoyée à Tracéo.
- **Position** (mode automatique uniquement) : elle est lue seulement quand on touche le widget, sans la
  permission « Toujours autoriser ». Les arrêts proches sont cherchés dans un fichier local : la position
  n'est envoyée à personne. Seuls les lignes et les arrêts retenus partent vers la source de passages, comme
  pour un widget ordinaire. La dernière position et les lieux où une ligne a été choisie restent sur le
  téléphone.

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
- **Requêtes du widget À proximité** : les lignes sont interrogées dans l'ordre du classement, jusqu'à remplir
  le widget et au plus 8 par actualisation. Sans clé, cela fait une requête par ligne interrogée, le cas
  échéant précédée du chargement de ses arrêts chez Tracéo. Avec une clé, une seule requête par zone d'arrêt
  suffit, car PRIM renvoie toutes les lignes de la zone.
- **Index local des arrêts** : le téléphone télécharge les colonnes utiles de `arrets-lignes`, `arrets`,
  `zones-d-arrets` et `referentiel-des-lignes` (environ 3 Mo compressés, sans quota PRIM ni appel à Tracéo),
  puis les compile en un fichier d'environ 2 Mo. Il est renouvelé chaque semaine en Wi-Fi, chaque mois sur
  le réseau mobile, et l'ancien reste utilisé en cas d'échec. Une zone (`arrid` → `zdaid` → `zdcid`) devient
  `stop_area:IDFM:zdcid`, compris par Tracéo et par PRIM. Le temps de lecture de la position est écrit dans
  les journaux Android : `adb logcat -s TRWidget`.
- Application 100 % Kotlin, sans AndroidX : widget dessiné en image (`Canvas`, car Android ignore les
  polices de l'application dans le texte natif d'un widget) et transmis via `RemoteViews`, interface
  en vues Android natives, coroutines Kotlin, `HttpURLConnection` + `org.json`.

```
app/src/main/java/fr/trprim/siri/
├── data/     modèles, clients Tracéo et PRIM (SIRI), choix de la source, parseur SIRI Lite, référentiel IDFM, sens, préférences, index des arrêts, règles du mode automatique, position
├── widget/   AppWidgetProvider, choix du format et des couleurs, dessin, actualisation et planification, mode automatique et son service
└── ui/       accueil, galerie des formats, réglages, clé API, sources et licences, assistant de configuration et feuille de choix des lignes proches
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

Données © Île-de-France Mobilités, via [Tracéo](https://gotraceo.fr) (gotraceo.fr) : sans clé, Tracéo
interroge l'API PRIM et met ses réponses en cache.

- **Sans clé** : contient des informations de [PRIM](https://prim.iledefrance-mobilites.fr/)
  (Île-de-France Mobilités), présentement mises à disposition aux conditions de la
  [Licence Mobilités](https://cloud.fabmob.io/s/eYWWJBdM3fQiFNm) et de
  l'[Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/1-0/).
- **Avec une clé**, passages : contient des informations de
  [Prochains passages (PRIM)](https://prim.iledefrance-mobilites.fr/fr/apis/idfm-ivtr-requete_unitaire),
  aux mêmes conditions. L'usage d'une clé est soumis aux
  [conditions d'utilisation de PRIM](https://prim.iledefrance-mobilites.fr/fr/conditions-utilisation).
- **Avec une clé**, lignes et arrêts : contient des informations de
  [Référentiel des lignes](https://data.iledefrance-mobilites.fr/explore/dataset/referentiel-des-lignes/)
  et d'[Arrêts et lignes associées](https://data.iledefrance-mobilites.fr/explore/dataset/arrets-lignes/),
  présentement mises à disposition aux conditions de
  l'[Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/1-0/).
- **Mode automatique, avec ou sans clé**, arrêts proches : contient des informations
  d'[Arrêts et lignes associées](https://data.iledefrance-mobilites.fr/explore/dataset/arrets-lignes/),
  d'[Arrêts](https://data.iledefrance-mobilites.fr/explore/dataset/arrets/), de
  [Zones d'arrêts](https://data.iledefrance-mobilites.fr/explore/dataset/zones-d-arrets/) et du
  [Référentiel des lignes](https://data.iledefrance-mobilites.fr/explore/dataset/referentiel-des-lignes/),
  aux mêmes conditions. L'index compilé reste sur le téléphone : il n'est pas redistribué.

Cette application n'est pas affiliée à Île-de-France Mobilités. Sans clé, [Tracéo](https://gotraceo.fr) lui
fournit les données PRIM.
