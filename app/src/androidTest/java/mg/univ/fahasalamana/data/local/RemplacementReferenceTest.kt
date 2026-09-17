package mg.univ.fahasalamana.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B20, garantie n° 3 : **une mise à jour du contenu de référence ne touche jamais aux données
 * de santé.** C'est le test le plus important du lot.
 *
 * Le §B5.2 sépare deux familles de tables : le contenu publié (`vaccins_reference`, `regions`,
 * `districts`, `centres`), remplaçable en bloc, et les données saisies par l'utilisateur
 * (`enfants`, `vaccins_administres`), qui ne doivent jamais être touchées par un remplacement.
 * Il va plus loin : « une administration référençant un `vaccinId` disparu du calendrier est
 * conservée ».
 *
 * C'est exactement ce que protège la décision prise en B04 : la clé étrangère sur `vaccinId` a
 * été retirée, parce qu'avec elle une mise à jour retirant un vaccin déjà administré échouerait
 * au `COMMIT` et annulerait tout le remplacement — un calendrier bloqué par une dose
 * correctement saisie. Sans clé étrangère, la dose survit ; mais plus rien, dans le schéma, ne
 * garantit cette survie. Ce sont ces tests qui la tiennent : si quelqu'un remet la clé
 * étrangère, ajoute un `DELETE FROM vaccins_administres` dans `remplacerCalendrier` ou
 * « nettoie » les doses orphelines, la perte de donnée de santé se verra ici plutôt qu'en
 * production, où elle serait silencieuse et définitive.
 *
 * C'est la moitié « base » de la garantie. L'autre moitié, l'affichage de cette dose sous
 * « Doses hors calendrier », est tenue par `domain.dosesHorsCalendrier` et ses tests JVM.
 *
 * Chaque test vérifie d'abord que le remplacement a bien eu lieu : sans cela, un
 * `remplacerCalendrier` devenu inopérant ferait passer le test pour de mauvaises raisons.
 */
@RunWith(AndroidJUnit4::class)
class RemplacementReferenceTest {

    private lateinit var base: AppDatabase
    private lateinit var enfants: EnfantDao
    private lateinit var administres: VaccinAdministreDao
    private lateinit var vaccins: VaccinReferenceDao
    private lateinit var centres: CentreDao
    private lateinit var reference: ReferenceDao

    @Before
    fun ouvrirLaBase() {
        base = ouvrirBaseEnMemoire()
        enfants = base.enfantDao()
        administres = base.vaccinAdministreDao()
        vaccins = base.vaccinReferenceDao()
        centres = base.centreDao()
        reference = base.referenceDao()
    }

    @After
    fun fermerLaBase() {
        base.close()
    }

    @Test
    fun remplacerLeCalendrierSansLeVaccinDejaAdministre_conserveLaDose() = runBlocking {
        reference.remplacerCalendrier(CALENDRIER_V1)
        enfants.enregistrer(ENFANT_SOA)
        administres.upsert(DOSE_BCG_SOA)
        administres.upsert(DOSE_PENTA_2_SOA)
        val avant = enfants.lireToutAvecVaccins().photographie()

        // Une nouvelle version du calendrier arrive, et `penta2` n'y est plus. Le remplacement
        // doit aboutir : s'il levait une exception de contrainte, l'utilisateur resterait
        // bloqué sur l'ancienne version à cause d'une dose qu'il a correctement saisie.
        reference.remplacerCalendrier(CALENDRIER_V2)

        // 1. le calendrier a réellement changé
        assertEquals(
            "le nouveau calendrier est en place, dans son ordre de publication",
            listOf(VACCIN_BCG.id, VACCIN_PENTA_1.id, VACCIN_RR_1.id),
            vaccins.lireCalendrier().map { it.id },
        )
        assertNull(
            "le vaccin retiré n'est plus dans le calendrier",
            vaccins.lireVaccin(VACCIN_PENTA_2.id),
        )
        val penta1 = requireNotNull(vaccins.lireVaccin(VACCIN_PENTA_1.id)) {
            "les vaccins conservés d'une version à l'autre gardent leur identifiant"
        }
        assertEquals(
            "un remplacement écrase le contenu de référence, il ne le fusionne pas",
            21,
            penta1.toleranceJours,
        )

        // 2. la dose saisie a survécu au retrait de son vaccin, valeurs comprises
        assertEquals(
            "la dose reste en base bien que son vaccin ait disparu du calendrier (§B5.2, décision B04)",
            DOSE_PENTA_2_SOA,
            administres.lire(ENFANT_SOA.id, VACCIN_PENTA_2.id),
        )

        // 3. et rien d'autre n'a bougé côté données personnelles
        assertEquals(
            "`enfants` et `vaccins_administres` sont inchangées, ligne pour ligne",
            avant,
            enfants.lireToutAvecVaccins().photographie(),
        )
    }

    @Test
    fun remplacerLAnnuaire_neToucheNiAuxDonneesPersonnellesNiAuCalendrier() = runBlocking {
        reference.remplacerCalendrier(CALENDRIER_V1)
        reference.remplacerAnnuaire(
            ANNUAIRE_V1_REGIONS,
            ANNUAIRE_V1_DISTRICTS,
            ANNUAIRE_V1_CENTRES,
        )
        enfants.enregistrer(ENFANT_SOA)
        enfants.enregistrer(ENFANT_RANTO)
        administres.upsert(DOSE_BCG_SOA)
        administres.upsert(DOSE_BCG_RANTO)
        val avant = enfants.lireToutAvecVaccins().photographie()

        // Nouvel annuaire : la région Boeny disparaît, et le téléphone d'un centre conservé change.
        reference.remplacerAnnuaire(
            ANNUAIRE_V2_REGIONS,
            ANNUAIRE_V2_DISTRICTS,
            ANNUAIRE_V2_CENTRES,
        )

        // 1. l'annuaire a réellement changé, aux trois niveaux
        assertEquals(
            listOf(REGION_ANALAMANGA.id),
            centres.observerRegions().first().map { it.id },
        )
        assertEquals("un seul district subsiste", 1, base.compterLignes("districts"))
        assertEquals("et un seul centre", 1, base.compterLignes("centres"))
        val isotry = requireNotNull(centres.observerCentre(CENTRE_ISOTRY.id).first()) {
            "le centre conservé d'une version à l'autre garde son identifiant"
        }
        assertEquals(
            "le contenu du centre conservé est celui de la nouvelle version",
            "+261 34 07 122 11",
            isotry.telephone,
        )

        // 2. les données de santé n'ont pas bougé
        assertEquals(
            "`enfants` et `vaccins_administres` sont inchangées, ligne pour ligne",
            avant,
            enfants.lireToutAvecVaccins().photographie(),
        )
        val doseDeRanto = requireNotNull(administres.lire(ENFANT_RANTO.id, VACCIN_BCG.id))
        assertEquals(
            "le lieu d'une dose est du texte libre, pas un renvoi vers `centres` : il survit au retrait du centre",
            CENTRE_MAHAJANGA_CENTRE.nom,
            doseDeRanto.lieu,
        )

        // 3. et remplacer l'annuaire ne touche pas non plus au calendrier, l'autre famille de référence
        assertEquals(
            "le calendrier n'est pas concerné par une mise à jour de l'annuaire",
            CALENDRIER_V1.size,
            base.compterLignes("vaccins_reference"),
        )
    }
}
