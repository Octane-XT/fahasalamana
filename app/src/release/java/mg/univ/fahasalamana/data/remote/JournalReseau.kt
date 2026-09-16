package mg.univ.fahasalamana.data.remote

import okhttp3.logging.HttpLoggingInterceptor

/*
 * Jumeau publié de `src/debug/java/.../JournalReseau.kt` (B19).
 *
 * `NONE` : l'APK publié n'écrit rien dans logcat au sujet du réseau. Les fichiers de
 * référence sont publics et ne contiennent aucune donnée de santé, mais une application de
 * carnet de vaccination n'a aucune raison de laisser des traces d'activité sur le téléphone
 * de l'utilisateur — et un journal muet est plus simple à défendre qu'un journal dont il
 * faut expliquer le contenu (§B8).
 *
 * La journalisation n'est pas « désactivée » ici : l'intercepteur est bien posé, mais son
 * niveau ne laisse rien passer. Le jumeau reste préférable à un drapeau lu à l'exécution,
 * pour la même raison qu'en B11.
 *
 * **Sa signature doit rester identique à celle de la version debug.**
 */
val NIVEAU_JOURNAL_RESEAU: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.NONE
