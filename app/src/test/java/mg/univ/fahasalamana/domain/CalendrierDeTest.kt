package mg.univ.fahasalamana.domain

import java.time.LocalDate

/**
 * Jeu de données des tests : recopie fidèle du calendrier de démonstration
 * embarqué (`app/src/main/assets/calendrier.json`, §B5.1).
 *
 * Volontairement figé ici plutôt que lu depuis les assets : les tests JVM doivent
 * rester indépendants d'Android, et une modification du fichier de référence doit
 * faire échouer un test plutôt que de changer silencieusement les attendus.
 */
object CalendrierDeTest {

    private fun vac(
        id: String,
        nom: String,
        dose: String,
        ordre: Int,
        ageJours: Int,
        dependDe: String?,
        toleranceJours: Int,
        description: String,
    ) = VaccinReference(
        id = id,
        nom = nom,
        dose = dose,
        ordre = ordre,
        ageJours = ageJours,
        dependDe = dependDe,
        toleranceJours = toleranceJours,
        description = description,
    )

    /** Les 16 doses du calendrier de démonstration, dans l'ordre. */
    val COMPLET: List<VaccinReference> = listOf(
        vac("bcg", "BCG", "dose unique", 1, 0, null, 30, "À la naissance"),
        vac("vpo0", "Polio oral", "dose 0", 2, 0, null, 14, "À la naissance"),
        vac("penta1", "Pentavalent", "1re dose", 3, 42, null, 14, "6 semaines"),
        vac("vpo1", "Polio oral", "1re dose", 4, 42, null, 14, "6 semaines"),
        vac("pcv1", "Pneumocoque", "1re dose", 5, 42, null, 14, "6 semaines"),
        vac("rota1", "Rotavirus", "1re dose", 6, 42, null, 14, "6 semaines"),
        vac("penta2", "Pentavalent", "2e dose", 7, 28, "penta1", 14, "4 semaines après la 1re dose"),
        vac("vpo2", "Polio oral", "2e dose", 8, 28, "vpo1", 14, "4 semaines après la 1re dose"),
        vac("pcv2", "Pneumocoque", "2e dose", 9, 28, "pcv1", 14, "4 semaines après la 1re dose"),
        vac("rota2", "Rotavirus", "2e dose", 10, 28, "rota1", 14, "4 semaines après la 1re dose"),
        vac("penta3", "Pentavalent", "3e dose", 11, 28, "penta2", 14, "4 semaines après la 2e dose"),
        vac("vpo3", "Polio oral", "3e dose", 12, 28, "vpo2", 14, "4 semaines après la 2e dose"),
        vac("pcv3", "Pneumocoque", "3e dose", 13, 28, "pcv2", 14, "4 semaines après la 2e dose"),
        vac("vpi", "Polio injectable", "dose unique", 14, 98, null, 14, "14 semaines"),
        vac("rr1", "Rougeole-Rubéole", "1re dose", 15, 270, null, 30, "9 mois"),
        vac("rr2", "Rougeole-Rubéole", "2e dose", 16, 450, null, 30, "15 mois"),
    )

    /** Le calendrier privé des doses [ids] : simule un vaccin retiré d'une nouvelle version. */
    fun sans(vararg ids: String): List<VaccinReference> = COMPLET.filterNot { it.id in ids }

    /** Deux doses qui se référencent l'une l'autre : donnée de référence invalide. */
    val CIRCULAIRE: List<VaccinReference> = listOf(
        vac("a", "Vaccin A", "dose unique", 1, 10, "b", 14, "dépend de B"),
        vac("b", "Vaccin B", "dose unique", 2, 10, "a", 14, "dépend de A"),
    )
}

const val ID_FALY: String = "enfant-faly"

fun enfantNeLe(naissance: LocalDate, id: String = ID_FALY): Enfant =
    Enfant(id = id, prenom = "Faly", dateNaissance = naissance, sexe = Sexe.GARCON)

fun administre(vaccinId: String, date: LocalDate, enfantId: String = ID_FALY): VaccinAdministre =
    VaccinAdministre(
        id = "adm-$enfantId-$vaccinId-$date",
        enfantId = enfantId,
        vaccinId = vaccinId,
        date = date,
        lieu = "CSB2 Ankirihiry",
    )

/** La ligne de [vaccinId] dans un échéancier. Échoue bruyamment si elle n'existe pas. */
fun List<LigneEcheancier>.ligne(vaccinId: String): LigneEcheancier =
    firstOrNull { it.vaccin.id == vaccinId }
        ?: error("Aucune ligne pour « $vaccinId » dans l'échéancier (${map { it.vaccin.id }})")
