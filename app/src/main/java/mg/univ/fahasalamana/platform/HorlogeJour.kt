package mg.univ.fahasalamana.platform

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Fuseau de Madagascar : UTC+3, sans heure d'été (CDC §0.2). */
val ZONE_MADAGASCAR: ZoneId = ZoneId.of("Indian/Antananarivo")

/**
 * Émet la date du jour, puis une nouvelle valeur à chaque changement de jour.
 *
 * Le statut d'un vaccin dépend de la date du jour : sans cette horloge, une fiche ouverte
 * la veille afficherait encore « à venir » un vaccin devenu « à faire » ce matin, et il
 * faudrait tuer l'application pour voir le bon état.
 *
 * Deux déclencheurs :
 * - **minuit**, pour un téléphone resté allumé sur l'écran ;
 * - **le retour au premier plan**, parce qu'une application en arrière-plan ne consomme
 *   plus son flux et raterait le passage de minuit.
 *
 * `distinctUntilChanged` évite de recalculer les échéanciers à chaque retour au premier
 * plan dans la même journée.
 */
fun horlogeJour(
    zone: ZoneId = ZONE_MADAGASCAR,
    lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle,
): Flow<LocalDate> = merge(
    flow {
        while (currentCoroutineContext().isActive) {
            emit(Unit)
            delay(dureeJusquAuProchainMinuit(zone).toMillis())
        }
    },
    lifecycle.currentStateFlow
        .filter { it.isAtLeast(Lifecycle.State.STARTED) }
        .map { },
).map { LocalDate.now(zone) }
    .distinctUntilChanged()

/**
 * Temps restant avant le prochain minuit local, plus une seconde de marge pour que le
 * réveil tombe après le changement de date et non sur la frontière exacte.
 */
private fun dureeJusquAuProchainMinuit(zone: ZoneId): Duration {
    val maintenant = java.time.ZonedDateTime.now(zone)
    val prochainMinuit = maintenant.toLocalDate().plusDays(1).atStartOfDay(zone)
    return Duration.between(maintenant, prochainMinuit).plusSeconds(1)
}

/** Heure d'envoi des rappels (règle R3) : 9 h, heure locale. */
val HEURE_RAPPEL: LocalTime = LocalTime.of(9, 0)
