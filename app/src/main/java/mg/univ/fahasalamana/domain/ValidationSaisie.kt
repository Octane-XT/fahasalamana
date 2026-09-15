package mg.univ.fahasalamana.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Validation d'une saisie d'administration — règle R5 (§B4), tâche B09.
 *
 * Fonction **pure** au même titre que `CalculateurEcheancier` : entrées (dates, fenêtre du
 * calendrier) → sortie (un résultat typé). Aucun accès Android, aucune horloge implicite,
 * aucun texte : les formulations vivent dans `strings_saisie_vaccin.xml`. C'est ce qui rend
 * la règle testable en JVM (`ValidationSaisieTest`) et vérifiable en soutenance.
 *
 * **Deux refus, et deux seulement** (R5) : une date antérieure à la naissance et une date
 * dans le futur. Tout le reste passe.
 *
 * **Une saisie hors fenêtre est acceptée** (R5), avec un avertissement neutre affiché mais
 * jamais bloquant. Ce n'est pas une tolérance technique, c'est le cœur du ton du projet
 * (R7) : une mère qui fait vacciner son enfant avec six mois de retard doit pouvoir
 * l'enregistrer sans que l'application s'y oppose ni lui fasse la leçon. L'avertissement
 * dit ce qui est constaté — « reçue 180 jours après la date prévue » — et rappelle que la
 * saisie est conservée telle quelle ; il ne dit jamais ce qui aurait dû être fait.
 *
 * L'unicité (enfant, vaccin) du même R5 n'est pas validée ici : elle est portée par un
 * index unique en base et rattrapée par l'`upsert` du repository (voir
 * `EnfantRepositoryImpl.enregistrerAdministration`). Une deuxième saisie pour le même
 * couple corrige la première au lieu d'être refusée — c'est exactement US-B4.
 */

/**
 * Ce que la règle R5 répond à une saisie.
 *
 * Type scellé à deux cas : un `when` sans `else` côté écran, et l'impossibilité d'oublier
 * qu'un refus existe. [Acceptee] porte un avertissement **facultatif** parce qu'accepter
 * et avertir ne sont pas deux états différents : la saisie est enregistrée dans les deux cas.
 */
sealed interface ResultatSaisie {

    /** La saisie peut être enregistrée. [avertissement] est nul quand la date tombe dans la fenêtre prévue. */
    data class Acceptee(val avertissement: AvertissementSaisie? = null) : ResultatSaisie

    /** La saisie ne peut pas être enregistrée : la date décrit un fait impossible. */
    data class Refusee(val motif: MotifRefus) : ResultatSaisie
}

/**
 * Les deux seules dates impossibles (R5).
 *
 * Ce sont des impossibilités matérielles, pas des jugements : on ne peut pas avoir reçu une
 * dose avant d'être né, ni l'avoir reçue demain.
 */
sealed interface MotifRefus {

    /** Date antérieure à la naissance de l'enfant. */
    data class AvantLaNaissance(val dateNaissance: LocalDate) : MotifRefus

    /** Date postérieure au jour de la saisie. */
    data class DansLeFutur(val aujourdHui: LocalDate) : MotifRefus
}

/**
 * Écart constaté entre la date saisie et la fenêtre du calendrier (R5).
 *
 * **N'empêche jamais l'enregistrement.** Sa seule raison d'être est d'informer : une date
 * tapée à côté (le 05/02 au lieu du 05/12) se repère ainsi avant d'être enregistrée, et un
 * vrai retard est simplement constaté.
 */
sealed interface AvertissementSaisie {

    /**
     * Dose reçue avant la date prévue : la fenêtre n'était pas encore ouverte.
     *
     * @param joursAvance nombre de jours entre la date saisie et [prevuLe], toujours positif.
     */
    data class AvantLaDatePrevue(
        val prevuLe: LocalDate,
        val joursAvance: Long,
    ) : AvertissementSaisie

    /**
     * Dose reçue après la fin de la fenêtre de tolérance.
     *
     * @param finFenetre dernier jour de la fenêtre, `prevuLe + toleranceJours` inclus.
     * @param joursApres nombre de jours écoulés depuis [prevuLe] — et non depuis
     *   [finFenetre] : c'est la date prévue qui est affichée à côté, comme pour
     *   [StatutVaccin.EnRetard].
     */
    data class ApresLaFenetre(
        val prevuLe: LocalDate,
        val finFenetre: LocalDate,
        val joursApres: Long,
    ) : AvertissementSaisie
}

/**
 * Applique la règle R5 à une saisie d'administration.
 *
 * L'ordre des vérifications est fixé : **la naissance d'abord**, puis le futur. Une date
 * qui serait à la fois antérieure à la naissance et postérieure à aujourd'hui ne peut se
 * produire que si la naissance elle-même est dans le futur — ce que le formulaire d'ajout
 * d'un enfant interdit déjà (US-B1) — et, dans ce cas, le motif le plus proche de la cause
 * est bien la naissance.
 *
 * Les deux bornes sont **inclusives** : le jour de la naissance (BCG et Polio 0 sont donnés
 * à la maternité) et le jour même sont des saisies parfaitement valides.
 *
 * @param dateSaisie date d'administration entrée par l'utilisateur.
 * @param dateNaissance date de naissance de l'enfant concerné.
 * @param aujourdHui jour de référence, venu de l'horloge injectée (`horlogeJour`) et jamais
 *   d'un `LocalDate.now()` lu au passage.
 * @param prevuLe date prévue de cette dose, calculée par la règle R1. Nulle quand la chaîne
 *   de dépendances est cassée : aucun écart n'est alors calculable, donc aucun avertissement.
 * @param toleranceJours fenêtre du calendrier après [prevuLe] ([VaccinReference.toleranceJours]).
 *   Une valeur négative est traitée comme zéro plutôt que de rétrécir la fenêtre en deçà de
 *   la date prévue : un `calendrier.json` malformé ne doit pas inventer un avertissement.
 */
fun validerSaisie(
    dateSaisie: LocalDate,
    dateNaissance: LocalDate,
    aujourdHui: LocalDate,
    prevuLe: LocalDate?,
    toleranceJours: Int,
): ResultatSaisie = when {
    dateSaisie.isBefore(dateNaissance) ->
        ResultatSaisie.Refusee(MotifRefus.AvantLaNaissance(dateNaissance))

    dateSaisie.isAfter(aujourdHui) ->
        ResultatSaisie.Refusee(MotifRefus.DansLeFutur(aujourdHui))

    else ->
        ResultatSaisie.Acceptee(ecartAlaFenetre(dateSaisie, prevuLe, toleranceJours))
}

/**
 * Écart entre la date saisie et la fenêtre `[prevuLe, prevuLe + toleranceJours]`.
 *
 * @return `null` quand la date tombe dans la fenêtre, bornes comprises, ou quand aucune date
 *   prévue n'est calculable.
 */
private fun ecartAlaFenetre(
    dateSaisie: LocalDate,
    prevuLe: LocalDate?,
    toleranceJours: Int,
): AvertissementSaisie? {
    if (prevuLe == null) return null
    val finFenetre = prevuLe.plusDays(toleranceJours.coerceAtLeast(0).toLong())

    return when {
        dateSaisie.isBefore(prevuLe) -> AvertissementSaisie.AvantLaDatePrevue(
            prevuLe = prevuLe,
            joursAvance = ChronoUnit.DAYS.between(dateSaisie, prevuLe),
        )

        dateSaisie.isAfter(finFenetre) -> AvertissementSaisie.ApresLaFenetre(
            prevuLe = prevuLe,
            finFenetre = finFenetre,
            joursApres = ChronoUnit.DAYS.between(prevuLe, dateSaisie),
        )

        else -> null
    }
}
