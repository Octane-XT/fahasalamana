package mg.univ.fahasalamana.platform

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.data.repository.EnfantRepository
import org.koin.compose.koinInject
import java.time.LocalDate

/*
 * Notification de test (Definition of Done de B10 : « notification de test depuis les
 * réglages, build debug »).
 *
 * Ce fichier vit dans `src/debug/` et **pas** dans `src/main/` : le source set `release`
 * en contient un homonyme vide, de même signature. La version publiée n'embarque donc ni
 * ce code, ni les textes de `src/debug/res/`, et le bloc disparaît de l'écran Réglages sans
 * qu'aucun `if (BuildConfig.DEBUG)` ne traîne dans le code de production.
 *
 * Ce qu'il démontre en soutenance, sans attendre trois jours ni B11 :
 *  - le canal et l'apparence de la notification (§B8) ;
 *  - le parcours de permission POST_NOTIFICATIONS si elle n'a pas encore été accordée ;
 *  - le lien profond : toucher la notification ouvre la fiche de l'enfant, et le retour
 *    mène à « Mes enfants » (pile reconstruite, CDC §B7.1).
 */

/** Dose de démonstration : celle du scénario de US-B5. */
private const val VACCIN_DEMO: String = "penta1"

/** Identifiant utilisé quand le carnet est vide : le lien profond mènera à « Introuvable ». */
private const val ENFANT_DEMO: String = "demo-enfant"

/** Trois jours, comme la règle R3, pour que le texte de la notification soit celui de US-B5. */
private const val JOURS_DEMO: Long = 3L

/**
 * Bloc « Rappels » des réglages, build debug uniquement.
 *
 * Il vise **le premier enfant du carnet** plutôt qu'un identifiant inventé : le lien profond
 * ouvre alors une vraie fiche, ce qui est tout l'intérêt de la démonstration. Carnet vide, il
 * retombe sur un identifiant fictif et la fiche affiche son état « Introuvable » — ce qui
 * démontre quand même la navigation, et se corrige en ajoutant un enfant.
 */
@Composable
fun BlocRappelsDebug(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.debug_rappels_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 4.dp)
                .semantics { heading() },
        )

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.debug_rappels_explication),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Les aperçus de l'écran Réglages n'ont ni Koin, ni gestionnaire de
                // notifications : ils s'arrêtent au texte ci-dessus.
                if (!LocalInspectionMode.current) {
                    BoutonNotificationDeTest()
                }
            }
        }
    }
}

@Composable
private fun BoutonNotificationDeTest(enfants: EnfantRepository = koinInject()) {
    val contexte = LocalContext.current
    val notifications = remember(contexte) { NotificationHelper(contexte) }

    // `remember` sur le repository : `observerTous()` fabrique un Flow à chaque appel, et le
    // recréer à chaque recomposition relancerait la requête Room sans fin.
    val flux = remember(enfants) { enfants.observerTous() }
    val carnet by flux.collectAsStateWithLifecycle(initialValue = emptyList())
    val premier = carnet.firstOrNull()?.enfant

    var autorisees by remember { mutableStateOf(notifications.notificationsAutorisees()) }
    var resultat by remember { mutableStateOf<Int?>(null) }

    // Relu au retour au premier plan : on revient souvent ici après être allé activer les
    // notifications dans les réglages d'Android, et l'état affiché doit suivre.
    LifecycleResumeEffect(notifications) {
        autorisees = notifications.notificationsAutorisees()
        onPauseOrDispose { }
    }

    val contenu = ContenuRappel(
        enfantId = premier?.id ?: ENFANT_DEMO,
        vaccinId = VACCIN_DEMO,
        prenomEnfant = premier?.prenom ?: stringResource(R.string.debug_rappels_prenom_demo),
        libelleVaccin = stringResource(R.string.debug_rappels_vaccin_demo),
        prevuLe = LocalDate.now(ZONE_MADAGASCAR).plusDays(JOURS_DEMO),
        joursRestants = JOURS_DEMO,
    )

    val lanceur = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        autorisees = notifications.notificationsAutorisees()
        resultat = if (autorisees && notifications.afficherRappel(contenu)) {
            R.string.debug_rappels_envoyee
        } else {
            R.string.debug_rappels_bloquee
        }
    }

    Text(
        text = stringResource(
            if (autorisees) R.string.debug_rappels_etat_autorise else R.string.debug_rappels_etat_refuse,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (premier == null) {
        Text(
            text = stringResource(R.string.debug_rappels_sans_enfant),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Button(
        onClick = {
            val possible = notifications.notificationsAutorisees()
            autorisees = possible
            when {
                possible -> resultat = if (notifications.afficherRappel(contenu)) {
                    R.string.debug_rappels_envoyee
                } else {
                    R.string.debug_rappels_bloquee
                }

                // Android 13+ : la boîte système ne s'affichera que si l'utilisateur n'a pas
                // déjà refusé deux fois ; sinon le résultat revient « refusé » immédiatement.
                permissionNotificationsRequise() -> lanceur.launch(Manifest.permission.POST_NOTIFICATIONS)

                else -> resultat = R.string.debug_rappels_bloquee
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.debug_rappels_action))
    }

    resultat?.let { message ->
        Text(
            text = stringResource(message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
