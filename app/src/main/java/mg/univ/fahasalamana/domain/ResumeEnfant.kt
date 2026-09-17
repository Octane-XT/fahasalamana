package mg.univ.fahasalamana.domain

import java.time.LocalDate

/**
 * Ce que le domaine sait de la **prochaine dose à faire** d'un enfant (règle R6).
 *
 * Trois cas, et non une `LocalDate?`. L'absence de date recouvrait deux situations
 * opposées — « il ne reste plus rien à faire » et « aucune date n'est calculable » — que
 * seul l'échéancier brut permettait de départager. La liste des enfants le faisait donc
 * elle-même, à partir de cet échéancier : une règle métier dans la couche d'affichage
 * (règle 1 de CLAUDE.md). Recopiée ensuite dans la fiche enfant, elle y avait divergé — la
 * fiche annonçait « À jour » à un enfant dont rien n'avait pu être calculé.
 *
 * Type scellé : les `when` sur ce type s'écrivent sans `else`, et un quatrième cas ferait
 * échouer la compilation à chaque point d'affichage plutôt que d'y passer inaperçu.
 */
sealed interface ProchaineEcheance {

    /**
     * Une dose reste à faire, le [date] : la plus proche date prévue parmi toutes les
     * lignes qui ne sont pas faites — [StatutVaccin.AVenir], [StatutVaccin.EnAttente],
     * [StatutVaccin.AFaire] et [StatutVaccin.EnRetard].
     *
     * Elle peut être **antérieure au jour de calcul**, et c'est voulu : restreinte aux
     * seules dates encore à venir, la carte affichait « Prochain : aucune échéance à
     * venir » juste sous « 3 en retard », alors qu'il restait précisément trois doses à
     * faire.
     */
    data class Prevue(val date: LocalDate) : ProchaineEcheance

    /**
     * Toutes les doses du calendrier sont faites : il n'y a plus d'échéance parce qu'il
     * n'y a plus rien à faire. C'est le seul cas où un écran a le droit d'écrire que le
     * carnet est complet.
     */
    data object CarnetComplet : ProchaineEcheance

    /**
     * Aucune date n'est calculable, et rien ne permet de dire que le carnet est complet :
     * calendrier de référence encore vide (amorçage des assets pas terminé, ou en échec —
     * `App.kt` le journalise et continue), ou chaîne `dependDe` cassée de bout en bout
     * (voir `CalculateurEcheancier.datePrevue`).
     *
     * **Ce n'est pas « à jour ».** Sur une application de santé, la différence entre
     * « rien à faire » et « rien à calculer » ne doit jamais se perdre : l'écran affiche
     * l'attente du calendrier, pas une pastille verte.
     */
    data object Indeterminable : ProchaineEcheance
}

/**
 * Synthèse d'un échéancier (règle R6). Sert aux badges de la fiche et au tri de
 * la liste « Mes enfants » par nombre de retards (US-B8).
 *
 * @param nbEnRetard nombre de lignes [StatutVaccin.EnRetard].
 * @param nbAFaire nombre de lignes [StatutVaccin.AFaire] : la fenêtre est ouverte aujourd'hui.
 * @param prochaineEcheance la prochaine dose à faire, ou la raison pour laquelle il n'y en
 *   a pas. Voir [ProchaineEcheance] : les trois cas sont distincts dans le domaine, et
 *   aucun écran n'a plus à les reconstituer depuis l'échéancier.
 */
data class ResumeEnfant(
    val nbEnRetard: Int,
    val nbAFaire: Int,
    val prochaineEcheance: ProchaineEcheance,
) {

    /**
     * Rien en retard et rien à faire aujourd'hui, **sur un échéancier qui existe** : la
     * carte de la liste affiche « À jour » (wireframe §B7.2) et la fiche son badge vert.
     *
     * La condition sur [ProchaineEcheance.Indeterminable] n'est pas une précaution de
     * plus : les deux compteurs valent zéro aussi bien pour un carnet complet que pour un
     * enfant dont aucune ligne n'a pu être calculée. Sans elle, la carte annonçait « À
     * jour » et « Toutes les doses du calendrier sont faites » à un enfant qui n'a jamais
     * reçu une seule dose.
     *
     * Elle vit ici et non dans un `UiState` : c'est la lecture de R6, et deux écrans la
     * font. Écrite deux fois, elle avait divergé une fois déjà.
     */
    val aJour: Boolean
        get() = nbEnRetard == 0 &&
            nbAFaire == 0 &&
            prochaineEcheance !is ProchaineEcheance.Indeterminable
}
