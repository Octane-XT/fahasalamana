package mg.univ.fahasalamana.ui.importation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import mg.univ.fahasalamana.R

/*
 * Ce que l'application a réellement pu faire des rappels après une écriture en masse, et ce
 * qu'elle en dit.
 *
 * Deux endroits déclenchent `PlanificateurRappels.replanifierTout()` (§B8) : l'import d'un
 * carnet (B17) et le remplacement du calendrier de référence (B19). Les deux avalent son
 * échec — c'est le bon arbitrage, l'écriture est faite et ne doit pas être présentée comme
 * ratée — mais les deux annonçaient ensuite « les rappels ont été recalculés » sans savoir
 * si c'était vrai. Sur un carnet de vaccination, cette phrase est celle qui dit au parent
 * qu'il sera prévenu : fausse, il ne le sera pas et ne le saura pas.
 *
 * D'où un seul état et un seul bloc d'affichage, partagés par les deux écrans, comme
 * `import_rappels_recalcules` est déjà la seule et même chaîne pour les deux (voir l'en-tête
 * de `strings_maj_reference.xml`). En dupliquer une variante par écran, c'est exactement ce
 * qui les laisserait diverger une seconde fois.
 *
 * Le fichier vit dans `ui/importation/` et non dans `ui/reglages/` pour garder la dépendance
 * dans le sens qu'elle a déjà : `ReglagesScreen` pose `LigneImportCarnet()` et reprend ses
 * chaînes, jamais l'inverse. Si un troisième déclencheur apparaît, sa place sera
 * `ui/components/`.
 */

/**
 * Où en est la replanification des rappels qui suit une écriture en masse.
 *
 * Un état à part et non un `Boolean` : [EnCours] existe pour le second essai, pendant lequel
 * le panneau reste affiché et doit dire ce qu'il est en train de faire plutôt que de garder
 * la phrase d'échec sous un bouton devenu inactif.
 *
 * L'absence de valeur (`null` côté état d'écran) veut dire « aucune replanification n'était
 * attendue ici » — le cas d'une mise à jour qui n'a pas remplacé le calendrier — et n'affiche
 * rien du tout : il n'y a rien à annoncer.
 */
enum class EtatReplanification {

    /** Un essai est en cours : ni la réussite ni l'échec ne sont encore connus. */
    EnCours,

    /** Les rappels de tous les enfants ont été recalculés et réenfilés. */
    Reussie,

    /**
     * La replanification a échoué. L'écriture qui l'a déclenchée, elle, est faite : c'est
     * tout l'objet du message, et la raison pour laquelle l'échec ne remonte pas plus haut.
     */
    Echouee,
}

/**
 * La phrase sur les rappels, à poser à la fin d'un panneau de compte rendu.
 *
 * Émet directement dans la `Column` qui l'appelle, pour hériter de son espacement et de sa
 * couleur de contenu : le panneau garde son apparence de réussite même quand les rappels
 * ont manqué, parce que l'import ou la mise à jour, eux, ont bien eu lieu.
 *
 * @param etat résultat réel de la replanification, ou `null` s'il n'y en avait pas à faire.
 * @param detailEchec la phrase qui rappelle ce qui **a** été enregistré, propre à l'appelant
 *   (le carnet importé pour B17, le calendrier installé pour B19) : c'est elle qui empêche de
 *   lire l'échec des rappels comme un échec de l'opération. Lue seulement en cas d'échec.
 * @param onReessayer relance la replanification sans refaire l'import ni le téléchargement.
 */
@Composable
fun RappelsReplanifies(
    etat: EtatReplanification?,
    detailEchec: String,
    onReessayer: () -> Unit,
) {
    when (etat) {
        // Rien à annoncer : ne rien écrire vaut mieux qu'une phrase qui remplit la place.
        null -> Unit

        EtatReplanification.EnCours -> Text(
            text = stringResource(R.string.import_rappels_en_cours),
            style = MaterialTheme.typography.bodyMedium,
        )

        EtatReplanification.Reussie -> Text(
            text = stringResource(R.string.import_rappels_recalcules),
            style = MaterialTheme.typography.bodyMedium,
        )

        // Ce qui est vrai d'abord, ce qui manque ensuite, le geste qui y remédie enfin
        // (R7) : le parent n'y est pour rien et sa donnée est enregistrée.
        EtatReplanification.Echouee -> {
            Text(
                text = detailEchec,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.import_rappels_non_recalcules),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onReessayer) {
                Text(stringResource(R.string.import_rappels_reessayer))
            }
        }
    }
}
