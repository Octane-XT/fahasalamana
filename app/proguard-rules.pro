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
