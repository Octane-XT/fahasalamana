# Règles R8 pour le build release.
#
# Ces règles ne sont exercées par aucun test : les tests JVM et les tests instrumentés
# tournent sur du code non minifié, et le build debug a `isMinifyEnabled = false`. Une
# règle manquante ne se voit donc qu'à l'exécution de l'APK release, sur un téléphone.
# C'est la raison d'être de la Definition of Done de B24 : installer l'APK release et
# refaire le parcours complet (ajout d'un enfant, saisie, rappel, export, import,
# vérification des mises à jour).

# kotlinx.serialization : conserver les sérialiseurs générés.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class mg.univ.fahasalamana.**$$serializer { *; }
-keepclassmembers class mg.univ.fahasalamana.** {
    *** Companion;
}
-keepclasseswithmembers class mg.univ.fahasalamana.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# WorkManager retrouve un worker par le nom de sa classe, qu'il persiste en base : un rappel
# enfile par une version et execute apres une mise a jour au mapping different ne serait plus
# retrouve. Les noms des workers doivent donc survivre a R8 (B11).
-keepnames class * extends androidx.work.ListenableWorker

# Retrofit lit par reflexion le type de retour des fonctions de ReferenceApi (B19). La
# signature generique d'une fonction `suspend` (Continuation<CalendrierDto>) doit donc
# survivre a R8, sans quoi la mise a jour echouerait dans l'APK release et nulle part
# ailleurs. Retrofit 2.11 embarque deja ces regles dans META-INF/proguard/ ; elles sont
# repetees ici parce qu'un APK release est un livrable de soutenance et que cette
# dependance-la ne doit pas rester implicite.
-keepattributes Signature, Exceptions
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ===========================================================================
# B24 — compléments relus pour ce projet
# ===========================================================================
#
# Les règles ci-dessus (kotlinx.serialization, Room, WorkManager, Retrofit) ont été
# relues une par une contre le code réellement écrit. Il leur manquait les six points
# ci-dessous, ajoutés à la suite plutôt qu'insérés au milieu pour que la relecture voie
# d'un coup d'œil ce que B24 change.

# --- 1. Objets sérialisables : les quatre routes sans argument -------------
#
# `MesEnfants`, `Centres`, `Reglages` et `Verrouillage` (ui/navigation/Routes.kt) sont des
# `@Serializable object`, pas des `data class`. C'est un cas que les règles ci-dessus ne
# couvrent pas : elles conservent le champ `Companion` et la fonction `serializer(...)`,
# mais un objet Kotlin n'a pas de `Companion` — il a un champ statique `INSTANCE`, et
# c'est celui-là que la sérialisation va chercher. En mode R8 « full » (le défaut depuis
# AGP 8) un `INSTANCE` jugé inutilisé disparaît, et la navigation échoue à construire le
# sérialiseur de la route. Panne connue en amont :
# github.com/Kotlin/kotlinx.serialization/issues/2861.
#
# Règle reprise telle quelle de `rules/r8.pro` du dépôt kotlinx.serialization. La répéter
# ici est sans effet de bord si une version future de la bibliothèque finit par l'embarquer :
# R8 additionne les jeux de règles, il n'en choisit pas un.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# --- 2. L'annotation @Serializable elle-même, en mode R8 « full » ----------
#
# Le mode « full » retire les annotations des classes. Or `serializer<T>()` relit
# l'annotation `@Serializable` à l'exécution pour décider entre `SealedClassSerializer` et
# `PolymorphicSerializer`. Sans cette règle, une hiérarchie scellée sérialisée bascule en
# polymorphique et échoue à la lecture. `StatutVaccin` n'est pas sérialisé aujourd'hui,
# mais le carnet exporté est un contrat entre deux téléphones (B16/B17) : la règle protège
# la première hiérarchie scellée qu'on y ajoutera, sans qu'il faille y repenser.
#
# Reprise de `rules/r8.pro` (kotlinx.serialization), y compris sa mise en forme.
-if @kotlinx.serialization.Serializable class **
-keep, allowshrinking, allowoptimization, allowobfuscation, allowaccessmodification class <1>

# `java.lang.ClassValue` n'existe pas sur Android : la sérialisation ne l'utilise donc pas,
# mais R8 ne le sait pas et avertit sur une référence qu'il ne résout pas.
-dontwarn kotlinx.serialization.internal.ClassValueReferences

# --- 3. Routes typées : le nom de la classe EST le chemin de la route ------
#
# Avec la navigation typée, la route d'une destination est dérivée du nom sérialisé de sa
# classe, c'est-à-dire de son nom qualifié complet : la destination `FicheEnfant` est
# enregistrée sous `mg.univ.fahasalamana.ui.navigation.FicheEnfant/{enfantId}`. R8 renomme
# de façon cohérente à l'intérieur d'un même APK, donc la navigation ordinaire survivrait
# à l'obfuscation ; ce que pinner les noms protège, c'est ce qui traverse les frontières
# d'un APK :
#
#  - une pile de retour sauvegardée puis restaurée après la mort du processus, ou après
#    une réinstallation pendant la démonstration, contient ces chaînes ;
#  - une trace de plantage lisible : `…ui.navigation.FicheEnfant` dit où on était,
#    `c.a.b` ne dit rien.
#
# `-keepnames` = garder les noms, mais laisser R8 supprimer ce qui n'est pas utilisé :
# aucune route morte n'est conservée pour autant.
-keepnames class mg.univ.fahasalamana.ui.navigation.**

# --- 4. Room : le constructeur de l'implémentation générée ----------------
#
# Complète la règle Room plus haut, qui conserve la classe mais pas ses membres. Room
# instancie `AppDatabase_Impl` par réflexion, avec son constructeur sans argument : si R8
# le supprime — il n'est appelé nulle part dans le code écrit à la main — l'ouverture de
# la base échoue au premier lancement de l'APK release, avant le moindre écran.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# --- 5. Noms des constantes de `Sexe`, écrits dans le carnet exporté -------
#
# `EnfantExport.sexe` transporte le **nom** de la constante (`GARCON`, `FILLE`,
# `NON_PRECISE`) et non l'énumération, et l'import relit ce nom (`Sexe.entries.firstOrNull
# { it.name == texte }`). Ces trois chaînes sont donc du contrat de fichier, au même titre
# que les noms de clés JSON, et un fichier écrit par l'APK release doit rester lisible par
# une autre installation. On épingle les constantes plutôt que de dépendre du fait que R8
# renonce de lui-même à optimiser une énumération dont `name` est lu.
-keepclassmembers enum mg.univ.fahasalamana.domain.Sexe {
    <fields>;
}

# --- 6. Traces de plantage exploitables ------------------------------------
#
# Sans ces deux lignes, une trace venue de l'APK release n'a ni nom de fichier ni numéro de
# ligne : on sait qu'il y a eu une exception, pas où. `-renamesourcefileattribute` remplace
# le nom de fichier réel par « SourceFile », de sorte que les lignes restent mais que
# l'arborescence du projet ne s'imprime pas dans l'APK.
#
# La correspondance entre noms obfusqués et noms d'origine est écrite par R8 dans
# `app/build/outputs/mapping/release/mapping.txt`. Ce fichier est le seul moyen de relire
# une trace : le conserver avec l'APK livré (voir README.md).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
