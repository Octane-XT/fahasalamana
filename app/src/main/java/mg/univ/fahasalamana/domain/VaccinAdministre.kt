package mg.univ.fahasalamana.domain

import java.time.LocalDate

/**
 * Une dose effectivement reçue par un enfant.
 *
 * Donnée personnelle de santé (§B8) : jamais envoyée sur le réseau.
 * Contrainte d'unicité (enfant, vaccin) tenue par la base (R5) ; le calculateur
 * reste tolérant si un doublon lui parvient malgré tout.
 *
 * @param vaccinId référence un [VaccinReference.id] ; la dose peut avoir été retirée
 *   d'une version ultérieure du calendrier, l'enregistrement reste valide.
 */
data class VaccinAdministre(
    val id: String,
    val enfantId: String,
    val vaccinId: String,
    val date: LocalDate,
    val lieu: String? = null,
    val lot: String? = null,
)
