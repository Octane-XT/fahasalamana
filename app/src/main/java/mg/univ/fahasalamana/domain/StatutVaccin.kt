package mg.univ.fahasalamana.domain

import java.time.LocalDate

/**
 * État d'une dose pour un enfant à une date donnée (règle R2).
 *
 * Type scellé à cinq cas : les `when` sur ce type s'écrivent sans `else`, pour
 * qu'un sixième état oblige le compilateur à signaler tous les points d'affichage.
 *
 * Les libellés construits à partir de ces états doivent rester factuels et non
 * culpabilisants (R7) : « Prévu le… », « À faire dès que possible », jamais
 * « Vous avez oublié ». Le calculateur ne produit aucun texte : les formulations
 * vivent dans `strings.xml`.
 */
sealed interface StatutVaccin {

    /** Dose saisie comme reçue le [date]. */
    data class Fait(val date: LocalDate) : StatutVaccin

    /** Échéance encore devant nous : [dansJours] jours séparent aujourd'hui de [prevuLe]. */
    data class AVenir(val prevuLe: LocalDate, val dansJours: Long) : StatutVaccin

    /** Dans la fenêtre : la date prévue est atteinte et la tolérance court jusqu'au [jusquAu] inclus. */
    data class AFaire(val prevuLe: LocalDate, val jusquAu: LocalDate) : StatutVaccin

    /**
     * Fenêtre dépassée. [retardJours] compte les jours écoulés depuis [prevuLe]
     * (et non depuis la fin de la fenêtre) : c'est la date prévue qui est affichée à côté.
     */
    data class EnRetard(val prevuLe: LocalDate, val retardJours: Long) : StatutVaccin

    /**
     * Dose suspendue à une dose précédente non encore faite, et dont la date
     * théorique n'est pas encore atteinte (R1). Dès que cette date théorique est
     * atteinte, le statut bascule en [AFaire] puis [EnRetard] pour que le retard
     * cumulé de la série reste visible.
     *
     * @param dependDe identifiant de la dose attendue.
     */
    data class EnAttente(val dependDe: String) : StatutVaccin
}
