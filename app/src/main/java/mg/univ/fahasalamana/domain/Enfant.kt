package mg.univ.fahasalamana.domain

import java.time.LocalDate

/**
 * Un enfant suivi dans le carnet.
 *
 * Donnée personnelle de santé : cet objet ne part jamais sur le réseau (§B8).
 * Il n'est exporté que par une action explicite de l'utilisateur via le SAF.
 *
 * @param id identifiant stable (UUID) généré à la création, conservé à l'export/import.
 */
data class Enfant(
    val id: String,
    val prenom: String,
    val dateNaissance: LocalDate,
    val sexe: Sexe,
)

/** Le sexe est facultatif dans le formulaire : [NON_PRECISE] est une valeur valide, pas un défaut technique. */
enum class Sexe { GARCON, FILLE, NON_PRECISE }
