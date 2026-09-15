package mg.univ.fahasalamana.domain

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Un rappel à programmer pour une dose (règle R3). Objet purement descriptif :
 * c'est `PlanificateurRappels` (B11) qui le traduit en `OneTimeWorkRequest`.
 *
 * L'identifiant d'enfant n'apparaît pas ici parce que l'échéancier est déjà
 * calculé pour un enfant donné : le planificateur, qui le connaît, compose le
 * nom unique de travail (R4) :
 * - [TypeRappel.AVANT_ECHEANCE] : `"rappel-$enfantId-$vaccinId"` ;
 * - [TypeRappel.FENETRE_BIENTOT_FERMEE] : `"rappel-$enfantId-$vaccinId-fenetre"`,
 *   suffixé pour ne pas écraser le rappel principal avec la politique `REPLACE`.
 *
 * @param prevuLe date d'échéance annoncée dans la notification.
 * @param dateHeure instant d'émission souhaité, fuseau `Indian/Antananarivo`.
 */
data class Rappel(
    val vaccinId: String,
    val prevuLe: LocalDate,
    val dateHeure: LocalDateTime,
    val type: TypeRappel = TypeRappel.AVANT_ECHEANCE,
)

enum class TypeRappel {
    /** Trois jours avant la date prévue (R3, Must). */
    AVANT_ECHEANCE,

    /** Deux jours avant la fermeture de la fenêtre de tolérance (R3, Should). */
    FENETRE_BIENTOT_FERMEE,
}
