package mg.univ.fahasalamana.domain

import java.time.LocalDate

/*
 * Validation du formulaire d'édition d'un enfant — écran `EditionEnfant` (B07, US-B1).
 *
 * Fonctions pures, sans Android : elles se testent en JVM (`ValidationEnfantTest`), et
 * c'est la raison pour laquelle elles vivent ici plutôt que dans le ViewModel ou dans un
 * composable (règle 1 de CLAUDE.md). L'écran ne décide jamais si une saisie est correcte :
 * il affiche ce que ce fichier lui répond, et n'active son bouton de validation que si
 * [ValidationEnfant.valide] est vrai.
 *
 * **Aucun texte ici.** Les erreurs sont des types, pas des phrases : les formulations
 * vivent dans `res/values/strings_edition_enfant.xml`, où le ton factuel et non
 * culpabilisant de la règle R7 se relit d'un coup d'œil. Le scénario Gherkin de US-B1 fixe
 * d'ailleurs l'une d'elles mot pour mot : « La date de naissance ne peut pas être dans le
 * futur ».
 *
 * Portée : c'est la validation **du formulaire**, pas celle de la base. Un carnet importé
 * (B17) peut contenir des valeurs qui ne passeraient pas ici ; elles ne sont pas rejetées
 * pour autant, les écrans de lecture savent les afficher (voir `ageDepuis` dans
 * `MesEnfantsUiState`). Ce fichier empêche de *saisir* une aberration, il ne prétend pas
 * en interdire l'existence.
 */

/**
 * Longueur maximale d'un prénom, une fois les espaces nettoyés.
 *
 * Ce n'est pas une contrainte de la base — la colonne est un `TEXT` sans limite — mais une
 * borne d'affichage : au-delà, le prénom ne tient plus sur la carte de la liste ni dans le
 * titre d'une notification, et il ne s'agit alors plus d'un prénom mais d'un collage.
 */
const val LONGUEUR_MAX_PRENOM: Int = 60

/**
 * Âge au-delà duquel une date de naissance est tenue pour une faute de frappe.
 *
 * **Ce n'est pas une règle médicale** et le CDC n'en fixe aucune : c'est une borne de
 * vraisemblance, qui rattrape l'année mal tapée (1026 pour 2026, 1926 pour 2026) sans
 * jamais gêner une saisie réelle. Dix-huit ans couvre très largement le calendrier de
 * référence, qui s'arrête à quelques années après la naissance.
 */
const val AGE_MAXIMUM_ANNEES: Long = 18

/**
 * Les trois champs du formulaire, tels que l'utilisateur les a laissés.
 *
 * Le prénom est gardé **brut** : c'est bien ce qui est affiché dans le champ de saisie
 * pendant la frappe. Le nettoyage n'a lieu qu'à la validation et à la construction de
 * l'[Enfant], sans quoi l'espace qu'on vient de taper entre deux prénoms disparaîtrait
 * sous les doigts.
 */
data class SaisieEnfant(
    val prenom: String = "",
    val dateNaissance: LocalDate? = null,
    val sexe: Sexe = Sexe.NON_PRECISE,
)

/** Ce qui empêche un prénom d'être accepté. */
sealed interface ErreurPrenom {

    /** Vide, ou composé uniquement d'espaces. */
    data object Vide : ErreurPrenom

    /** Plus long que [maximum] caractères, espaces nettoyés. */
    data class TropLong(val maximum: Int) : ErreurPrenom
}

/** Ce qui empêche une date de naissance d'être acceptée. */
sealed interface ErreurDateNaissance {

    /** Aucune date choisie : le formulaire n'est pas complet. */
    data object Absente : ErreurDateNaissance

    /** Postérieure à aujourd'hui (scénario « date de naissance dans le futur » de US-B1). */
    data object DansLeFutur : ErreurDateNaissance

    /**
     * Antérieure à [limite], la date de naissance la plus ancienne acceptée
     * (voir [AGE_MAXIMUM_ANNEES]). La borne est portée par l'erreur pour que le message
     * affiché puisse la citer au lieu de rester vague.
     */
    data class TropAncienne(val limite: LocalDate) : ErreurDateNaissance
}

/**
 * Résultat de la validation : au plus une erreur par champ.
 *
 * Les deux champs sont validés ensemble et non l'un après l'autre : un formulaire dont le
 * prénom **et** la date sont fautifs signale les deux d'un coup, plutôt que de faire
 * corriger l'un pour découvrir l'autre.
 */
data class ValidationEnfant(
    val prenom: ErreurPrenom? = null,
    val dateNaissance: ErreurDateNaissance? = null,
) {
    /** Vrai quand la saisie peut être enregistrée : c'est ce qui active le bouton de validation. */
    val valide: Boolean get() = prenom == null && dateNaissance == null
}

/**
 * Prénom débarrassé de ses espaces : bordures retirées, suites d'espaces internes
 * ramenées à un seul.
 *
 * "  Faly  " devient "Faly", "Jean  Luc" devient "Jean Luc". C'est cette forme qui est
 * enregistrée, jamais la frappe brute : deux enfants nommés "Soa" et "Soa " ne doivent pas
 * se distinguer dans la liste par un espace invisible.
 */
fun prenomNettoye(brut: String): String = brut.trim().replace(ESPACES, " ")

/** Date de naissance la plus ancienne acceptée à la date [aujourdHui] (voir [AGE_MAXIMUM_ANNEES]). */
fun dateNaissanceMinimum(aujourdHui: LocalDate): LocalDate = aujourdHui.minusYears(AGE_MAXIMUM_ANNEES)

/**
 * Valide la saisie à la date [aujourdHui].
 *
 * [aujourdHui] est un paramètre et non un `LocalDate.now()` : c'est ce qui rend la fonction
 * pure, testable sans toucher à l'horloge du système, et ce qui permet à l'écran d'utiliser
 * le même jour de référence que le reste de l'application (`platform/HorlogeJour.kt`).
 */
fun SaisieEnfant.valider(aujourdHui: LocalDate): ValidationEnfant = ValidationEnfant(
    prenom = erreurDePrenom(prenom),
    dateNaissance = erreurDeDateNaissance(dateNaissance, aujourdHui),
)

/**
 * L'[Enfant] décrit par cette saisie, ou `null` si elle n'est pas valide à la date
 * [aujourdHui].
 *
 * Point de passage unique entre le formulaire et le modèle : le prénom y est nettoyé une
 * fois pour toutes, et un enfant ne peut pas être construit à partir d'une saisie que
 * [valider] rejette. Le ViewModel n'a donc aucune règle à réappliquer avant d'enregistrer.
 *
 * @param id identifiant de l'enfant : celui de la route en modification, un UUID neuf en
 *   création. Il n'est jamais renuméroté, c'est lui qui permet la fusion à l'import (B17).
 */
fun SaisieEnfant.enfantValide(id: String, aujourdHui: LocalDate): Enfant? {
    val date = dateNaissance ?: return null
    if (!valider(aujourdHui).valide) return null
    return Enfant(
        id = id,
        prenom = prenomNettoye(prenom),
        dateNaissance = date,
        sexe = sexe,
    )
}

/** Suites d'espaces, tabulations et retours à la ligne, pour [prenomNettoye]. */
private val ESPACES = Regex("\\s+")

private fun erreurDePrenom(brut: String): ErreurPrenom? {
    val prenom = prenomNettoye(brut)
    return when {
        prenom.isEmpty() -> ErreurPrenom.Vide
        prenom.length > LONGUEUR_MAX_PRENOM -> ErreurPrenom.TropLong(LONGUEUR_MAX_PRENOM)
        else -> null
    }
}

/**
 * Ordre des contrôles : absence d'abord, puis futur, puis ancienneté. Le message le plus
 * utile est celui qui décrit le geste à faire ensuite ; annoncer « trop ancienne » à qui
 * n'a encore rien choisi n'aiderait personne.
 *
 * Né aujourd'hui est valide : `isAfter` et non `isAfter(aujourdHui.minusDays(1))`. De même,
 * la borne d'ancienneté est inclusive : le jour des dix-huit ans passe encore.
 */
private fun erreurDeDateNaissance(date: LocalDate?, aujourdHui: LocalDate): ErreurDateNaissance? {
    if (date == null) return ErreurDateNaissance.Absente
    val limite = dateNaissanceMinimum(aujourdHui)
    return when {
        date.isAfter(aujourdHui) -> ErreurDateNaissance.DansLeFutur
        date.isBefore(limite) -> ErreurDateNaissance.TropAncienne(limite)
        else -> null
    }
}
