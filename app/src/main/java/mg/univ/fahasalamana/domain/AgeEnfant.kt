package mg.univ.fahasalamana.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Âge d'un enfant, dans l'unité sous laquelle on l'écrit.
 *
 * Ce fichier existe parce que les deux écrans du carnet le calculaient chacun de leur côté,
 * avec des seuils différents : le même enfant de 18 mois se lisait « 1 an » dans la liste
 * (`MesEnfantsScreen`) et « 18 mois » dans sa fiche (`FicheEnfantScreen`), à un toucher
 * d'intervalle — revue statique du 15/09, point n° 9. Il n'y a plus qu'un calcul, qu'un
 * seuil et qu'un jeu de textes (`age_*` de `res/values/strings.xml`).
 *
 * C'est du calcul sur `LocalDate`, pas de l'affichage : sa place est ici, testable en JVM
 * sans Android (règle 1 de CLAUDE.md, comme `CalculateurEcheancier` ou `grouperParAge`).
 *
 * **Aucun texte dans ce fichier.** Le domaine ne connaît pas `strings.xml` : il répond une
 * unité et un nombre, les écrans les mettent en mots (`ui/components/TexteAge.kt`).
 */

/**
 * Seuil unique de passage aux années : en dessous de 24 mois révolus, un âge s'écrit en mois.
 *
 * Deux ans, et non un an. Le calendrier vaccinal s'arrête à 15 mois (CDC §B5.1) : tant que
 * l'enfant a des doses devant lui, « 18 mois » situe le parent sur sa frise, là où « 1 an »
 * l'en sortirait — un an et demi et un an tout juste ne sont pas le même moment du carnet.
 * Au-delà de 24 mois, plus rien n'est attendu et l'année redevient l'unité qui parle.
 */
const val MOIS_AVANT_ANNEES: Int = 24

/**
 * Seuil de passage aux semaines : en dessous de 14 jours révolus, un âge s'écrit en jours.
 *
 * C'est l'unité dont parle le CSB pendant les premiers jours, et celle des premières lignes
 * du calendrier (« à la naissance », puis « 6 semaines »).
 */
const val JOURS_AVANT_SEMAINES: Int = 14

private const val JOURS_PAR_SEMAINE: Int = 7
private const val MOIS_PAR_AN: Int = 12

/**
 * Âge atteint, exprimé dans une seule unité — celle sous laquelle il se dit.
 *
 * Type scellé plutôt qu'un triplet (années, mois, jours) : le choix de l'unité est une
 * décision, et elle se prend **une fois**, ici. Un `when` sans `else` sur ce type oblige
 * chaque point d'affichage à traiter les cinq cas, et un sixième les signalerait tous.
 *
 * Les valeurs portées sont toujours des unités **révolues** : `Semaines(3)` veut dire au
 * moins 21 jours, `Mois(8)` au moins huit mois pleins. Jamais d'arrondi vers le haut — un
 * âge annoncé plus grand qu'il n'est ferait attendre une dose trop tôt.
 */
sealed interface AgeEnfant {

    /**
     * L'enfant est né aujourd'hui : il n'y a pas encore un jour à compter.
     *
     * Cas à part plutôt que `Jours(0)` : « 0 jour » ne se dit pas, et c'est un âge qui
     * s'affiche réellement — l'enfant est ajouté au carnet le jour de sa naissance, à la
     * maternité (US-B1).
     */
    data object JourDeNaissance : AgeEnfant

    /** De 1 à 13 jours révolus. */
    data class Jours(val jours: Int) : AgeEnfant

    /** De 2 semaines révolues au premier mois révolu. */
    data class Semaines(val semaines: Int) : AgeEnfant

    /** De 1 à 23 mois révolus : toute la durée du calendrier vaccinal, et un peu au-delà. */
    data class Mois(val mois: Int) : AgeEnfant

    /** À partir de [MOIS_AVANT_ANNEES] mois révolus. */
    data class Annees(val annees: Int) : AgeEnfant
}

/**
 * Âge atteint le [aujourdHui] par un enfant né le [naissance].
 *
 * Fonction pure : deux dates entrent, une unité sort. Le jour courant est un paramètre et
 * non une lecture d'horloge, pour deux raisons — les tests fixent leurs dates, et les
 * écrans reçoivent le jour de `horlogeJour()`, ce qui fait vieillir l'âge affiché au
 * passage de minuit sans rouvrir l'application.
 *
 * Les mois et les années sont ceux de `java.time` : un enfant né le 15 janvier a 8 mois le
 * 15 septembre. Sur un 29 février, `java.time` compte le mois révolu au 1er mars de l'année
 * suivante, jamais au 28 février — l'âge est alors annoncé au plus juste, donc un jour trop
 * tard plutôt qu'un jour trop tôt.
 *
 * Une date de naissance dans le futur renvoie [AgeEnfant.JourDeNaissance] : le formulaire
 * l'interdit (US-B1), mais un carnet importé (B17) peut contenir n'importe quoi et aucun
 * écran ne doit afficher « -3 mois ».
 */
fun ageDepuis(naissance: LocalDate, aujourdHui: LocalDate): AgeEnfant {
    if (!naissance.isBefore(aujourdHui)) return AgeEnfant.JourDeNaissance

    val jours = ChronoUnit.DAYS.between(naissance, aujourdHui).toInt()
    if (jours < JOURS_AVANT_SEMAINES) return AgeEnfant.Jours(jours)

    val mois = ChronoUnit.MONTHS.between(naissance, aujourdHui).toInt()
    return when {
        mois < 1 -> AgeEnfant.Semaines(jours / JOURS_PAR_SEMAINE)
        mois < MOIS_AVANT_ANNEES -> AgeEnfant.Mois(mois)
        else -> AgeEnfant.Annees(mois / MOIS_PAR_AN)
    }
}
