import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// NUMÉROTATION DES VERSIONS (B24)
// ---------------------------------------------------------------------------
//
// Convention du binôme, calée sur les jalons de démonstration du CDC §B10.5. Le nom de
// version est celui du tag du jalon, débarrassé du préfixe et du libellé
// (`v0.2-carnet` -> « 0.2 »), et le code de version en est **déduit** au lieu d'être
// saisi à côté :
//
//     versionCode = majeure × 100 + mineure × 10 + correctif
//
//   | Jalon §B10.5 | Tag               | versionName | versionCode |
//   |--------------|-------------------|-------------|-------------|
//   | J1 socle     | `v0.1-socle`      | 0.1         |          10 |
//   | J2 carnet    | `v0.2-carnet`     | 0.2         |          20 |
//   | J3 MVP       | `v0.3-mvp`        | 0.3         |          30 |
//   | J4 soutenance| `v1.0-soutenance` | 1.0         |         100 |
//
// Le déduire, plutôt que de tenir deux nombres en parallèle, répond au seul vrai danger :
// republier un APK sous un `versionCode` déjà utilisé. Android refuse alors l'installation
// par-dessus le précédent (« application non installée »), et le message ne dit pas
// pourquoi. Ici, changer le nom de version change forcément le code.
//
// `correctif` (1 à 9) est réservé aux branches `hotfix/…` : il republie un APK entre deux
// jalons sans en inventer un (1.0.1 -> 101), et le code reste strictement croissant.
//
// À chaque jalon : modifier les trois entiers ci-dessous, et rien d'autre.
val versionMajeure = 0
val versionMineure = 1
val versionCorrectif = 0

val nomDeVersion: String =
    if (versionCorrectif == 0) "$versionMajeure.$versionMineure"
    else "$versionMajeure.$versionMineure.$versionCorrectif"

val codeDeVersion: Int = versionMajeure * 100 + versionMineure * 10 + versionCorrectif

// ---------------------------------------------------------------------------
// SIGNATURE DE L'APK DE RELEASE (B24, CDC §B9)
// ---------------------------------------------------------------------------
//
// Le keystore et ses mots de passe **ne sont jamais dans le dépôt** : `.gitignore` exclut
// déjà `*.jks`, `*.keystore` et `keystore.properties`. Les paramètres sont lus dans un
// fichier `keystore.properties` posé à la racine par chaque développeur, sur le modèle
// donné par la section « Signer une release » du README.
//
// Trois comportements, et c'est tout le sujet de ce bloc :
//
//  1. fichier absent — le cas d'un clone neuf, de l'intégration continue et de la machine
//     du relecteur : la configuration se poursuit sans signature, `assembleDebug` et les
//     tests marchent normalement ;
//  2. fichier absent **et** build de release demandé : la garde posée plus bas arrête la
//     tâche d'empaquetage avec un message explicite. Sans elle, Gradle produirait
//     tranquillement un `app-release-unsigned.apk` — un fichier qu'aucun téléphone
//     n'installe et dont le nom est la seule explication ;
//  3. fichier présent mais incomplet, ou keystore introuvable : échec immédiat, en
//     nommant la clé ou le chemin fautif. Une signature à moitié configurée est une
//     erreur locale, pas un mode de fonctionnement.
val fichierProprietesSignature = rootProject.file("keystore.properties")

val proprietesSignature: Properties? =
    if (fichierProprietesSignature.exists()) {
        Properties().apply { fichierProprietesSignature.inputStream().use { load(it) } }
    } else {
        null
    }

/** Lit une clé obligatoire de `keystore.properties`, ou arrête le build en la nommant. */
fun valeurSignature(cle: String): String {
    val valeur = proprietesSignature?.getProperty(cle)?.trim()
    if (valeur.isNullOrEmpty()) {
        throw GradleException(
            "keystore.properties : la clé « $cle » est absente ou vide. " +
                "Clés attendues : storeFile, storePassword, keyAlias, keyPassword. " +
                "Voir « Signer une release » dans README.md.",
        )
    }
    return valeur
}

android {
    namespace = "mg.univ.fahasalamana"
    compileSdk = 35

    defaultConfig {
        applicationId = "mg.univ.fahasalamana"
        minSdk = 26          // Android 8.0 : java.time sans désucrage, canaux de notification obligatoires
        targetSdk = 35
        versionCode = codeDeVersion   // (B24) déduit des trois entiers en tête de fichier
        versionName = nomDeVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // (B24) Créée seulement si keystore.properties existe, sinon un clone neuf ne pourrait
    // plus configurer le projet du tout — pas même pour lancer les tests.
    signingConfigs {
        if (proprietesSignature != null) {
            create("release") {
                val keystore = rootProject.file(valeurSignature("storeFile"))
                if (!keystore.exists()) {
                    throw GradleException(
                        "keystore.properties pointe sur un keystore introuvable : " +
                            "${keystore.absolutePath}. Le chemin `storeFile` se lit depuis " +
                            "la racine du dépôt. Voir « Signer une release » dans README.md.",
                    )
                }
                storeFile = keystore
                storePassword = valeurSignature("storePassword")
                keyAlias = valeurSignature("keyAlias")
                keyPassword = valeurSignature("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Le build debug sert aux démonstrations : le délai des rappels y est plafonné à
            // une minute par src/debug/java/.../platform/DelaiDemonstration.kt, dont le jumeau
            // src/release/ ne plafonne rien. Le raccourci n'existe donc pas dans l'APK publié.
            isMinifyEnabled = false
        }
        release {
            // (B24) Signé par le keystore de projet quand il est configuré. Quand il ne
            // l'est pas, on laisse volontairement `signingConfig` nul : c'est la garde
            // `packageRelease` en bas de fichier qui arrête le build, avec un message que
            // « app-release-unsigned.apk » ne donnerait pas.
            if (proprietesSignature != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            // R8 retire aussi les ressources qu'aucun code ne référence. Sans danger ici :
            // l'application ne cherche jamais une ressource par son nom
            // (`getIdentifier`), tout passe par les constantes `R.*` que R8 sait suivre.
            // Les deux JSON d'`assets/` ne sont pas des ressources et ne sont pas touchés.
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // (B04) Les deux JSON de référence d'`assets/` sont ajoutés au classpath des tests JVM.
    // `ContratReferenceTest` vérifie ainsi le contrat du §B5.1 sur les fichiers réellement
    // embarqués dans l'APK, plutôt que sur une copie dans `src/test/resources` qui finirait
    // par diverger. Cela ne change rien à l'APK : seul le source set `test` est touché.
    sourceSets {
        getByName("test") {
            resources.srcDir("src/main/assets")
        }
    }
}

// La cible Java du compilateur Kotlin se règle sur l'extension `kotlin`, qui est au niveau
// du projet et non dans le bloc `android`.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Schéma Room exporté et versionné (B02) : c'est lui qui permettra d'écrire une migration
// le jour où le schéma change, au lieu d'effacer la base des utilisateurs.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    // OkHttp est utilisé directement (`OkHttpClient`, `MediaType` dans ReferenceApi.kt,
    // `ResponseBody` dans SynchroniseurReferenceTest), donc il est déclaré directement.
    // Ne pas le retirer sous prétexte qu'il « arrive déjà » par l'intercepteur ou par
    // Retrofit : ces deux-là sont libres de changer leurs propres dépendances.
    // `testImplementation` hérite d'`implementation`, le test JVM est couvert par cette ligne.
    implementation(libs.okhttp)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.workmanager)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// ---------------------------------------------------------------------------
// GARDE DE SIGNATURE (B24)
// ---------------------------------------------------------------------------
//
// Un `assembleRelease` sans keystore ne doit pas « réussir ». Il produirait un
// `app-release-unsigned.apk` que le téléphone refuse d'installer, une demi-heure après,
// avec pour seul indice « Échec de l'analyse du paquet » — exactement le genre de panne
// qu'on découvre la veille de la soutenance.
//
// La vérification est posée sur les tâches d'empaquetage, et pas au moment de la
// configuration du projet, pour qu'elle ne se déclenche que lorsqu'un APK ou un AAB de
// release va réellement être écrit. `lint`, `testDebugUnitTest` et `assembleDebug`
// continuent donc de fonctionner sur un poste sans keystore, ce dont dépend
// l'intégration continue.
//
// `packageRelease` écrit l'APK, `packageReleaseBundle` l'AAB (le CDC §0.1 distribue l'APK,
// mais garde l'AAB possible « plus tard » : autant que les deux chemins soient gardés).
val messageSignatureManquante = """
    Build de release impossible : le fichier keystore.properties est absent de la racine du dépôt.

    Ce fichier et le keystore qu'il désigne restent volontairement hors du dépôt (.gitignore).
    Pour en créer un, suivre « Signer une release » dans README.md, puis relancer.

    Pour un APK installable sans keystore (démonstration, intégration continue) :
        ./gradlew assembleDebug
""".trimIndent()

val signatureConfiguree: Boolean = proprietesSignature != null

tasks.configureEach {
    if (name == "packageRelease" || name == "packageReleaseBundle") {
        doFirst {
            if (!signatureConfiguree) {
                throw GradleException(messageSignatureManquante)
            }
        }
    }
}
