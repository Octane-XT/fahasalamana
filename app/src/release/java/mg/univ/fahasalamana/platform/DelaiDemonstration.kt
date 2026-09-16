package mg.univ.fahasalamana.platform

import java.time.Duration

/*
 * Jumeau publié de `src/debug/java/.../DelaiDemonstration.kt` (B11).
 *
 * `null` : aucun plafond, `PlanificateurRappels` programme le délai réel de la règle R3
 * — trois jours avant la date prévue, à 09:00, soit couramment plusieurs mois d'attente,
 * ce que WorkManager sait tenir (§B8).
 *
 * Le raccourci de démonstration n'est pas « désactivé » ici : il n'existe pas. C'est tout
 * l'intérêt du jumeau plutôt que d'un drapeau lu à l'exécution.
 *
 * **Sa signature doit rester identique à celle de la version debug.**
 */
val PLAFOND_DELAI_RAPPEL: Duration? = null
