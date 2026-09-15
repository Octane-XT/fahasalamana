package mg.univ.fahasalamana.domain

/**
 * Une ligne du calendrier de référence (§B4), alimentée par `calendrier.json` (§B5.1).
 *
 * Contenu de référence, jamais une donnée personnelle : il est remplacé en bloc
 * lors d'une mise à jour sans jamais toucher aux administrations saisies.
 *
 * @param id identifiant stable dans le temps (« penta1 ») : les [VaccinAdministre] y font référence.
 * @param ordre rang d'affichage dans le calendrier, contigu à partir de 1.
 * @param ageJours délai depuis la naissance si [dependDe] est nul, sinon délai après la dose dont il dépend.
 * @param dependDe identifiant de la dose précédente de la même série, ou nul.
 * @param toleranceJours durée de la fenêtre qui suit la date prévue avant de basculer « En retard ».
 */
data class VaccinReference(
    val id: String,
    val nom: String,
    val dose: String,
    val ordre: Int,
    val ageJours: Int,
    val dependDe: String?,
    val toleranceJours: Int,
    val description: String,
)
