package mg.univ.fahasalamana.data.remote

import okhttp3.logging.HttpLoggingInterceptor

/*
 * Niveau de journalisation HTTP — **build debug uniquement** (B19).
 *
 * Ce fichier vit dans `src/debug/` et son jumeau, de même signature, dans `src/release/`.
 * C'est le procédé déjà retenu en B10 (`BlocRappelsDebug.kt`) et en B11
 * (`DelaiDemonstration.kt`), et pour les mêmes raisons : la version publiée ne contient pas
 * le comportement de démonstration, elle ne se contente pas de ne pas l'emprunter ; et
 * `buildFeatures { buildConfig = … }` n'étant pas activé sur ce module, un
 * `if (BuildConfig.DEBUG)` demanderait de modifier `app/build.gradle.kts`.
 *
 * `BASIC` et non `BODY` : la ligne de requête, le code de retour et la taille suffisent à
 * diagnostiquer une mise à jour qui échoue. `BODY` recopierait les 115 Ko de l'annuaire
 * dans logcat à chaque vérification, ce qui noie le journal sans rien apprendre — le contenu
 * du fichier est de toute façon lisible à son URL.
 *
 * **Sa signature doit rester identique à celle de la version release.**
 */
val NIVEAU_JOURNAL_RESEAU: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BASIC
