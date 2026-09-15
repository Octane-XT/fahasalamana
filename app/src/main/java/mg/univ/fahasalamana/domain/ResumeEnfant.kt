package mg.univ.fahasalamana.domain

import java.time.LocalDate

/**
 * Synthèse d'un échéancier (règle R6). Sert aux badges de la fiche et au tri de
 * la liste « Mes enfants » par nombre de retards (US-B8).
 *
 * @param nbEnRetard nombre de lignes [StatutVaccin.EnRetard].
 * @param nbAFaire nombre de lignes [StatutVaccin.AFaire] : la fenêtre est ouverte aujourd'hui.
 * @param prochaineEcheance date de la **prochaine dose à faire** : la plus proche date prévue
 *   parmi toutes les lignes qui ne sont pas faites — [StatutVaccin.AVenir],
 *   [StatutVaccin.EnAttente], [StatutVaccin.AFaire] et [StatutVaccin.EnRetard]. Elle peut
 *   donc être antérieure au jour de calcul : c'est voulu. Restreinte aux seules dates encore
 *   à venir, elle affichait « Prochain : aucune échéance à venir » juste sous « 3 en retard »,
 *   alors qu'il restait précisément trois doses à faire. Nulle seulement quand il ne reste
 *   rien à faire — carnet complet — ou quand aucune date n'est calculable.
 */
data class ResumeEnfant(
    val nbEnRetard: Int,
    val nbAFaire: Int,
    val prochaineEcheance: LocalDate?,
)
