package mg.univ.fahasalamana.domain

import java.time.LocalDate

/**
 * Une ligne de l'échéancier d'un enfant : un vaccin du calendrier, sa date prévue
 * et son état à la date de calcul.
 *
 * @param prevuLe date calculée par la règle R1. Nulle dans le seul cas où la
 *   chaîne de dépendances est cassée — la dose dont celle-ci dépend a disparu du
 *   calendrier et n'a jamais été administrée : aucune date n'est alors calculable
 *   et le statut est [StatutVaccin.EnAttente].
 */
data class LigneEcheancier(
    val vaccin: VaccinReference,
    val prevuLe: LocalDate?,
    val statut: StatutVaccin,
)
