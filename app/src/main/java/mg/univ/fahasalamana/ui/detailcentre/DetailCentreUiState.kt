package mg.univ.fahasalamana.ui.detailcentre

import androidx.compose.runtime.Immutable
import mg.univ.fahasalamana.domain.Centre

/**
 * État de la fiche d'un centre (tâche B14, US-B6 scénarios 2 et 3).
 *
 * Quatre états exclusifs, dont [Introuvable] qui n'est pas une erreur : l'identifiant vient
 * de la route, donc d'une liste affichée il y a un instant, d'un lien, ou d'une pile de
 * retour restaurée après la mort du processus. Entre-temps, une mise à jour de l'annuaire
 * (B19) a pu retirer ce centre. Le distinguer d'[Erreur] permet de dire ce qui s'est passé
 * — « ce centre ne figure plus dans l'annuaire » — au lieu d'annoncer une panne.
 */
sealed interface DetailCentreUiState {

    /** Première lecture, avant la première émission du `Flow` Room. */
    data object Chargement : DetailCentreUiState

    /** L'annuaire de ce téléphone ne contient pas (ou plus) ce centre. */
    data object Introuvable : DetailCentreUiState

    /** Le centre, prêt à afficher. */
    data class Pret(val centre: FicheCentre) : DetailCentreUiState

    /** La lecture de la base a échoué. Le message vient de `strings.xml`, pas d'ici. */
    data object Erreur : DetailCentreUiState
}

/**
 * Le centre tel que la fiche l'affiche : exactement les champs publiés par l'annuaire.
 *
 * Trois champs sont nullables alors que `domain/Centre` les donne en `String` : un contenu
 * publié peut arriver vide, et une ligne vide sous une icône ne veut rien dire. `null`
 * signifie « l'annuaire ne publie pas cette information », ce que l'écran sait écrire, et
 * c'est aussi ce qui décide de l'affichage du bouton — pas de numéro, pas de bouton
 * « Appeler ». Un bouton grisé sans explication serait pire.
 *
 * Pas de latitude ni de longitude : l'annuaire n'en publie pas (§B5.1), et US-B6 ne
 * demande pas d'itinéraire. L'adresse est une information à lire, pas un lien.
 */
@Immutable
data class FicheCentre(
    val id: String,
    val nom: String,
    val type: String,
    val horaires: String?,
    val adresse: String?,
    val telephone: String?,
) {

    /** « Appeler » n'a de sens que si l'annuaire publie un numéro. */
    val appelPossible: Boolean get() = telephone != null
}

/**
 * Passage du domaine à l'affichage : aucun calcul, juste la mise à `null` des champs vides.
 *
 * `ifBlank` et non `isEmpty` : un champ rempli d'espaces dans le JSON publié doit compter
 * comme absent, sinon la fiche affiche une ligne vide sous une icône.
 */
internal fun Centre.enFiche(): FicheCentre = FicheCentre(
    id = id,
    nom = nom,
    type = type,
    horaires = horaires.trim().ifBlank { null },
    adresse = adresse.trim().ifBlank { null },
    telephone = telephone.trim().ifBlank { null },
)
