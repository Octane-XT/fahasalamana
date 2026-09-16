package mg.univ.fahasalamana.platform

import mg.univ.fahasalamana.domain.Rappel
import mg.univ.fahasalamana.domain.TypeRappel
import java.time.Duration
import java.time.LocalDateTime

/*
 * Traduction d'un `Rappel` (règle R3) en paramètres de travail WorkManager (B11).
 *
 * Ce fichier ne contient **aucune API Android**, exactement pour la même raison que
 * `LienNotification.kt` : c'est ici que vivent les deux seules décisions de B11 qui sont
 * du calcul et non de la plomberie — le nom unique du travail (R4) et son délai initial.
 * Les deux se testent en JVM, sans émulateur et sans `WorkManagerTestInitHelper`
 * (`src/test/java/.../platform/TraductionRappelsTest.kt`).
 *
 * `PlanificateurRappels` n'a plus, en face, qu'à construire un `OneTimeWorkRequest` par
 * [TravailRappel] : il ne recalcule rien, ne rejoue aucune règle métier, et ne connaît de
 * R3 que ce que `CalculateurEcheancier.rappelsAProgrammer()` lui a déjà donné.
 */

/**
 * Suffixe du second rappel « fenêtre bientôt fermée » (R3, Should).
 *
 * Sans lui, les deux rappels d'une même dose porteraient le même `uniqueWorkName` et la
 * politique `REPLACE` de R4 ferait disparaître le rappel principal au profit du second.
 * La KDoc de `domain/Rappel.kt` fixe cette convention ; elle est appliquée ici, une fois.
 */
const val SUFFIXE_RAPPEL_FENETRE: String = "-fenetre"

/**
 * Nom unique du travail d'un rappel, règle R4.
 *
 * - [TypeRappel.AVANT_ECHEANCE] : `rappel-<enfantId>-<vaccinId>`, la chaîne de
 *   [etiquetteRappel] telle quelle — c'est elle qui sert aussi d'étiquette à la
 *   notification, ce qui permet d'annuler le travail et la notification avec la même clé.
 * - [TypeRappel.FENETRE_BIENTOT_FERMEE] : la même, suffixée de [SUFFIXE_RAPPEL_FENETRE].
 *
 * `when` sans `else` : ajouter un type de rappel doit casser la compilation ici plutôt que
 * de produire en silence un nom qui écrase un travail existant.
 */
fun nomTravailRappel(enfantId: String, vaccinId: String, type: TypeRappel): String = when (type) {
    TypeRappel.AVANT_ECHEANCE -> etiquetteRappel(enfantId, vaccinId)
    TypeRappel.FENETRE_BIENTOT_FERMEE -> etiquetteRappel(enfantId, vaccinId) + SUFFIXE_RAPPEL_FENETRE
}

/**
 * Étiquette (`tag`) commune à tous les travaux de rappel d'un enfant.
 *
 * Elle répond à un besoin que le nom unique ne couvre pas : **annuler les rappels d'un
 * enfant qui n'existe plus**. Après une suppression, on ne peut plus lire son échéancier,
 * donc plus énumérer ses `uniqueWorkName` ; `cancelAllWorkByTag` sur cette étiquette-là,
 * si.
 *
 * Préfixe distinct de celui de [etiquetteRappel] (`rappels-` au pluriel contre `rappel-`)
 * pour qu'un nom unique ne puisse jamais être pris pour une étiquette de groupe, ni
 * l'inverse.
 */
fun etiquetteTravauxEnfant(enfantId: String): String = "rappels-enfant-$enfantId"

/** Étiquette portée par **tous** les rappels, tous enfants confondus : utile au débogage. */
const val ETIQUETTE_TRAVAIL_RAPPEL: String = "rappel-vaccination"

/**
 * Un `Rappel` traduit en paramètres de travail, avant toute API Android.
 *
 * Volontairement plat et sans `Data` ni `WorkRequest` : c'est ce qui rend la traduction
 * testable en JVM. `PlanificateurRappels` construit le `OneTimeWorkRequest` à partir de là.
 *
 * @param delai attente avant exécution (`setInitialDelay`), jamais négative.
 */
data class TravailRappel(
    val nomUnique: String,
    val etiquetteEnfant: String,
    val enfantId: String,
    val vaccinId: String,
    val type: TypeRappel,
    val delai: Duration,
)

/**
 * Traduit la liste produite par `CalculateurEcheancier.rappelsAProgrammer()` (R3).
 *
 * Fonction totale et sans effet de bord : même entrée, même sortie. C'est elle qui rend
 * « tout replanifier » idempotent au sens de R4 — deux appels successifs produisent
 * exactement les mêmes noms uniques, donc `REPLACE` remplace au lieu d'accumuler.
 *
 * @param maintenant instant de référence, passé et jamais lu d'une horloge interne : c'est
 *   le même que celui donné à `rappelsAProgrammer`, sans quoi un rappel calculé comme
 *   « encore devant nous » pourrait se traduire par un délai nul.
 * @param plafond plafond de démonstration, voir [delaiInitial]. `null` en production.
 */
fun travauxRappels(
    enfantId: String,
    rappels: List<Rappel>,
    maintenant: LocalDateTime,
    plafond: Duration? = null,
): List<TravailRappel> = rappels.map { rappel ->
    TravailRappel(
        nomUnique = nomTravailRappel(enfantId, rappel.vaccinId, rappel.type),
        etiquetteEnfant = etiquetteTravauxEnfant(enfantId),
        enfantId = enfantId,
        vaccinId = rappel.vaccinId,
        type = rappel.type,
        delai = delaiInitial(maintenant, rappel.dateHeure, plafond),
    )
}

/**
 * Délai initial d'un travail : `dateHeure - maintenant` (§B8), borné des deux côtés.
 *
 * **Borne basse, zéro.** `rappelsAProgrammer` ne produit que des émissions futures, mais
 * l'instant de référence peut avoir glissé entre les deux appels, et `setInitialDelay`
 * refuse une valeur négative. Un délai nul signifie « dès que possible », ce qui est le
 * comportement correct pour un rappel dont l'heure vient de passer.
 *
 * **Borne haute, [plafond].** C'est le délai de démonstration de la DoD de B11 : en build
 * debug, `PLAFOND_DELAI_RAPPEL` vaut une minute, et un rappel prévu dans trois mois tombe
 * donc au bout d'une minute. En build release, le plafond est `null` et cette fonction
 * rend le délai réel — le raccourci n'existe pas dans le code publié, il n'est pas
 * seulement désactivé (voir `platform/DelaiDemonstration.kt`, source sets `debug` et
 * `release`).
 *
 * Le plafond raccourcit, il n'allonge jamais : un rappel déjà proche garde son délai.
 */
fun delaiInitial(maintenant: LocalDateTime, emission: LocalDateTime, plafond: Duration?): Duration {
    val attente = Duration.between(maintenant, emission).coerceAtLeast(Duration.ZERO)
    return if (plafond == null) attente else attente.coerceAtMost(plafond)
}

/**
 * Forme abrégée d'un identifiant pour le journal `FAHASALAMANA_RAPPEL` (§B0).
 *
 * Les huit premiers caractères d'un UUID suffisent à suivre une planification puis son
 * exécution dans un logcat, et ne reconstituent rien : le journal doit dire **ce qui s'est
 * passé**, jamais ce que contient le carnet. Aucun prénom, aucune date de naissance, et
 * aucune date prévue n'est écrite ailleurs — une date prévue, c'est la date de naissance
 * plus un délai du calendrier public, donc une donnée de santé en clair.
 */
fun abregerIdentifiant(id: String): String = id.take(8)
