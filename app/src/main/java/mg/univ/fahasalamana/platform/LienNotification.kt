package mg.univ.fahasalamana.platform

/*
 * Le lien profond `fahasalamana://enfant/{enfantId}` et le nom unique d'un rappel (B10).
 *
 * Ce fichier ne contient **aucune API Android**, et c'est voulu : le schéma du lien est
 * écrit à trois endroits qui ne se voient pas les uns les autres —
 *
 *  1. `AndroidManifest.xml`, dans l'`intent-filter` de `MainActivity` ;
 *  2. `AppNavHost`, dans le `navDeepLink<FicheEnfant>(basePath = …)` de la destination ;
 *  3. `NotificationHelper`, dans le `PendingIntent` de la notification.
 *
 * Les trois doivent dire exactement la même chose, sans quoi le lien se perd en silence :
 * une notification qui n'ouvre rien ne produit ni erreur ni journal. Les constantes
 * ci-dessous sont la seule source de vérité des points 2 et 3, et le test JVM
 * `LienNotificationTest` fige la chaîne attendue au point 1.
 */

/** Schéma privé de l'application (CDC §B7.1). Identique dans `AndroidManifest.xml`. */
const val SCHEME_DEEP_LINK: String = "fahasalamana"

/** Hôte du lien vers la fiche d'un enfant. Identique dans `AndroidManifest.xml`. */
const val HOTE_ENFANT: String = "enfant"

/**
 * Base du lien, telle que l'attend `navDeepLink<FicheEnfant>(basePath = …)` : la
 * navigation typée ajoute elle-même le segment `{enfantId}` pour l'argument obligatoire
 * de la route `FicheEnfant`.
 */
const val BASE_LIEN_ENFANT: String = "$SCHEME_DEEP_LINK://$HOTE_ENFANT"

/**
 * Lien vers la fiche d'un enfant : `fahasalamana://enfant/<id>`.
 *
 * L'identifiant n'est pas échappé parce que ce n'en est jamais un cas : `nouvelIdentifiant()`
 * produit un UUID, qui ne contient que des chiffres, des lettres et des tirets — tous
 * valables tels quels dans un segment d'URI. Le jour où un identifiant viendrait d'ailleurs
 * (import d'un fichier bricolé à la main, B17), c'est ici qu'il faudra l'encoder.
 */
fun lienFicheEnfant(enfantId: String): String = "$BASE_LIEN_ENFANT/$enfantId"

/**
 * Nom unique d'un rappel, règle R4 : `rappel-<enfantId>-<vaccinId>`.
 *
 * La même chaîne sert deux fois, et ce n'est pas un hasard :
 * - `uniqueWorkName` du `OneTimeWorkRequest` côté `PlanificateurRappels` (TODO(B11)) ;
 * - étiquette (`tag`) de la notification affichée par [NotificationHelper].
 *
 * Conséquence utile : annuler un rappel, c'est annuler le travail **et** retirer la
 * notification éventuellement déjà affichée, avec la même clé des deux côtés.
 */
fun etiquetteRappel(enfantId: String, vaccinId: String): String = "rappel-$enfantId-$vaccinId"
