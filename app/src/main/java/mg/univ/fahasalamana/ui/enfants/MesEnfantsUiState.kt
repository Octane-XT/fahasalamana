package mg.univ.fahasalamana.ui.enfants

import androidx.compose.runtime.Immutable
import mg.univ.fahasalamana.domain.AgeEnfant
import mg.univ.fahasalamana.domain.Enfant
import mg.univ.fahasalamana.domain.LigneEcheancier
import mg.univ.fahasalamana.domain.ResumeEnfant
import mg.univ.fahasalamana.domain.StatutVaccin
import mg.univ.fahasalamana.domain.ageDepuis
import java.time.LocalDate

/**
 * État de l'écran « Mes enfants » (US-B8, wireframe §B7.2).
 *
 * Quatre états exclusifs plutôt qu'une `data class` avec un booléen `vide` : « aucun
 * enfant » et « liste pas encore lue » ne s'affichent pas du tout pareil, et les confondre
 * ferait clignoter « Ajoutez votre premier enfant » pendant la première lecture de la base.
 */
sealed interface MesEnfantsUiState {

    /** Première lecture de la base, avant la première émission des `Flow`. */
    data object Chargement : MesEnfantsUiState

    /** Aucun enfant enregistré : c'est le premier lancement, pas une anomalie (US-B8). */
    data object Vide : MesEnfantsUiState

    /** Au moins un enfant, déjà trié pour l'affichage (voir `MesEnfantsViewModel`). */
    data class Pret(val enfants: List<LigneEnfant>) : MesEnfantsUiState

    /**
     * La lecture du carnet a échoué.
     *
     * Aucun message n'est porté par l'état : il vient de `strings.xml` côté écran, et il ne
     * doit contenir aucune donnée personnelle (prénom, date de naissance).
     */
    data object Erreur : MesEnfantsUiState
}

/**
 * Une carte de la liste : un enfant, son âge, et son résumé (règle R6).
 *
 * [resume] est l'objet produit tel quel par `CalculateurEcheancier.resume()` : les trois
 * chiffres de R6 ne sont recopiés nulle part, ce qui garantit que l'écran et la fiche
 * enfant affichent les mêmes.
 *
 * @param prochainVaccinNom nom du vaccin de [ResumeEnfant.prochaineEcheance], `null` s'il
 *   ne reste plus rien à faire ou si plus aucun vaccin du calendrier ne porte cette date.
 *   La date peut être passée : c'est alors la dose en retard la plus ancienne.
 * @param prochainVaccinDose dose correspondante (« 1re dose »), même condition.
 */
@Immutable
data class LigneEnfant(
    val id: String,
    val prenom: String,
    val age: AgeEnfant,
    val resume: ResumeEnfant,
    val prochainVaccinNom: String?,
    val prochainVaccinDose: String?,
) {
    /** Rien en retard et rien à faire aujourd'hui : la carte affiche « À jour » (wireframe §B7.2). */
    val aJour: Boolean get() = resume.nbEnRetard == 0 && resume.nbAFaire == 0
}

/**
 * Assemble la carte d'un enfant à partir de son échéancier déjà calculé.
 *
 * Fonction pure, hors ViewModel et hors composable : elle ne calcule aucune règle métier —
 * [resume] arrive tel quel du `CalculateurEcheancier` (R6), [echeancier] aussi (R1, R2) et
 * l'âge de `domain.ageDepuis`, partagé avec la fiche enfant. Ce qu'elle fait tient en un
 * geste d'affichage : retrouver de quel vaccin il s'agit à la date de la prochaine
 * échéance, que R6 ne renvoie pas.
 *
 * @param aujourdHui jour de référence, venu de `horlogeJour()` : l'âge affiché change à
 *   minuit comme les statuts, sans qu'il faille rouvrir l'application.
 */
internal fun ligneEnfant(
    enfant: Enfant,
    echeancier: List<LigneEcheancier>,
    resume: ResumeEnfant,
    aujourdHui: LocalDate,
): LigneEnfant {
    // `echeancier` est trié par `ordre` : la première ligne qui tombe à cette date est la
    // plus précoce du calendrier, donc celle qu'on annonce quand plusieurs coïncident.
    val prochain = resume.prochaineEcheance?.let { date ->
        echeancier.firstOrNull { ligne ->
            ligne.prevuLe == date && ligne.statut !is StatutVaccin.Fait
        }
    }

    return LigneEnfant(
        id = enfant.id,
        prenom = enfant.prenom,
        age = ageDepuis(enfant.dateNaissance, aujourdHui),
        resume = resume,
        prochainVaccinNom = prochain?.vaccin?.nom,
        prochainVaccinDose = prochain?.vaccin?.dose,
    )
}
