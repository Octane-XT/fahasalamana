# Fahasalamana Zaza

Application Android qui tient le carnet de vaccination d'un enfant : on saisit un prénom et
une date de naissance, et l'application en déduit l'échéancier complet des vaccins, montre
ce qui est fait, ce qui arrive et ce qui est en retard, puis pose un rappel par notification
avant chaque échéance. Tout reste sur le téléphone — aucun compte, aucun serveur, aucune
donnée de santé qui parte sur le réseau — et l'application fonctionne entièrement hors
ligne. Elle embarque en plus un annuaire des centres de santé de base de Madagascar, pour
savoir où aller.

*Fahasalamana zaza* : « la santé de l'enfant », en malgache.

> ### ⚠️ Données de démonstration
>
> **Le calendrier vaccinal embarqué est une démonstration, pas une source officielle.** Il
> a la forme d'un vrai calendrier — 16 vaccins, âges d'administration, intervalles entre
> doses — mais il a été produit par un script (`tools/`) pour les besoins d'un projet
> universitaire et **il n'a été validé par aucune autorité de santé**.
>
> L'annuaire des centres est **fictif** lui aussi : les noms de régions et de districts sont
> réels, les centres, adresses, horaires et numéros de téléphone sont inventés.
>
> L'application ne donne aucun avis médical et ne pose aucun diagnostic. Elle calcule des
> dates à partir d'un calendrier qu'on lui fournit, et c'est tout. Avant tout usage réel, il
> faudrait remplacer le calendrier par celui du Ministère de la Santé Publique (voir
> [Publier les données de référence](#publier-les-données-de-référence)) — cette mention
> apparaît d'ailleurs dans l'application elle-même, dans l'écran Réglages.

---

## Sommaire

- [Ce qu'il faut sur la machine](#ce-quil-faut-sur-la-machine)
- [Compiler et installer](#compiler-et-installer)
- [Lancer les tests](#lancer-les-tests)
- [Signer une release](#signer-une-release)
- [Installer l'APK sur un téléphone](#installer-lapk-sur-un-téléphone)
- [Publier les données de référence](#publier-les-données-de-référence)
- [Numérotation des versions](#numérotation-des-versions)
- [Données de santé et vie privée](#données-de-santé-et-vie-privée)
- [Organisation du code](#organisation-du-code)

---

## Ce qu'il faut sur la machine

| Outil | Version | Remarque |
|---|---|---|
| JDK | **17** | Exigé par le plugin Android 8.7. `java -version` doit afficher 17. |
| SDK Android | **API 35** installé | `compileSdk`/`targetSdk` du projet. |
| Android Studio | Ladybug (2024.2) ou plus récent | Facultatif : tout se fait en ligne de commande. |
| Téléphone | **Android 8.0 (API 26)** ou plus | `minSdk = 26`. Téléphone en portrait ; pas de tablette. |

Gradle n'a pas besoin d'être installé : le dépôt contient son wrapper (`./gradlew`).

En ligne de commande, Gradle doit savoir où est le SDK. Soit la variable d'environnement
`ANDROID_HOME` pointe dessus, soit un fichier `local.properties` non versionné le déclare à
la racine du dépôt :

```properties
sdk.dir=/chemin/vers/Android/Sdk
```

Android Studio écrit ce fichier tout seul à la première ouverture du projet.

## Compiler et installer

```bash
./gradlew assembleDebug        # APK de démonstration, signé avec la clé de debug
./gradlew installDebug         # le même, poussé sur le téléphone branché en USB
```

L'APK produit est `app/build/outputs/apk/debug/app-debug.apk`.

**Le build debug n'est pas le build livré.** Il plafonne volontairement le délai des
rappels à une minute, pour qu'une notification programmée dans trois mois puisse être
montrée en démonstration sans attendre. Ce raccourci vit dans un fichier propre au source
set `src/debug/` et n'existe pas dans l'APK de release.

## Lancer les tests

```bash
./gradlew testDebugUnitTest          # tests JVM : règles métier, échéancier, export/import
./gradlew connectedDebugAndroidTest  # tests instrumentés : base de données, rappels
./gradlew lint                       # analyse statique Android
```

Les tests JVM ne demandent ni téléphone ni émulateur : c'est là que vit l'essentiel de la
logique, volontairement écrite sans dépendance à Android. Les tests instrumentés, eux,
demandent un appareil branché ou un émulateur démarré.

L'intégration continue lance `lint`, les tests JVM et l'APK debug sur chaque pull request,
et publie l'APK debug en artefact. Elle ne lance pas les tests instrumentés, qui demandent
un appareil.

## Signer une release

Le keystore de l'application et ses mots de passe **ne sont pas dans le dépôt, et ne doivent
jamais y entrer** : `.gitignore` exclut déjà `*.jks`, `*.keystore` et `keystore.properties`.
Un keystore publié, c'est la possibilité pour n'importe qui de signer une fausse mise à jour
de l'application.

La configuration Gradle lit ses paramètres dans un fichier `keystore.properties` placé à la
racine du dépôt. **S'il est absent, rien ne casse** : `assembleDebug`, les tests et
l'intégration continue fonctionnent normalement. Seul un build de release s'arrête, avec un
message qui explique quoi faire — plutôt que de produire en silence un `app-release-unsigned.apk`
qu'aucun téléphone n'installera.

### 1. Créer le keystore

Une seule fois pour toute la vie du projet. `keytool` est fourni avec le JDK.

```bash
keytool -genkeypair -v \
  -keystore fahasalamana-release.jks \
  -alias fahasalamana \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

`keytool` demande alors un mot de passe pour le fichier, puis quelques informations
d'identité (nom, unité, organisation, ville, pays) qui finiront dans le certificat.

Conserver ce fichier **et** ses mots de passe ailleurs que dans le dépôt, et en double : le
perdre signifie qu'aucune mise à jour ne pourra plus être installée par-dessus une version
déjà distribuée. Android n'accepte une mise à jour que si elle est signée par la même clé.

### 2. Déclarer ses paramètres

Créer `keystore.properties` à la racine du dépôt :

```properties
storeFile=fahasalamana-release.jks
storePassword=<mot de passe du fichier>
keyAlias=fahasalamana
keyPassword=<mot de passe de la clé>
```

`storeFile` se lit depuis la racine du dépôt ; un chemin absolu marche aussi, et c'est plus
prudent si le keystore est rangé hors du projet. Les quatre clés sont obligatoires : s'il en
manque une, le build s'arrête en la nommant.

### 3. Construire

```bash
./gradlew assembleRelease
```

Produit :

- `app/build/outputs/apk/release/app-release.apk` — l'APK signé, à installer ;
- `app/build/outputs/mapping/release/mapping.txt` — la table de correspondance de R8.

**Garder `mapping.txt` avec chaque APK distribué.** Le build de release passe par R8, qui
supprime le code inutilisé et renomme le reste : sans ce fichier, une trace de plantage
remontée d'un téléphone est illisible. Un APK et son `mapping.txt` vont ensemble.

## Installer l'APK sur un téléphone

Par USB, téléphone en mode développeur avec le débogage USB activé :

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Sans câble : copier l'APK sur le téléphone (clé USB, Bluetooth, carte SD), l'ouvrir depuis
le gestionnaire de fichiers et autoriser l'installation d'applications de cette source quand
Android le demande.

Deux points qui coincent souvent :

- **« Application non installée » alors que tout semble correct** : un APK de la même
  application est déjà installé, signé par une autre clé — typiquement le build debug.
  Désinstaller d'abord (`adb uninstall mg.univ.fahasalamana`). Le carnet de ce téléphone est
  alors perdu : penser à l'exporter avant.
- **Les rappels n'arrivent pas** : Android 13 et plus demandent une autorisation pour les
  notifications ; l'application la demande à l'ajout du premier enfant. Si elle a été
  refusée, elle se réactive dans les réglages système de l'application. Vérifier aussi que
  l'application n'est pas soumise à une restriction d'économie de batterie agressive, ce que
  certains constructeurs appliquent par défaut.

## Publier les données de référence

Le calendrier vaccinal et l'annuaire des centres sont deux fichiers JSON. Chacun existe en
deux exemplaires, **au même format** :

- une copie embarquée dans `app/src/main/assets/`, chargée au premier démarrage, qui rend
  l'application utilisable sans réseau dès l'installation ;
- une copie publiée sur un dépôt GitHub public séparé, `fahasalamana-data`, que
  l'application sait aller relire depuis l'écran Réglages (« Vérifier les mises à jour »).

Les deux fichiers ne sont **jamais écrits à la main** : ils sont produits par les scripts
Python de `tools/`, sans dépendance et à graine fixe. La marche à suivre complète — options
des scripts, incrémentation du numéro de version, règles à ne pas casser — est dans
[`tools/README.md`](tools/README.md).

En deux lignes :

```bash
python tools/generer_calendrier.py --version 2 --publie-le 2026-10-01
python tools/generer_csb.py        --version 2 --publie-le 2026-10-01
```

puis copier les deux fichiers produits dans `v1/` du dépôt de données. L'application ne
remplace son contenu local que si le `version` distant est **strictement supérieur** au
sien ; publier sans l'incrémenter ne produit aucun effet visible.

Le remplacement se fait en une transaction et ne touche jamais les enfants ni les vaccins
saisis : mettre à jour le calendrier ne peut pas abîmer un carnet.

> **À faire avant la première publication.** L'URL du dépôt de données contient encore le
> marqueur `ORGANISATION-A-DEFINIR` (dans `data/remote/ReferenceApi.kt`). Tant qu'il est là,
> « Vérifier les mises à jour » échoue proprement, sans rien changer en base. Créer le dépôt
> public, y publier `v1/calendrier.json` et `v1/csb.json`, puis remplacer ce segment.

## Numérotation des versions

Le `versionName` est celui du jalon de démonstration, et le `versionCode` en est déduit
dans `app/build.gradle.kts` :

```
versionCode = majeure × 100 + mineure × 10 + correctif
```

| Tag | `versionName` | `versionCode` |
|---|---|---|
| `v0.1-socle` | 0.1 | 10 |
| `v0.2-carnet` | 0.2 | 20 |
| `v0.3-mvp` | 0.3 | 30 |
| `v1.0-soutenance` | 1.0 | 100 |
| correction après coup | 1.0.1 | 101 |

Le déduire au lieu de tenir deux nombres côte à côte évite la seule panne vraiment pénible :
republier un APK sous un `versionCode` déjà utilisé, qu'Android refuse d'installer par-dessus
le précédent sans dire pourquoi. À chaque jalon, il n'y a que trois entiers à changer, en
tête de `app/build.gradle.kts`.

## Données de santé et vie privée

Un carnet de vaccination est une donnée de santé qui concerne un enfant. Les choix du projet
en découlent, et ils sont vérifiables dans le code :

- **Rien ne part sur le réseau.** La permission `INTERNET` ne sert qu'à télécharger les deux
  fichiers de référence, qui sont du contenu public. Aucune requête ne transporte un enfant
  ou un vaccin ; il n'y a ni compte, ni serveur applicatif, ni télémétrie.
- **Aucune sauvegarde automatique.** `android:allowBackup="false"`, et
  `res/xml/data_extraction_rules.xml` exclut la base de l'application de la sauvegarde cloud
  **et** du transfert d'appareil à appareil d'Android 12+. Le carnet ne se recopie pas tout
  seul sur un téléphone neuf.
- **La seule sortie est explicite.** L'export écrit un fichier JSON à l'emplacement que
  l'utilisateur choisit lui-même, par le sélecteur de documents du système. L'application ne
  demande aucune permission de stockage et n'envoie ce fichier nulle part. Il est en clair,
  pour rester lisible par son propriétaire : à conserver en lieu sûr.
- **L'import fusionne par identifiant**, sans créer de doublon ni écraser ce qui est déjà là.

## Organisation du code

Un seul module, `app`, organisé par couche puis par écran.

```
app/src/main/java/mg/univ/fahasalamana/
├── App.kt, MainActivity.kt      point d'entrée, injection, thème
├── di/                          modules Koin
├── ui/                          thème, navigation, et un paquet par écran
│                                (XxxScreen.kt + XxxViewModel.kt + XxxUiState.kt)
├── domain/                      modèles et logique pure — ni Android, ni Room,
│                                testable en JVM (échéancier, export/import, validations)
├── data/local|remote|repository base Room, DataStore, accès réseau, dépôts
└── platform/                    ce qui touche au système : notifications, WorkManager,
                                 intents, sélecteur de documents

app/src/main/assets/             calendrier.json et csb.json embarqués
app/src/main/res/                textes (français), thème, icônes, règles de sauvegarde
app/schemas/                     schéma Room exporté par le build, à versionner :
                                 c'est lui qui permettra d'écrire une migration
tools/                           générateurs Python des données de référence
```

Quelques conventions qui expliquent ce qu'on lit :

- **Le métier est en français** (`Enfant`, `VaccinAdministre`, `CalculateurEcheancier`), ce
  qui vient d'Android reste en anglais. Les deux langues dans un même fichier ne sont pas un
  accident : elles marquent la frontière.
- **Aucune logique métier dans un écran ni dans un ViewModel.** Elle vit dans `domain/`,
  sous forme de fonctions pures. C'est ce qui rend l'essentiel testable sans téléphone.
- **Les dates sont typées** (`LocalDate`, `LocalTime`), jamais des chaînes ni des entiers.
  Fuseau `Indian/Antananarivo`.
- **Tous les textes affichés sont dans `res/values/strings_*.xml`**, en français. Le ton est
  factuel : « Prévu le… », « À faire dès que possible » — jamais un reproche au parent.
- **La navigation est typée** : les huit destinations sont des classes `@Serializable`, il
  n'y a aucune route écrite sous forme de chaîne.

---

Projet universitaire de fin de parcours, réalisé en binôme. Application, données et annuaire
sont à usage pédagogique.
