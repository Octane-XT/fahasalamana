package mg.univ.fahasalamana.platform

import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.first
import mg.univ.fahasalamana.data.local.EnfantAvecVaccins
import mg.univ.fahasalamana.data.local.toDomain
import mg.univ.fahasalamana.data.repository.EnfantRepository
import mg.univ.fahasalamana.data.repository.ReferenceRepository
import mg.univ.fahasalamana.domain.CalculateurEcheancier
import mg.univ.fahasalamana.domain.LigneEcheancier
import mg.univ.fahasalamana.domain.StatutVaccin
import mg.univ.fahasalamana.domain.VaccinReference
import java.time.Duration
import java.time.LocalDateTime

/*
 * Programmation des rappels de vaccination (B11, CDC §B8, règles R3 et R4).
 *
 * Cette classe est le seul endroit de l'application qui enfile un `WorkRequest` : un écran
 * appelle `replanifier(enfantId)` et ne sait rien du reste (règle 9 de CLAUDE.md, §B6).
 *
 * Elle ne contient **aucune règle métier**. Le « quand » vient de
 * `CalculateurEcheancier.rappelsAProgrammer()` (R3), le « sous quel nom » de
 * `nomTravailRappel()` (R4) et le « dans combien de temps » de `delaiInitial()` — les deux
 * derniers étant dans `TraductionRappels.kt`, testés en JVM. Ce qui reste ici, et rien
 * d'autre : lire la base, annuler, enfiler, journaliser.
 */

/** Tag logcat des rappels (CDC §B0, couche Plateforme). 19 caractères, sous la limite d'Android. */
const val ETIQUETTE_LOG_RAPPEL: String = "FAHASALAMANA_RAPPEL"

/** Clé de l'identifiant d'enfant dans les données d'entrée du travail. */
const val CLE_ENFANT_ID: String = "enfantId"

/** Clé de l'identifiant de dose dans les données d'entrée du travail. */
const val CLE_VACCIN_ID: String = "vaccinId"

/** Clé du `TypeRappel`, transportée pour le seul journal. */
const val CLE_TYPE_RAPPEL: String = "typeRappel"

/**
 * Programme, remplace et annule les rappels de vaccination.
 *
 * @param plafondDelai plafond appliqué à tous les délais. Vaut [PLAFOND_DELAI_RAPPEL],
 *   c'est-à-dire une minute en build debug et `null` en release (voir
 *   `platform/DelaiDemonstration.kt`). **Les tests instrumentés de B12 doivent passer
 *   `plafondDelai = null` explicitement** : ils s'exécutent sur le variant debug, donc avec
 *   le plafond de démonstration, et vérifieraient sinon une minute là où ils attendent le
 *   délai réel de R3.
 * @param avecRappelFenetre active le second rappel « fenêtre bientôt fermée » (R3, Should).
 *   Désactivé par défaut, comme dans `rappelsAProgrammer` : c'est un Should du CDC, et en
 *   build debug il doublerait le nombre de notifications reçues à la minute de
 *   démonstration. Le basculer à `true` suffit à l'activer — la plomberie est complète, son
 *   nom de travail étant déjà distinct de celui du rappel principal.
 * @param horloge instant courant, paramètre et non appel implicite : le même instant sert à
 *   `rappelsAProgrammer` (R3) et au calcul des délais, et un test peut le figer.
 */
class PlanificateurRappels(
    private val workManager: WorkManager,
    private val enfants: EnfantRepository,
    private val reference: ReferenceRepository,
    private val calc: CalculateurEcheancier,
    private val notifications: NotificationHelper,
    private val plafondDelai: Duration? = PLAFOND_DELAI_RAPPEL,
    private val avecRappelFenetre: Boolean = false,
    private val horloge: () -> LocalDateTime = { LocalDateTime.now(ZONE_MADAGASCAR) },
) {

    /**
     * Recalcule et réenfile tous les rappels d'un enfant.
     *
     * Déclencheurs prévus par le §B8, tous branchés en B12 : création, modification et
     * suppression d'un enfant, saisie, correction et suppression d'une dose, import, mise à
     * jour du calendrier, activation des notifications.
     *
     * **Idempotente** (R4) : deux appels de suite laissent exactement le même jeu de
     * travaux, parce que les noms uniques sont une fonction pure de (enfant, vaccin, type).
     * On peut donc l'appeler à chaque écriture sans se demander si c'est la première fois.
     *
     * **Enfant absent de la base** : l'appel dégénère proprement en annulation de tous ses
     * rappels. L'appeler après une suppression est donc correct, et revient à [annulerTous].
     *
     * L'ordre — lire, puis annuler, puis enfiler — n'est pas indifférent : une lecture qui
     * échoue laisse les rappels existants en place plutôt que de désarmer le carnet.
     */
    suspend fun replanifier(enfantId: String) {
        val calendrier = reference.observerCalendrier().first()
        val carnet = enfants.observer(enfantId).first()
        replanifier(enfantId, carnet, calendrier)
    }

    /**
     * Recalcule les rappels de **tout** le carnet (§B8).
     *
     * Appelée après un import (B17) et après une mise à jour du calendrier (B19) : dans les
     * deux cas, ce n'est pas un enfant qui a changé mais les données qui servent à calculer
     * l'échéancier de tous.
     *
     * Le calendrier est lu une fois pour l'ensemble du carnet, pas une fois par enfant.
     */
    suspend fun replanifierTout() {
        val calendrier = reference.observerCalendrier().first()
        val carnet = enfants.observerTous().first()
        Log.i(ETIQUETTE_LOG_RAPPEL, "Replanification complète : ${carnet.size} enfant(s)")
        carnet.forEach { avecVaccins -> replanifier(avecVaccins.enfant.id, avecVaccins, calendrier) }
    }

    /**
     * Annule tous les rappels d'un enfant, sans rien relire.
     *
     * C'est le chemin de la suppression (US-B1, scénario « suppression ») : l'enfant n'est
     * plus en base, on ne peut donc plus énumérer ses noms uniques de travail. L'étiquette
     * de groupe posée sur chaque travail, elle, reste interrogeable — d'où
     * `cancelAllWorkByTag` plutôt qu'une série de `cancelUniqueWork`.
     *
     * Avantage annexe : elle attrape aussi les travaux d'une dose retirée d'une version plus
     * récente du calendrier, qu'un parcours de l'échéancier courant ne verrait plus.
     *
     * Non `suspend` : `cancelAllWorkByTag` rend la main immédiatement, WorkManager
     * sérialisant lui-même ses opérations. L'annulation est donc traitée avant les mises en
     * file qui suivent dans [replanifier].
     */
    fun annulerTous(enfantId: String) {
        workManager.cancelAllWorkByTag(etiquetteTravauxEnfant(enfantId))
    }

    // --- Interne --------------------------------------------------------------

    private fun replanifier(
        enfantId: String,
        carnet: EnfantAvecVaccins?,
        calendrier: List<VaccinReference>,
    ) {
        val trace = abregerIdentifiant(enfantId)
        annulerTous(enfantId)

        if (carnet == null) {
            Log.i(ETIQUETTE_LOG_RAPPEL, "Planification enfant=$trace : absent du carnet, rappels annulés")
            return
        }

        val maintenant = horloge()
        val echeancier = calc.echeancier(
            enfant = carnet.enfant.toDomain(),
            calendrier = calendrier,
            administres = carnet.administres.toDomain(),
            aujourdHui = maintenant.toLocalDate(),
        )

        retirerNotificationsDesDosesFaites(enfantId, echeancier)

        val travaux = travauxRappels(
            enfantId = enfantId,
            rappels = calc.rappelsAProgrammer(echeancier, maintenant, avecRappelFenetre),
            maintenant = maintenant,
            plafond = plafondDelai,
        )
        travaux.forEach { travail -> enfiler(travail) }

        Log.i(
            ETIQUETTE_LOG_RAPPEL,
            "Planification enfant=$trace : ${travaux.size} rappel(s) sur ${echeancier.size} dose(s)",
        )
    }

    /**
     * Retire du volet de notifications les rappels des doses désormais faites.
     *
     * Scénario « vaccin saisi avant le rappel » de US-B5 : annuler le travail suffit tant
     * que le rappel n'est pas tombé, mais si le parent saisit la dose après avoir vu la
     * notification, celle-ci resterait affichée à parler d'une dose déjà reçue. Le travail
     * et la notification partagent la même clé ([etiquetteRappel]) précisément pour que les
     * deux s'annulent ensemble.
     *
     * C'est au planificateur de le faire et non à l'écran de saisie : un écran ne connaît ni
     * les rappels ni leur clé (règle 9 de CLAUDE.md).
     */
    private fun retirerNotificationsDesDosesFaites(enfantId: String, echeancier: List<LigneEcheancier>) {
        echeancier.forEach { ligne ->
            if (ligne.statut is StatutVaccin.Fait) {
                notifications.annulerRappel(enfantId, ligne.vaccin.id)
            }
        }
    }

    /**
     * Enfile un travail de rappel (§B8 : `OneTimeWorkRequest` + `initialDelay`).
     *
     * `ExistingWorkPolicy.REPLACE` est la règle R4 elle-même. Il est redondant ici, puisque
     * [annulerTous] vient de tout effacer — et c'est voulu : si l'étiquette de groupe venait
     * à manquer sur un travail enfilé par une version antérieure de l'application, le nom
     * unique le remplacerait quand même. Deux protections pour une, sur le point précis dont
     * dépend l'absence de doublon.
     *
     * Aucune `Constraints` : le rappel ne demande ni réseau, ni charge, ni Wi-Fi. C'est une
     * application hors ligne, et exiger quoi que ce soit ne ferait que retarder le rappel.
     *
     * Les données d'entrée sont un **point de départ, pas une vérité** : [RappelWorker]
     * relit la base avant de notifier (règle 9). Le type de rappel n'y sert qu'au journal.
     */
    private fun enfiler(travail: TravailRappel) {
        val requete = OneTimeWorkRequestBuilder<RappelWorker>()
            .setInitialDelay(travail.delai)
            .setInputData(
                workDataOf(
                    CLE_ENFANT_ID to travail.enfantId,
                    CLE_VACCIN_ID to travail.vaccinId,
                    CLE_TYPE_RAPPEL to travail.type.name,
                ),
            )
            .addTag(travail.etiquetteEnfant)
            .addTag(ETIQUETTE_TRAVAIL_RAPPEL)
            .build()

        workManager.enqueueUniqueWork(travail.nomUnique, ExistingWorkPolicy.REPLACE, requete)

        // Le détail reste en `Log.d` : il porte un délai, donc de quoi retrouver une date
        // prévue, donc indirectement une date de naissance. Le niveau `info` se contente des
        // compteurs.
        Log.d(
            ETIQUETTE_LOG_RAPPEL,
            "Rappel enfilé travail=${travail.nomUnique} type=${travail.type} dans ${travail.delai.toMinutes()} min",
        )
    }
}
