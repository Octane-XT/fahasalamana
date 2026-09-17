package mg.univ.fahasalamana.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/*
 * ---------------------------------------------------------------------------
 * L'ACTION « APPELER » DE LA FICHE D'UN CENTRE (tâche B14, CDC §B0 couche 3)
 * ---------------------------------------------------------------------------
 *
 * « Appeler » ouvre le composeur du téléphone. L'application **délègue** : elle ne compose
 * pas l'appel elle-même, elle passe la main à ce qui est installé. C'est l'argument du
 * CDC — 100 % hors ligne, aucune permission d'appel.
 *
 * Le vrai sujet de cette classe est le cas où il n'y a personne au bout (US-B6 scénario 3).
 * Sur un téléphone d'entrée de gamme, ou dans un profil restreint, `startActivity` lève
 * `ActivityNotFoundException` et l'application s'arrête. Ici, l'échec est une valeur de
 * retour ([ResultatIntent]), pas une exception, et l'écran a de quoi proposer le repli :
 * copier le numéro, qu'on colle ensuite dans un SMS.
 *
 * L'URI elle-même est fabriquée dans `LiensCentre.kt`, sans Android, pour être testée en JVM.
 */

/**
 * Issue d'une tentative de délégation à une autre application.
 *
 * Trois cas et non un booléen : « aucune application installée » et « l'annuaire ne publie
 * pas de numéro composable » demandent deux messages différents. Le premier propose une
 * copie, le second n'a rien à copier.
 */
enum class ResultatIntent {

    /** Une application a pris la main : le composeur est ouvert sur le numéro. */
    OUVERT,

    /** Personne ne gère cet Intent sur ce téléphone. C'est le scénario 3 de US-B6. */
    AUCUNE_APPLICATION,

    /**
     * Le champ publié ne contient aucun chiffre (vide, ou « non communiqué ») : il n'y a
     * pas d'URI à ouvrir, donc pas d'Intent. Distinct de [AUCUNE_APPLICATION], où le
     * numéro existe et mérite d'être copié.
     */
    RIEN_A_OUVRIR,
}

/**
 * Les appels au système faits depuis la fiche d'un centre.
 *
 * Regroupés dans une classe plutôt qu'écrits dans le composable pour trois raisons : le
 * composable n'a pas à connaître `Intent`, l'écran peut recevoir une autre implémentation
 * dans un test d'interface (B21), et le repli du scénario 3 reste écrit à un seul endroit.
 *
 * @param context contexte de l'écran. Fourni par [rememberActionsCentre] depuis
 *   `LocalContext`, donc rattaché à l'Activity et non au contexte d'application.
 */
@Stable
class ActionsCentre(private val context: Context) {

    /**
     * Ouvre le composeur téléphonique avec le numéro déjà saisi (`ACTION_DIAL`).
     *
     * `ACTION_DIAL` et **pas** `ACTION_CALL` : l'appel n'est jamais passé par
     * l'application. Le parent voit le numéro, et c'est lui qui appuie sur le bouton vert.
     * `ACTION_CALL` demanderait la permission `CALL_PHONE`, absente du CDC, et déclencher
     * un appel sans confirmation depuis une application de santé serait une faute — le
     * numéro peut être faux, le crédit compté, l'appel payant.
     *
     * Aucune permission n'est nécessaire pour `ACTION_DIAL` : c'est justement son intérêt.
     */
    fun appeler(telephone: String): ResultatIntent {
        val uri = uriTelephone(telephone) ?: return ResultatIntent.RIEN_A_OUVRIR
        return lancer(Intent(Intent.ACTION_DIAL, Uri.parse(uri)))
    }

    /**
     * Met [texte] dans le presse-papiers. Renvoie `false` si le service est indisponible.
     *
     * C'est la moitié utile du repli : sans application de téléphonie, le numéro copié se
     * colle dans un SMS, un carnet ou un autre appareil. Un message qui dirait seulement
     * « aucune application » laisserait le parent sans rien.
     *
     * @param etiquette nom du contenu, affiché par certains gestionnaires de presse-papiers.
     */
    fun copier(etiquette: String, texte: String): Boolean {
        val pressePapiers = ContextCompat.getSystemService(context, ClipboardManager::class.java)
            ?: return false
        pressePapiers.setPrimaryClip(ClipData.newPlainText(etiquette, texte))
        return true
    }

    /**
     * Vrai à partir d'Android 13, où le système affiche lui-même une confirmation de copie.
     *
     * L'écran s'en sert pour ne pas afficher un second message par-dessus celui du système,
     * qui dirait deux fois la même chose au même endroit de l'écran.
     */
    val systemeConfirmeLaCopie: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Démarre [intent], et traduit son échec en valeur de retour.
     *
     * **Deux gardes, et c'est volontaire.** `resolveActivity` est la vérification demandée
     * par le CDC (§B0 couche 3), mais elle dépend du bloc `<queries>` du manifeste : sur
     * Android 11 et au-delà, une déclaration incomplète la fait répondre « personne » alors
     * qu'un composeur est bien installé. S'y fier seule ferait rater le scénario 2 — le
     * bouton proposerait de copier le numéro au lieu d'ouvrir le composeur. On tente donc
     * toujours le démarrage, et c'est `ActivityNotFoundException` qui tranche : elle, elle
     * ne peut pas mentir.
     *
     * `SecurityException` est traitée comme une absence d'application : certains
     * gestionnaires d'entreprise ou profils restreints interdisent le composeur. Du point
     * de vue du parent, le résultat est le même — rien ne s'ouvre, et le repli doit venir.
     */
    private fun lancer(intent: Intent): ResultatIntent {
        if (intent.resolveActivity(context.packageManager) == null) {
            Log.i(TAG, "Aucune application déclarée pour " + intent.action + " ; tentative directe.")
        }

        // Depuis un contexte qui n'est pas une Activity, Android exige FLAG_ACTIVITY_NEW_TASK
        // et lève sinon une IllegalArgumentException. `LocalContext` est presque toujours
        // l'Activity, mais « presque » suffit à planter une démonstration.
        val hote = context.activiteHote()
        val aLancer = if (hote != null) intent else intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            (hote ?: context).startActivity(aLancer)
            ResultatIntent.OUVERT
        } catch (erreur: ActivityNotFoundException) {
            Log.i(TAG, "Aucune application ne gère " + intent.action + " : repli presse-papiers.", erreur)
            ResultatIntent.AUCUNE_APPLICATION
        } catch (erreur: SecurityException) {
            Log.w(TAG, "Démarrage refusé pour " + intent.action + " : repli presse-papiers.", erreur)
            ResultatIntent.AUCUNE_APPLICATION
        }
    }

    private companion object {

        /** Visible dans logcat pendant la démonstration des scénarios 2 et 3. */
        const val TAG = "FAHASALAMANA_INTENT"
    }
}

/**
 * [ActionsCentre] rattachée au contexte de l'écran, conservée d'une recomposition à l'autre.
 *
 * Déclarée ici plutôt que dans `ui/` pour que `DetailCentreScreen` n'ait pas une seule
 * ligne d'API Android : l'écran demande des actions, il ne sait pas qu'elles sont des
 * `Intent`.
 */
@Composable
fun rememberActionsCentre(): ActionsCentre {
    val contexte = LocalContext.current
    return remember(contexte) { ActionsCentre(contexte) }
}

/**
 * Remonte la chaîne des `ContextWrapper` jusqu'à l'Activity, ou `null` s'il n'y en a pas.
 *
 * `LocalContext.current` est en pratique un `ContextThemeWrapper` posé sur l'Activity, pas
 * l'Activity elle-même : un simple `context as? Activity` renverrait `null` et ferait
 * passer tous les démarrages par `FLAG_ACTIVITY_NEW_TASK`, ce qui sortirait le composeur
 * de la pile de l'application.
 */
private fun Context.activiteHote(): Activity? {
    var courant: Context? = this
    while (courant is ContextWrapper) {
        if (courant is Activity) return courant
        courant = courant.baseContext
    }
    return null
}
