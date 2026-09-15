package mg.univ.fahasalamana.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.LocalDate

/**
 * Balayage paramétré de la règle R2 sur une même dose : Penta 1, prévu le
 * 12/02/2026 pour un enfant né le 01/01/2026, avec 14 jours de tolérance
 * (fenêtre du 12/02 au 26/02 inclus).
 *
 * Chaque ligne fait avancer la date du jour d'un cran et vérifie la frontière
 * exacte entre les statuts — c'est là que se logent les erreurs de comparaison
 * (`<` au lieu de `<=`).
 */
@RunWith(Parameterized::class)
class StatutPenta1ParametreTest(
    private val cas: String,
    private val aujourdHui: LocalDate,
    private val attendu: StatutVaccin,
) {

    @Test
    fun statutDePenta1() {
        val lignes = CalculateurEcheancier().echeancier(
            enfant = enfantNeLe(LocalDate.of(2026, 1, 1)),
            calendrier = CalendrierDeTest.COMPLET,
            administres = emptyList(),
            aujourdHui = aujourdHui,
        )

        assertEquals(cas, attendu, lignes.ligne("penta1").statut)
    }

    companion object {
        private val PREVU_LE: LocalDate = LocalDate.of(2026, 2, 12)
        private val FIN_FENETRE: LocalDate = LocalDate.of(2026, 2, 26)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun donnees(): List<Array<Any>> = listOf(
            arrayOf(
                "le jour de la naissance : à venir dans 42 jours",
                LocalDate.of(2026, 1, 1),
                StatutVaccin.AVenir(PREVU_LE, 42L),
            ),
            arrayOf(
                "trois semaines avant : à venir dans 23 jours",
                LocalDate.of(2026, 1, 20),
                StatutVaccin.AVenir(PREVU_LE, 23L),
            ),
            arrayOf(
                "la veille : à venir dans 1 jour",
                LocalDate.of(2026, 2, 11),
                StatutVaccin.AVenir(PREVU_LE, 1L),
            ),
            arrayOf(
                "le jour prévu : à faire, la fenêtre s'ouvre",
                PREVU_LE,
                StatutVaccin.AFaire(PREVU_LE, FIN_FENETRE),
            ),
            arrayOf(
                "au milieu de la fenêtre : à faire",
                LocalDate.of(2026, 2, 19),
                StatutVaccin.AFaire(PREVU_LE, FIN_FENETRE),
            ),
            arrayOf(
                "le dernier jour de la fenêtre : encore à faire",
                FIN_FENETRE,
                StatutVaccin.AFaire(PREVU_LE, FIN_FENETRE),
            ),
            arrayOf(
                "le lendemain de la fenêtre : en retard de 15 jours",
                LocalDate.of(2026, 2, 27),
                StatutVaccin.EnRetard(PREVU_LE, 15L),
            ),
            arrayOf(
                "un mois après : en retard de 31 jours",
                LocalDate.of(2026, 3, 15),
                StatutVaccin.EnRetard(PREVU_LE, 31L),
            ),
            arrayOf(
                "un an après : en retard de 365 jours",
                LocalDate.of(2027, 2, 12),
                StatutVaccin.EnRetard(PREVU_LE, 365L),
            ),
        )
    }
}
