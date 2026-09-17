package mg.univ.fahasalamana.platform

import java.time.Duration

/*
 * Délai de démonstration des rappels — **build debug uniquement** (DoD de B11).
 *
 * Ce fichier vit dans `src/debug/` et son jumeau, de même signature, dans `src/release/`.
 * C'est le procédé déjà retenu en B10 pour `BlocRappelsDebug.kt`, et pour les mêmes
 * raisons :
 *
 *  - la version publiée ne contient pas le raccourci, elle ne se contente pas de ne pas
 *    l'emprunter : il n'y a rien à désactiver, donc rien à réactiver par erreur ;
 *  - aucun `if (BuildConfig.DEBUG)` ne traîne dans le code de production, où un relecteur
 *    devrait à chaque fois vérifier de quel côté du test il se trouve ;
 *  - `buildFeatures { buildConfig = … }` n'est pas activé sur ce module (AGP 8 ne génère
 *    plus `BuildConfig` par défaut), donc un `buildConfigField` demanderait deux
 *    modifications de `app/build.gradle.kts` au lieu de zéro.
 *
 * Ce que ça change, concrètement, dans un build debug : `PlanificateurRappels` plafonne
 * **tous** les délais à une minute. Une minute après avoir créé un enfant, on reçoit donc
 * d'un coup les rappels que le parent recevrait normalement étalés sur des mois — ce qui
 * est exactement ce qui rend le scénario 1 de US-B5 vérifiable en soutenance sans avancer
 * l'horloge du téléphone.
 *
 * **Sa signature doit rester identique à celle de la version release.**
 */

/** Une minute : de quoi lancer la démonstration, verrouiller l'écran et attendre. */
val PLAFOND_DELAI_RAPPEL: Duration? = Duration.ofMinutes(1)
