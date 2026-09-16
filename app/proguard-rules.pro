# Règles R8 pour le build release (TODO(B24) : vérifier l'APK release sur téléphone).

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
