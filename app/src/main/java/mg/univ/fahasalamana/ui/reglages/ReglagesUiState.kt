package mg.univ.fahasalamana.ui.reglages

import androidx.compose.runtime.Immutable
import java.time.LocalDate

/**
 * État de l'écran Réglages (CDC §B7.2, US-B7 et US-B10).
 *
 * `data class` au niveau de l'écran, et un `sealed interface` à l'intérieur pour le seul bloc
 * qui peut échouer : deux blocs ont des sorts indépendants. Un `calendrier.json` illisible ne
 * doit pas emporter le bloc « Sécurité » — c'est justement quand quelque chose ne va pas
 * qu'il faut pouvoir couper ou remettre le code de verrouillage.
 *
 * (B15 avait fait de `ReglagesUiState` lui-même un `sealed interface` : à l'époque un seul
 * bloc dépendait de données. B18 en ajoute un second, d'où ce niveau supplémentaire.)
 *
 * @param reference bloc « Données de référence » (US-B7), avec ses trois états.
 * @param verrouillageActif un code de verrouillage est configuré sur ce téléphone (US-B10).
 */
@Immutable
data class ReglagesUiState(
    val reference: EtatReference = EtatReference.Chargement,
    val verrouillageActif: Boolean = false,
)

/** Les trois états du bloc « Données de référence ». */
sealed interface EtatReference {

    /** Première lecture du calendrier en base, avant la première valeur du Flow. */
    data object Chargement : EtatReference

    /** Les informations de source sont connues et affichables. */
    data class Pret(val donnees: DonneesReference) : EtatReference

    /**
     * La lecture des informations de référence a échoué.
     *
     * Aucun message n'est porté par l'état : il vient de `strings.xml` côté écran, et il ne
     * doit contenir aucune donnée personnelle.
     */
    data object Erreur : EtatReference
}

/**
 * Ce que le parent doit pouvoir lire sur l'origine du calendrier (US-B7).
 *
 * `source`, `publieLe` et `version` viennent du `calendrier.json` chargé en base
 * (contrat §B5.1) ; les deux dernières valeurs viennent des préférences locales.
 *
 * @param sourceCalendrier phrase de provenance publiée avec le calendrier, affichée telle
 *   quelle : c'est elle qui porte la mention de démonstration côté données.
 * @param calendrierPublieLe date de publication du calendrier de référence.
 * @param versionCalendrier entier strictement croissant du contrat §B5.1.
 * @param versionAnnuaire version du `csb.json` chargé, `null` tant qu'aucun annuaire n'a
 *   été chargé sur ce téléphone.
 * @param derniereVerification jour de la dernière recherche de mise à jour, `null` tant
 *   qu'aucune n'a eu lieu — ce qui est toujours le cas tant que B19 n'est pas livrée.
 */
@Immutable
data class DonneesReference(
    // Les trois champs du calendrier sont facultatifs : tant que le contenu embarqué n'est
    // pas chargé — ou si son chargement a échoué au démarrage — la provenance est inconnue.
    // Sans cela, l'écran resterait sur « Chargement… » indéfiniment.
    val sourceCalendrier: String?,
    val calendrierPublieLe: LocalDate?,
    val versionCalendrier: Int?,
    val versionAnnuaire: Int?,
    val derniereVerification: LocalDate?,
)
