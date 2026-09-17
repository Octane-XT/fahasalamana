package mg.univ.fahasalamana.ui.reglages

import androidx.compose.runtime.Immutable
import mg.univ.fahasalamana.data.repository.ResultatSync
import java.time.LocalDate

/**
 * État de l'écran Réglages (CDC §B7.2, US-B7, US-B10 et US-B11).
 *
 * `data class` au niveau de l'écran, et un `sealed interface` à l'intérieur pour le seul bloc
 * qui peut échouer : les blocs ont des sorts indépendants. Un `calendrier.json` illisible ne
 * doit pas emporter le bloc « Sécurité » — c'est justement quand quelque chose ne va pas
 * qu'il faut pouvoir couper ou remettre le code de verrouillage.
 *
 * (B15 avait fait de `ReglagesUiState` lui-même un `sealed interface` : à l'époque un seul
 * bloc dépendait de données. B18 en ajoute un second, d'où ce niveau supplémentaire.)
 *
 * (B19) La recherche de mise à jour est le troisième champ de ce même état, et non un second
 * `StateFlow` : l'écran n'a ainsi qu'un seul état à collecter, et le compte rendu ne peut pas
 * annoncer des versions que la carte au-dessus n'affiche pas encore, le temps d'une
 * recomposition. Elle est posée **à côté** de [reference] et non dans [EtatReference.Pret],
 * pour la même raison que le verrouillage : un calendrier illisible est précisément le moment
 * où l'on va chercher une mise à jour, et le compte rendu de cette recherche — y compris son
 * échec — doit rester lisible alors que le bloc de référence, lui, est en erreur.
 *
 * @param reference bloc « Données de référence » (US-B7), avec ses trois états.
 * @param verrouillageActif un code de verrouillage est configuré sur ce téléphone (US-B10).
 * @param miseAJour où en est la recherche de mise à jour des contenus de référence (US-B11).
 */
@Immutable
data class ReglagesUiState(
    val reference: EtatReference = EtatReference.Chargement,
    val verrouillageActif: Boolean = false,
    val miseAJour: EtatMiseAJour = EtatMiseAJour(),
)

/** Les trois états du bloc « Données de référence ». */
sealed interface EtatReference {

    /** Première lecture du calendrier en base, avant la première valeur du Flow. */
    data object Chargement : EtatReference

    /**
     * Les informations de source sont connues et affichables.
     *
     * @param donnees ce qui est en base : versions, provenance, date du dernier contrôle.
     */
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

/**
 * Où en est le bouton « Vérifier les mises à jour » (B19, US-B11).
 *
 * @param enCours une vérification est en cours : le bouton est inactif et annonce son
 *   avancement par du texte, pas seulement par un indicateur qui tourne — TalkBack ne lit
 *   pas une animation.
 * @param resultat le compte rendu de la dernière vérification, ou `null` s'il n'y a rien à
 *   annoncer. **Événement à consommer**, effacé quand le parent ferme le panneau : sans
 *   cela, une rotation de l'écran réafficherait un compte rendu déjà lu.
 *
 *   Le type vient de `data/repository` et traverse donc une couche. C'est déjà le cas de
 *   `InfosSource` dans ce même écran, et c'est préférable à un triplé de types d'affichage
 *   qui ne ferait que recopier les mêmes cinq issues : les libellés, eux, restent dans
 *   `strings_maj_reference.xml`, et l'état ne porte aucune chaîne.
 */
@Immutable
data class EtatMiseAJour(
    val enCours: Boolean = false,
    val resultat: ResultatSync? = null,
)
