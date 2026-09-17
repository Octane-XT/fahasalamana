package mg.univ.fahasalamana.ui.fiche

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import mg.univ.fahasalamana.RegleDispatcherPrincipal
import mg.univ.fahasalamana.data.local.EnfantAvecVaccins
import mg.univ.fahasalamana.data.local.toEntity
import mg.univ.fahasalamana.data.repository.EnfantRepository
import mg.univ.fahasalamana.data.repository.InfosSource
import mg.univ.fahasalamana.data.repository.ReferenceRepository
import mg.univ.fahasalamana.data.repository.ResultatSync
import mg.univ.fahasalamana.domain.CalculateurEcheancier
import mg.univ.fahasalamana.domain.CalendrierDeTest
import mg.univ.fahasalamana.domain.CarnetExport
import mg.univ.fahasalamana.domain.Enfant
import mg.univ.fahasalamana.domain.GroupeEcheancier
import mg.univ.fahasalamana.domain.ID_FALY
import mg.univ.fahasalamana.domain.LigneEcheancier
import mg.univ.fahasalamana.domain.ResultatImport
import mg.univ.fahasalamana.domain.StatutVaccin
import mg.univ.fahasalamana.domain.VaccinAdministre
import mg.univ.fahasalamana.domain.VaccinReference
import mg.univ.fahasalamana.domain.administre
import mg.univ.fahasalamana.domain.enfantNeLe
import mg.univ.fahasalamana.domain.ligne
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Combinaison des flux de la fiche enfant (CDC §B9, ligne « `FicheEnfantViewModel` :
 * combinaison des flux, mise à jour instantanée après saisie »).
 *
 * **Ce qui est vérifié ici, et nulle part ailleurs** : l'assemblage. Le calcul des statuts
 * appartient au `CalculateurEcheancier` et il a déjà ses propres tests ; on ne le rejoue
 * pas. Ce que ces tests gardent, c'est le câblage :
 *
 *  1. la séquence `Chargement` → `Pret`, avec l'échéancier complet et ses tranches d'âge ;
 *  2. **la recomposition instantanée** — une écriture dans le repository suffit à réémettre
 *     l'état, sans aucune méthode de rafraîchissement. C'est le dernier scénario Gherkin de
 *     l'US-B2 (« la pastille du vaccin est déjà verte sans rafraîchissement manuel »), le
 *     seul que la revue de conformité classait « tenu, mais non prouvé », et c'est ce qui
 *     justifie d'avoir bâti toute la lecture sur des `Flow` plutôt que sur des `suspend fun` ;
 *  3. le changement de jour : l'horloge injectée émet, les statuts se recalculent, les
 *     sections ne bougent pas ;
 *  4. l'enfant supprimé alors que la fiche est ouverte → `Introuvable` ;
 *  5. le bandeau de source, qui n'émet rien tant que le contenu de référence n'est pas en
 *     base : l'état doit être produit quand même. C'est la parade `onStart { emit(null) }`
 *     du ViewModel, et sans ce test elle peut disparaître lors d'un nettoyage sans que rien
 *     ne le signale — la fiche resterait bloquée sur « Chargement… » à la première
 *     installation, exactement là où personne ne regarde.
 *
 * **Aucune date ne vient de `LocalDate.now()`** : l'horloge est un paramètre du ViewModel,
 * et c'est un `MutableStateFlow` que ces tests pilotent. Un test qui lirait l'heure système
 * changerait de résultat le 12 février.
 *
 * Les deux faux repositories sont **pilotables** : un `MutableStateFlow` par source, qu'on
 * pousse en cours de test pour observer la réémission. Ils simulent Room, ils ne le
 * contournent pas : `enregistrerAdministration()` écrit *et* fait réémettre le flux de
 * lecture, comme le ferait une requête `@Query` observée.
 */
class FicheEnfantViewModelTest {

    /** `viewModelScope` exige un `Dispatchers.Main` : sans cette règle, rien ne démarre. */
    @get:Rule
    val dispatcherPrincipal = RegleDispatcherPrincipal()

    private val enfants = FauxEnfantRepository()
    private val reference = FauxReferenceRepository()
    private val horloge = MutableStateFlow(LE_15_FEVRIER)

    /** Faly en base, sans aucune dose saisie : le point de départ de tous les scénarios. */
    @Before
    fun poserLeCarnetEnBase() {
        enfants.flux.value = EnfantAvecVaccins(
            // `creeLe` figé : c'est un instant technique, il ne doit pas venir de l'horloge
            // système, sans quoi deux exécutions du même test comparent des états différents.
            enfant = enfantNeLe(NAISSANCE).toEntity(creeLe = 0L),
            administres = emptyList(),
        )
    }

    private fun viewModel() = FicheEnfantViewModel(
        savedStateHandle = SavedStateHandle(mapOf("enfantId" to ID_FALY)),
        enfants = enfants,
        reference = reference,
        calc = CalculateurEcheancier(),
        horlogeJour = horloge,
    )

    // -----------------------------------------------------------------------
    // 0. L'argument de navigation
    // -----------------------------------------------------------------------

    /**
     * Isolé exprès : `SavedStateHandle.toRoute<FicheEnfant>()` est la seule ligne de ce
     * ViewModel qui dépende de la bibliothèque de navigation. Si la lecture de route typée
     * ne fonctionne pas hors Android, c'est **ce** test qui le dira, au lieu de laisser les
     * six autres échouer sur une pile d'appels illisible.
     */
    @Test
    fun lArgumentDeNavigationEstLuParLaRouteTypee() {
        viewModel()

        assertEquals(
            "Le ViewModel doit observer l'enfant désigné par la route, pas un autre.",
            ID_FALY,
            enfants.idDemande,
        )
    }

    // -----------------------------------------------------------------------
    // 1. Séquence d'états
    // -----------------------------------------------------------------------

    @Test
    fun chargementPuisEcheancierComplet() = runTest {
        viewModel().uiState.test {
            assertEquals(FicheEnfantUiState.Chargement, awaitItem())

            val etat = pret(awaitItem())
            assertEquals("Faly", etat.enfant.prenom)
            assertEquals(NAISSANCE, etat.enfant.dateNaissance)
            // La date des statuts vient de l'horloge injectée, jamais de l'instant du test.
            assertEquals(LE_15_FEVRIER, etat.aujourdHui)
            assertEquals(CalendrierDeTest.COMPLET.size, etat.nbLignes())
            // Les tranches d'âge du wireframe §B7.2, dans l'ordre.
            assertEquals(
                listOf<Int?>(0, 42, 70, 98, 270, 450),
                etat.groupes.map(GroupeEcheancier::ageJours),
            )
            assertEquals(0, etat.nbFaits)
            assertEquals(emptyList<VaccinAdministre>(), etat.dosesHorsCalendrier)
            assertTrue(
                "Au 15/02, Penta 1 (prévu le 12/02, tolérance 14 j) est dans sa fenêtre.",
                etat.ligneDe("penta1").statut is StatutVaccin.AFaire,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // -----------------------------------------------------------------------
    // 2. Recomposition instantanée — le cœur du sujet (US-B2, dernier scénario)
    // -----------------------------------------------------------------------

    @Test
    fun uneSaisieEnBaseRendLaPastilleVerteSansAucunRafraichissement() = runTest {
        viewModel().uiState.test {
            assertEquals(FicheEnfantUiState.Chargement, awaitItem())

            val avant = pret(awaitItem())
            assertTrue(avant.ligneDe("penta1").statut is StatutVaccin.AFaire)
            assertEquals(0, avant.nbFaits)

            // Tout ce que fait l'écran SaisieVaccin en validant : une écriture. Rien n'est
            // appelé sur le ViewModel de la fiche — il n'expose d'ailleurs que `uiState`.
            enfants.enregistrerAdministration(administre("penta1", LE_13_FEVRIER))

            val apres = pret(awaitItem())
            assertEquals(StatutVaccin.Fait(LE_13_FEVRIER), apres.ligneDe("penta1").statut)
            assertEquals(1, apres.nbFaits)
            assertEquals(avant.resume.nbAFaire - 1, apres.resume.nbAFaire)
            // La règle R1 a retraversé toute la chaîne : Penta 2 se recale sur la date
            // *réelle* de Penta 1, et non sur sa date théorique.
            assertEquals(LE_13_FEVRIER.plusDays(28), apres.ligneDe("penta2").prevuLe)

            // Preuve qu'aucune relecture n'a eu lieu : le ViewModel s'est abonné une fois,
            // à la construction, et c'est le `Flow` qui a réémis. Si quelqu'un ajoutait un
            // `refresh()` qui rappelle le repository, ce compteur le trahirait.
            assertEquals(1, enfants.nbAppelsObserver)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // -----------------------------------------------------------------------
    // 3. Changement de jour
    // -----------------------------------------------------------------------

    @Test
    fun leChangementDeJourRecalculeLesStatutsSansDeplacerLesSections() = runTest {
        horloge.value = LE_20_JANVIER

        viewModel().uiState.test {
            assertEquals(FicheEnfantUiState.Chargement, awaitItem())

            val veille = pret(awaitItem())
            assertEquals(StatutVaccin.AVenir(LE_12_FEVRIER, 23L), veille.ligneDe("penta1").statut)

            // Minuit, ou retour au premier plan : `horlogeJour` émet une nouvelle date.
            horloge.value = LE_15_FEVRIER

            val apres = pret(awaitItem())
            assertEquals(LE_15_FEVRIER, apres.aujourdHui)
            assertEquals(
                StatutVaccin.AFaire(LE_12_FEVRIER, LE_26_FEVRIER),
                apres.ligneDe("penta1").statut,
            )
            // Rien d'autre ne bouge : même enfant, mêmes sections, mêmes doses reçues.
            assertEquals(veille.enfant, apres.enfant)
            assertEquals(
                veille.groupes.map(GroupeEcheancier::ageJours),
                apres.groupes.map(GroupeEcheancier::ageJours),
            )
            assertEquals(veille.nbFaits, apres.nbFaits)

            // `stateIn` dédoublonne les états égaux : un état supplémentaire serait un état
            // réellement différent, donc un recalcul que personne n'a demandé.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -----------------------------------------------------------------------
    // 4. Enfant supprimé pendant que la fiche est ouverte
    // -----------------------------------------------------------------------

    @Test
    fun lEnfantSupprimePendantQueLaFicheEstOuverteDevientIntrouvable() = runTest {
        viewModel().uiState.test {
            assertEquals(FicheEnfantUiState.Chargement, awaitItem())
            pret(awaitItem())

            // Suppression depuis « Mes enfants », la fiche étant restée ouverte derrière.
            enfants.supprimer(ID_FALY)

            assertEquals(FicheEnfantUiState.Introuvable, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -----------------------------------------------------------------------
    // 5. Le bandeau de source ne retient pas l'échéancier en otage
    // -----------------------------------------------------------------------

    @Test
    fun lEcheancierSAfficheAvantLeBandeauDeSourcePuisLeBandeauArrive() = runTest {
        // `observerInfosSource()` n'a encore rien émis : c'est l'état d'une première
        // installation, tant que `chargerEmbarqueSiVide()` n'a pas fini.
        viewModel().uiState.test {
            assertEquals(FicheEnfantUiState.Chargement, awaitItem())

            val sansBandeau = pret(awaitItem())
            assertNull(
                "Le bandeau de source est un complément : son absence ne bloque pas la fiche.",
                sansBandeau.infosSource,
            )
            assertEquals(CalendrierDeTest.COMPLET.size, sansBandeau.nbLignes())

            reference.fluxInfos.value = INFOS

            val avecBandeau = pret(awaitItem())
            assertEquals(INFOS, avecBandeau.infosSource)
            // L'arrivée du bandeau ne recalcule pas l'échéancier, elle le complète.
            assertEquals(sansBandeau.groupes, avecBandeau.groupes)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // -----------------------------------------------------------------------
    // 6. Une lecture qui échoue ne fait pas tomber l'écran
    // -----------------------------------------------------------------------

    @Test
    fun uneLectureQuiEchoueDonneLEtatErreur() = runTest {
        reference.erreurCalendrier = IllegalStateException("base illisible")

        viewModel().uiState.test {
            assertEquals(FicheEnfantUiState.Chargement, awaitItem())
            assertEquals(FicheEnfantUiState.Erreur, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// ---------------------------------------------------------------------------
// Dates et données du scénario — figées, jamais lues à l'horloge système
// ---------------------------------------------------------------------------

/** Faly, né le 01/01/2026 : l'enfant des scénarios Gherkin de l'US-B2. */
private val NAISSANCE: LocalDate = LocalDate.of(2026, 1, 1)

/** Penta 1 « À venir », dans 23 jours (scénario « statut À venir »). */
private val LE_20_JANVIER: LocalDate = LocalDate.of(2026, 1, 20)

/** Date prévue de Penta 1 : 01/01 + 42 jours. */
private val LE_12_FEVRIER: LocalDate = LocalDate.of(2026, 2, 12)

/** Jour de la saisie (scénario « statut Fait »). */
private val LE_13_FEVRIER: LocalDate = LocalDate.of(2026, 2, 13)

/** Penta 1 « À faire », fenêtre ouverte (scénario « statut À faire »). */
private val LE_15_FEVRIER: LocalDate = LocalDate.of(2026, 2, 15)

/** Fin de la fenêtre de Penta 1 : 12/02 + 14 jours de tolérance. */
private val LE_26_FEVRIER: LocalDate = LocalDate.of(2026, 2, 26)

private val INFOS = InfosSource(
    source = "Calendrier de démonstration — à valider auprès du Ministère de la Santé Publique",
    publieLe = LocalDate.of(2026, 1, 15),
    version = 3,
)

// ---------------------------------------------------------------------------
// Petits outils de lecture d'état
// ---------------------------------------------------------------------------

/** Échoue en nommant l'état obtenu, plutôt que sur un `ClassCastException` muet. */
private fun pret(etat: FicheEnfantUiState): FicheEnfantUiState.Pret {
    assertTrue("État attendu : Pret, obtenu : $etat", etat is FicheEnfantUiState.Pret)
    return etat as FicheEnfantUiState.Pret
}

private fun FicheEnfantUiState.Pret.nbLignes(): Int = groupes.sumOf { it.lignes.size }

/** La ligne d'un vaccin, toutes sections confondues. Réutilise `ligne()` de `CalendrierDeTest`. */
private fun FicheEnfantUiState.Pret.ligneDe(vaccinId: String): LigneEcheancier =
    groupes.flatMap(GroupeEcheancier::lignes).ligne(vaccinId)

/** Une écriture atteinte depuis la fiche serait une régression d'architecture, pas un cas de test. */
private fun horsPerimetre(operation: String): Nothing = error(
    "FicheEnfantViewModel ne doit rien écrire : appel inattendu à « $operation ». " +
        "La fiche est un écran de lecture ; les écritures passent par SaisieVaccin et EditionEnfant.",
)

// ---------------------------------------------------------------------------
// Faux repositories pilotables
// ---------------------------------------------------------------------------

/**
 * Se comporte comme Room : une écriture met à jour la table **et** le flux de lecture
 * réémet. C'est ce couplage-là que le scénario « recomposition instantanée » teste ; un
 * faux qui se contenterait de stocker sans réémettre rendrait le test vert pour rien.
 */
private class FauxEnfantRepository : EnfantRepository {

    val flux = MutableStateFlow<EnfantAvecVaccins?>(null)

    /** Identifiant réellement demandé par le ViewModel, c'est-à-dire lu dans la route. */
    var idDemande: String? = null
        private set

    /** Doit rester à 1 : le ViewModel s'abonne une fois et ne relit jamais. */
    var nbAppelsObserver: Int = 0
        private set

    override fun observer(id: String): Flow<EnfantAvecVaccins?> {
        idDemande = id
        nbAppelsObserver++
        return flux.map { enBase -> enBase?.takeIf { it.enfant.id == id } }
    }

    override fun observerTous(): Flow<List<EnfantAvecVaccins>> = flux.map { listOfNotNull(it) }

    override suspend fun enregistrerAdministration(v: VaccinAdministre) {
        val courant = flux.value ?: return
        // R5 : une seule dose par couple (enfant, vaccin) — la nouvelle remplace l'ancienne.
        val autres = courant.administres
            .filterNot { it.enfantId == v.enfantId && it.vaccinId == v.vaccinId }
        flux.value = courant.copy(administres = autres + v.toEntity())
    }

    override suspend fun supprimerAdministration(id: String) {
        val courant = flux.value ?: return
        flux.value = courant.copy(administres = courant.administres.filterNot { it.id == id })
    }

    /** Cascade comprise : l'enfant et ses doses disparaissent ensemble. */
    override suspend fun supprimer(id: String) {
        if (flux.value?.enfant?.id == id) flux.value = null
    }

    override suspend fun enregistrer(enfant: Enfant) {
        horsPerimetre("enregistrer")
    }

    override suspend fun exporter(jour: LocalDate): CarnetExport = horsPerimetre("exporter")

    override suspend fun importer(carnet: CarnetExport): ResultatImport = horsPerimetre("importer")
}

private class FauxReferenceRepository : ReferenceRepository {

    val fluxCalendrier = MutableStateFlow(CalendrierDeTest.COMPLET)

    /** `null` tant que le contenu embarqué n'est pas chargé : rien n'est alors émis. */
    val fluxInfos = MutableStateFlow<InfosSource?>(null)

    /** Posée **avant** la construction du ViewModel pour simuler une base illisible. */
    var erreurCalendrier: Throwable? = null

    override fun observerCalendrier(): Flow<List<VaccinReference>> {
        val panne = erreurCalendrier
        // Type explicite : sans lui, `flow { throw … }` s'infère en `Flow<Nothing>`.
        return if (panne == null) fluxCalendrier else flow<List<VaccinReference>> { throw panne }
    }

    /**
     * Fidèle à l'implémentation réelle, qui filtre les valeurs incomplètes : tant que le
     * contenu de référence n'est pas en base, ce flux **n'émet rien du tout**. C'est la
     * source qui bloquerait le `combine` à quatre entrées sans la parade du ViewModel.
     */
    override fun observerInfosSource(): Flow<InfosSource> = fluxInfos.filterNotNull()

    override suspend fun chargerEmbarqueSiVide() {
        horsPerimetre("chargerEmbarqueSiVide")
    }

    override suspend fun mettreAJour(): ResultatSync = horsPerimetre("mettreAJour")
}
