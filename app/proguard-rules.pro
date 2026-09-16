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
