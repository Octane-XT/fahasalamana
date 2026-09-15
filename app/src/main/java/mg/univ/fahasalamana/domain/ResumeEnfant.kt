package mg.univ.fahasalamana.domain

import java.time.LocalDate

/**
 * Synthèse d'un échéancier (règle R6). Sert aux badges de la fiche et au tri de
 * la liste « Mes enfants » par nombre de retards (US-B8).
 *
 * @param nbEnRetard nombre de lignes [StatutVaccin.EnRetard].
 * @param nbAFaire nombre de lignes [StatutVaccin.AFaire] : la fenêtre est ouverte aujourd'hui.
 * @param prochaineEcheance plus proche date prévue parmi les lignes qui ne sont pas
 *   encore dues ([StatutVaccin.AVenir] et [StatutVaccin.EnAttente]), donc toujours
 *   postérieure au jour de calcul. Nulle si tout est fait, à faire ou en retard.
 */
data class ResumeEnfant(
    val nbEnRetard: Int,
    val nbAFaire: Int,
    val prochaineEcheance: LocalDate?,
)
