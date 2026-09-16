package mg.univ.fahasalamana.platform

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.data.local.toDomain
import mg.univ.fahasalamana.data.repository.EnfantRepository
import mg.univ.fahasalamana.data.repository.ReferenceRepository
import mg.univ.fahasalamana.domain.CalculateurEcheancier
import mg.univ.fahasalamana.domain.LigneEcheancier
import mg.univ.fahasalamana.domain.StatutVaccin
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Le travail qui affiche un rappel (B11, CDC §B8, US-B5).
 *
 * Tout le sujet tient dans une phrase du §B8 : « relit la base ; si le vaccin est toujours
 * non fait, affiche la notification, sinon ne fait rien ». Un rappel a pu être programmé
 * trois mois plus tôt, et trois mois, c'est long : la dose a pu être saisie, l'enfant
 * supprimé, le calendrier remplacé. **Les données d'entrée du travail ne disent donc que
 * de quelle dose il s'agissait** — jamais si elle est encore à faire, ni pour quelle date,
 * ni sous quel libellé. Tout cela est relu ici, à l'instant d'afficher.
 *
 * Le worker est construit par Koin (`workerOf(::RappelWorker)`, §B6) : c'est ce qui lui
 * permet de recevoir les deux repositories, le calculateur et le gestionnaire de
 * notifications au lieu d'aller les chercher lui-même. Koin fournit [Context] et
 * [WorkerParameters], qui doivent rester les deux premiers paramètres du constructeur.
 */
class RappelWorker(
    context: Context,
    parametres: WorkerParameters,
    private val enfants: EnfantRepository,
    private val reference: ReferenceRepository,
    private val calc: CalculateurEcheancier,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, parametres) {

    /**
     * Relit la base, décide, et affiche au plus une notification.
     *
     * Politique de résultat, qui n'est pas anodine pour un travail réveillé longtemps après
     * sa programmation :
     * - `failure()` pour des données d'entrée inutilisables : réessayer n'y changera rien.
     * - `success()` pour tous les cas « il n'y a rien à afficher » — dose faite, enfant
     *   supprimé, dose disparue du calendrier, notifications coupées par l'utilisateur. Ce
     *   ne sont pas des erreurs : c'est le travail qui fait exactement ce qu'on attend de
     *   lui, c'est-à-dire se taire.
     * - `retry()` pour un échec technique de lecture. WorkManager réessaiera avec son
     *   recul exponentiel par défaut, ce qui est le bon comportement pour une base
     *   momentanément indisponible.
     *
     * `CancellationException` est relancée telle quelle : c'est WorkManager qui arrête le
     * travail, l'attraper le transformerait en échec technique.
     */
    override suspend fun doWork(): Result {
        val enfantId = inputData.getString(CLE_ENFANT_ID)
        val vaccinId = inputData.getString(CLE_VACCIN_ID)
        if (enfantId.isNullOrBlank() || vaccinId.isNullOrBlank()) {
            Log.w(ETIQUETTE_LOG_RAPPEL, "Exécution abandonnée : données d'entrée sans identifiants")
            return Result.failure()
        }

        // Fragment d'identifiant, dose et type de rappel : de quoi relier cette exécution à
        // la ligne « Rappel enfilé » du même logcat, sans écrire ni prénom ni date (§B0).
        // Le type ne sert qu'ici : la décision d'afficher, elle, vient de la base relue.
        val trace = "${abregerIdentifiant(enfantId)}/$vaccinId" +
            "/${inputData.getString(CLE_TYPE_RAPPEL) ?: "?"}"
        return try {
            executer(enfantId, vaccinId, trace)
        } catch (annulation: CancellationException) {
            throw annulation
        } catch (erreur: Exception) {
            Log.w(ETIQUETTE_LOG_RAPPEL, "Exécution $trace : lecture impossible, nouvel essai demandé", erreur)
            Result.retry()
        }
    }

    private suspend fun executer(enfantId: String, vaccinId: String, trace: String): Result {
        // 1. L'enfant existe-t-il encore ? La suppression annule les travaux (B12), mais un
        //    travail déjà en cours d'exécution au moment de la suppression arrive ici.
        val carnet = enfants.observer(enfantId).first()
        if (carnet == null) {
            Log.i(ETIQUETTE_LOG_RAPPEL, "Exécution $trace : enfant absent du carnet, rien affiché")
            return Result.success()
        }

        // 2. La dose figure-t-elle encore au calendrier ? Une mise à jour (B19) a pu la
        //    retirer ; on ne sait alors plus ni quand elle était prévue, ni comment la nommer.
        val calendrier = reference.observerCalendrier().first()
        val enfant = carnet.enfant.toDomain()
        val aujourdHui = LocalDate.now(ZONE_MADAGASCAR)
        val echeancier = calc.echeancier(enfant, calendrier, carnet.administres.toDomain(), aujourdHui)
        val ligne = echeancier.firstOrNull { it.vaccin.id == vaccinId }
        if (ligne == null) {
            Log.i(ETIQUETTE_LOG_RAPPEL, "Exécution $trace : dose absente du calendrier, rien affiché")
            return Result.success()
        }

        // 3. Le statut recalculé ce matin, pas celui d'il y a trois mois.
        val prevuLe = ligne.prevuLe
        if (!aAfficher(ligne) || prevuLe == null) {
            Log.i(
                ETIQUETTE_LOG_RAPPEL,
                "Exécution $trace : statut ${nomStatut(ligne.statut)}, rien affiché",
            )
            return Result.success()
        }

        val contenu = ContenuRappel(
            enfantId = enfantId,
            vaccinId = vaccinId,
            prenomEnfant = enfant.prenom,
            libelleVaccin = applicationContext.getString(
                R.string.notif_rappel_vaccin,
                ligne.vaccin.nom,
                ligne.vaccin.dose,
            ),
            prevuLe = prevuLe,
            // Recalculé ici et non à la programmation : WorkManager a le droit de réveiller
            // le worker avec plusieurs heures de retard (§B8), et « dans 3 jours » le jour
            // même de l'échéance serait faux. C'est la consigne de `ContenuRappel`.
            joursRestants = ChronoUnit.DAYS.between(aujourdHui, prevuLe),
        )

        val affichee = notifications.afficherRappel(contenu)
        Log.i(
            ETIQUETTE_LOG_RAPPEL,
            if (affichee) {
                "Exécution $trace : rappel affiché"
            } else {
                "Exécution $trace : notifications non autorisées, rien affiché"
            },
        )
        return Result.success()
    }

    /**
     * Faut-il encore afficher ce rappel, au vu du statut relu ?
     *
     * `when` exhaustif sans `else` (règle 5 de CLAUDE.md) : un sixième statut devrait
     * obliger à se poser la question ici plutôt que de tomber dans une branche par défaut.
     *
     * - [StatutVaccin.Fait] — **le cas qui justifie toute la relecture**. La dose a été
     *   saisie entre la programmation et le réveil : scénario « vaccin saisi avant le
     *   rappel » de US-B5.
     * - [StatutVaccin.AVenir] — cas nominal : le rappel de R3, trois jours avant.
     * - [StatutVaccin.AFaire] — le réveil a traîné, ou c'est le rappel « fenêtre bientôt
     *   fermée ». La dose est due, le rappel a tout son sens.
     * - [StatutVaccin.EnRetard] — réveil très tardif, ou téléphone resté éteint. On affiche
     *   quand même : `NotificationHelper` a une phrase factuelle et sans reproche pour ce
     *   cas (R7), et se taire priverait le parent de la seule information utile.
     * - [StatutVaccin.EnAttente] — la dose précédente n'est pas faite, donc la date prévue
     *   a bougé (R1) : annoncer l'ancienne serait faux. La replanification déclenchée par
     *   l'écriture qui a causé ce décalage a déjà enfilé le bon rappel.
     */
    private fun aAfficher(ligne: LigneEcheancier): Boolean = when (ligne.statut) {
        is StatutVaccin.Fait -> false
        is StatutVaccin.EnAttente -> false
        is StatutVaccin.AVenir -> true
        is StatutVaccin.AFaire -> true
        is StatutVaccin.EnRetard -> true
    }

    /**
     * Nom du statut pour le journal, sans son contenu.
     *
     * `toString()` d'une `data class` ferait entrer une date prévue — donc une date de
     * naissance — dans logcat. Le journal dit ce qui s'est passé, pas ce que contient le
     * carnet (§B0).
     */
    private fun nomStatut(statut: StatutVaccin): String = when (statut) {
        is StatutVaccin.Fait -> "fait"
        is StatutVaccin.AVenir -> "à venir"
        is StatutVaccin.AFaire -> "à faire"
        is StatutVaccin.EnRetard -> "en retard"
        is StatutVaccin.EnAttente -> "en attente"
    }
}
