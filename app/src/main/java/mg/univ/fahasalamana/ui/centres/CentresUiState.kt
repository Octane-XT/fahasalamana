package mg.univ.fahasalamana.ui.centres

import androidx.compose.runtime.Immutable
import mg.univ.fahasalamana.domain.Centre
import mg.univ.fahasalamana.domain.District
import mg.univ.fahasalamana.domain.Region

/**
 * État de l'écran « Centres de santé » (tâche B13, US-B6 scénario 1, wireframe §B7.2).
 *
 * Deux niveaux d'états, et c'est volontaire :
 *
 *  - le niveau écran ([CentresUiState]) dit si l'annuaire lui-même est lisible ; tant qu'il
 *    ne l'est pas, les deux menus déroulants n'ont rien à proposer et ne sont pas affichés ;
 *  - le niveau liste ([ResultatCentres]) dit ce qu'il y a sous les menus. Les menus, eux,
 *    restent à l'écran dans tous les cas : c'est par eux qu'on sort d'un district vide.
 *
 * Confondre les deux ferait disparaître les menus à chaque district sans centre, et il
 * faudrait quitter l'onglet pour en choisir un autre.
 */
sealed interface CentresUiState {

    /** Première lecture des régions, avant la première émission du `Flow` Room. */
    data object Chargement : CentresUiState

    /**
     * Aucune région en base : l'annuaire embarqué n'a pas encore été chargé (B04) ou une
     * mise à jour (B19) l'a vidé. Ce n'est pas une erreur de lecture, d'où un état distinct
     * de [Erreur] : le message dit quoi faire, il ne signale pas une panne.
     */
    data object AnnuaireAbsent : CentresUiState

    /**
     * L'annuaire est lisible : les deux menus sont affichés, la zone du bas dépend de
     * [resultat].
     *
     * @param regions les 23 régions, déjà triées par nom en SQL (`CentreDao`).
     * @param regionChoisie la région sélectionnée, `null` tant qu'aucune ne l'est. Retombe à
     *   `null` si la région choisie disparaît d'une mise à jour de l'annuaire pendant que
     *   l'écran est ouvert : l'écran revient alors à son invitation de départ plutôt que
     *   d'afficher un menu qui montre un nom absent de sa propre liste.
     * @param districts les districts de [regionChoisie], vides tant qu'aucune région n'est
     *   choisie. Jamais les 76 districts d'un coup : seuls ceux de la région demandée.
     * @param districtChoisi le district sélectionné, même règle de retombée que ci-dessus.
     */
    data class Pret(
        val regions: List<OptionAnnuaire>,
        val regionChoisie: OptionAnnuaire?,
        val districts: List<OptionAnnuaire>,
        val districtChoisi: OptionAnnuaire?,
        val resultat: ResultatCentres,
    ) : CentresUiState {

        /** Le second menu reste inactif tant qu'aucune région n'est choisie (§B7.2). */
        val districtActif: Boolean get() = regionChoisie != null
    }

    /**
     * La lecture de l'annuaire a échoué.
     *
     * Aucun message n'est porté par l'état : il vient de `strings.xml` côté écran.
     */
    data object Erreur : CentresUiState
}

/**
 * Une entrée de menu déroulant : ce qu'on affiche, et l'identifiant qu'on renvoie.
 *
 * Le même type sert aux régions et aux districts, ce qui permet d'écrire **un seul**
 * composable de menu au lieu de deux presque identiques.
 */
@Immutable
data class OptionAnnuaire(
    val id: String,
    val nom: String,
)

/**
 * Ce qui s'affiche sous les deux menus. Cinq situations exclusives, toutes atteignables.
 *
 * `sealed interface` plutôt qu'une liste éventuellement vide : « je n'ai pas encore choisi
 * de région », « je n'ai pas encore choisi de district » et « ce district n'a aucun centre »
 * sont trois écrans différents, alors qu'ils donnent tous les trois une liste vide.
 */
sealed interface ResultatCentres {

    /** Rien n'est choisi : l'écran invite à commencer par une région (§B7.2). */
    data object SansRegion : ResultatCentres

    /** Une région est choisie, pas encore de district. */
    data object SansDistrict : ResultatCentres

    /** District choisi, ses centres pas encore remontés de la base. */
    data object Chargement : ResultatCentres

    /** District choisi, mais l'annuaire ne publie aucun centre pour lui. */
    data object Aucun : ResultatCentres

    /** Les centres du district, déjà triés par nom en SQL (`CentreDao`). */
    @Immutable
    data class Liste(val centres: List<LigneCentre>) : ResultatCentres
}

/**
 * Une carte de la liste : exactement ce que le wireframe §B7.2 montre, et rien de plus.
 *
 * Le téléphone du centre n'est volontairement pas ici : il n'est pas affiché dans la liste,
 * et l'écran de détail (B14) le relit par `observerCentre(id)`. Un numéro qui ne sert à rien
 * n'a pas à traverser l'état de l'écran.
 *
 * @param type niveau publié (« CSB1 », « CSB2 »…), affiché tel quel : l'annuaire peut en
 *   introduire un nouveau sans qu'une version de l'application sorte (voir `domain/Annuaire.kt`).
 */
@Immutable
data class LigneCentre(
    val id: String,
    val nom: String,
    val type: String,
    val horaires: String,
    val adresse: String,
)

// --- Passages du domaine à l'affichage ---------------------------------------
//
// Trois traductions d'une ligne, hors ViewModel et hors composable : elles ne calculent
// rien, elles retirent ce que l'écran n'affiche pas.

internal fun Region.enOption(): OptionAnnuaire = OptionAnnuaire(id = id, nom = nom)

internal fun District.enOption(): OptionAnnuaire = OptionAnnuaire(id = id, nom = nom)

internal fun Centre.enLigne(): LigneCentre = LigneCentre(
    id = id,
    nom = nom,
    type = type,
    horaires = horaires,
    adresse = adresse,
)
