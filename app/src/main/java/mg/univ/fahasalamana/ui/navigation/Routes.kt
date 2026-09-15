package mg.univ.fahasalamana.ui.navigation

import kotlinx.serialization.Serializable

/*
 * Les huit destinations de l'application (CDC §B7.1), en routes typées : le compilateur
 * vérifie les arguments, il n'y a aucune chaîne "ecran/{id}" à tenir à jour.
 *
 * Un ViewModel ne lit jamais ses arguments dans l'écran : il les récupère avec
 * `savedStateHandle.toRoute<MaRoute>()` (CLAUDE.md, règle 4).
 *
 * Fichier partagé entre les deux développeurs : ajouter une route à la fin, ne rien
 * réordonner.
 */

/** Onglet Enfants — liste des enfants du carnet. Destination de départ. */
@Serializable
object MesEnfants

/** Création (`enfantId == null`) ou modification d'un enfant. */
@Serializable
data class EditionEnfant(val enfantId: String? = null)

/** Carnet d'un enfant : échéancier groupé par âge. Cible du deep link des rappels. */
@Serializable
data class FicheEnfant(val enfantId: String)

/** Saisie, correction ou suppression d'une administration de vaccin. */
@Serializable
data class SaisieVaccin(val enfantId: String, val vaccinId: String)

/** Onglet Centres — annuaire des centres de santé de base, par région et district. */
@Serializable
object Centres

/** Fiche d'un centre : horaires, contact, bouton Appeler. */
@Serializable
data class DetailCentre(val centreId: String)

/** Onglet Réglages — sécurité, rappels, carnet, données de référence. */
@Serializable
object Reglages

/** Saisie du code de verrouillage, hors onglets (CDC §B8). */
@Serializable
object Verrouillage
