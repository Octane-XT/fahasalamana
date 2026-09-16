package mg.univ.fahasalamana.platform

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import mg.univ.fahasalamana.data.local.AppDatabase
import mg.univ.fahasalamana.data.repository.EnfantRepository
import mg.univ.fahasalamana.data.repository.EnfantRepositoryImpl
import mg.univ.fahasalamana.data.repository.InfosSource
import mg.univ.fahasalamana.data.repository.ReferenceRepository
import mg.univ.fahasalamana.domain.CalculateurEcheancier
import mg.univ.fahasalamana.domain.Enfant
import mg.univ.fahasalamana.domain.Sexe
import mg.univ.fahasalamana.domain.TypeRappel
import mg.univ.fahasalamana.domain.VaccinAdministre
import mg.univ.fahasalamana.domain.VaccinReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Definition of Done de B12 : les rappels suivent les écritures du carnet.
 *
 * **Scénarios couverts** (§B3) : US-B5 scénario 2 « vaccin saisi avant le rappel », le
 * dernier « Et » de US-B1 « création valide » (« les rappels de ses échéances futures sont
 * programmés »), le troisième « Alors » de US-B1 « suppression », et l'idempotence de R4.
 * Le scénario 3 (redémarrage) n'est pas testable ici : il est garanti par la persistance de
 * WorkManager et se vérifie à la main (`adb reboot`, voir le rapport de tâche).
 *
 * **Ce qui est testé, et à quel niveau.** Les quatre déclencheurs branchés en B12
 * (`EditionEnfantViewModel` : enregistrement et suppression ; `SaisieVaccinViewModel` :
 * saisie et suppression d'une dose) tiennent chacun en un appel derrière une écriture du
 * repository. Ce sont ces couples « écriture puis appel » qui sont rejoués ici, sur le vrai
 * `EnfantRepositoryImpl` et une vraie base Room, et non les ViewModels eux-mêmes : les
 * instancier demanderait `Dispatchers.setMain` — `kotlinx-coroutines-test` n'est déclaré
 * qu'en `testImplementation` — et un `SavedStateHandle` portant une route typée. Le
 * branchement lui-même se relit à l'œil ; ce qui méritait des tests, c'est ce que
 * `PlanificateurRappels` laisse en file après chacun d'eux.
 *
 * **Base Room en mémoire**, comme `EnfantDaoTest` : chaque test repart d'un carnet vide et
 * rien n'est écrit sur l'appareil.
 *
 * **Calendrier figé dans ce fichier** plutôt que lu depuis `assets/` : les quatre doses
 * ci-dessous suffisent à produire les quatre statuts qui décident d'un rappel, et une
 * modification de `calendrier.json` ne doit pas changer les attendus en silence.
 *
 * **Piège du variant debug.** Ces tests s'exécutent sur le build debug, où
 * `PLAFOND_DELAI_RAPPEL` vaut une minute : tous les délais y seraient ramenés à 60 000 ms, et
 * les trois tests qui les vérifient liraient le plafond de démonstration au lieu de R3. Le
 * planificateur est donc construit avec `plafondDelai = null`, comme le demande sa KDoc —
 * c'est la seule façon de voir ici les délais réels de la version publiée.
 */
@RunWith(AndroidJUnit4::class)
class PlanificateurRappelsTest {

    private lateinit var contexte: Context
    private lateinit var base: AppDatabase
    private lateinit var enfants: EnfantRepository
    private lateinit var workManager: WorkManager
    private lateinit var planificateur: PlanificateurRappels

    /**
     * `SynchronousExecutor` : sans lui, `enqueueUniqueWork` rendrait la main avant que la
     * base de WorkManager ne soit écrite, et l'assertion qui suit lirait une file encore
     * vide. Chaque appel de `initializeTestWorkManager` repart d'une base de travaux neuve,
     * ce qui isole les tests les uns des autres.
     *
     * Aucune `WorkerFactory` n'est fournie : ces tests n'exécutent aucun `RappelWorker`, ils
     * n'observent que ce qui est **en file**. Rien n'est jamais construit, donc rien ne
     * réclame les dépendances Koin du worker. Le comportement du worker au réveil est déjà
     * couvert par B11.
     */
    @Before
    fun preparer() {
        contexte = ApplicationProvider.getApplicationContext<Context>()

        WorkManagerTestInitHelper.initializeTestWorkManager(
            contexte,
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build(),
        )
        workManager = WorkManager.getInstance(contexte)

        base = Room.inMemoryDatabaseBuilder(contexte, AppDatabase::class.java).build()
        enfants = EnfantRepositoryImpl(
            // (B17) La base elle-même, en plus des deux DAO : l'import ouvre une transaction
            // à cheval sur `enfants` et `vaccins_administres`.
            base = base,
            enfantDao = base.enfantDao(),
            vaccinAdministreDao = base.vaccinAdministreDao(),
        )

        planificateur = PlanificateurRappels(
            workManager = workManager,
            enfants = enfants,
            reference = CalendrierFige(CALENDRIER),
            calc = CalculateurEcheancier(),
            notifications = NotificationHelper(contexte),
            // Voir la KDoc de la classe : sans ce `null`, le build debug plafonnerait tous
            // les délais à une minute.
            plafondDelai = null,
            horloge = { MAINTENANT },
        )
    }

    @After
    fun fermerLaBase() {
        base.close()
    }

    // --- Création d'un enfant (US-B1, déclencheur EditionEnfantViewModel.onEnregistrer) ---

    /**
     * Un rappel par dose **à venir**, et aucun pour les autres statuts (R3).
     *
     * Le carnet de Soa au 03/02/2026 contient quatre doses et exactement deux rappels : R3 ne
     * programme que pour une ligne `AVenir` dont l'émission est encore devant nous.
     * - `bcg` est `AFaire` (prévue à la naissance, encore dans sa fenêtre de 30 jours) : son
     *   échéance est passée, un rappel « dans 3 jours » n'aurait plus de sens ;
     * - `penta2` est `EnAttente` de `penta1` : sa date prévue bougera dès que la 1re dose
     *   sera saisie (R1), annoncer la date théorique serait faux.
     */
    @Test
    fun apresCreation_unRappelParDoseAVenir() = runBlocking {
        enfants.enregistrer(SOA)
        planificateur.replanifier(ID_ENFANT)

        assertEquals("deux doses à venir, donc deux rappels", 2, travauxEnFile().size)
        assertNotNull("penta1 est à venir le 26/02", travailDe("penta1"))
        assertNotNull("rr1 est à venir le 12/10", travailDe("rr1"))
        assertNull("bcg est à faire, pas à venir", travailDe("bcg"))
        assertNull("penta2 est en attente de penta1", travailDe("penta2"))

        assertEquals(WorkInfo.State.ENQUEUED, travailDe("penta1")?.state)
    }

    /**
     * Les délais enfilés sont ceux de R3 : `prevuLe - 3 jours` à 09:00.
     *
     * Les deux attendus sont dérivés à la main du CDC et non du code :
     * naissance le 15/01/2026, `penta1` à 42 jours soit le 26/02, rappel le 23/02 à 09:00 ;
     * `rr1` à 270 jours soit le 12/10, rappel le 09/10 à 09:00. L'instant de référence étant
     * figé, l'égalité est exacte — aucune tolérance à prévoir.
     */
    @Test
    fun lesDelaisEnfilesSontCeuxDeLaRegleR3() = runBlocking {
        enfants.enregistrer(SOA)
        planificateur.replanifier(ID_ENFANT)

        assertEquals(
            "rappel de penta1 le 23/02/2026 à 09:00",
            Duration.between(MAINTENANT, LocalDateTime.of(2026, 2, 23, 9, 0)),
            delaiDe("penta1"),
        )
        assertEquals(
            "rappel de rr1 le 09/10/2026 à 09:00",
            Duration.between(MAINTENANT, LocalDateTime.of(2026, 10, 9, 9, 0)),
            delaiDe("rr1"),
        )
    }

    /**
     * Modifier la date de naissance décale tout l'échéancier, donc tous les rappels.
     *
     * C'est l'autre moitié du même déclencheur : `onEnregistrer` rejoue la replanification en
     * modification comme en création. Soa décalée de sept jours, `penta1` passe du 26/02 au
     * 05/03 et son rappel du 23/02 au 02/03.
     */
    @Test
    fun apresModificationDeLaDateDeNaissance_lesRappelsSuivent() = runBlocking {
        enfants.enregistrer(SOA)
        planificateur.replanifier(ID_ENFANT)

        enfants.enregistrer(SOA.copy(dateNaissance = LocalDate.of(2026, 1, 22)))
        planificateur.replanifier(ID_ENFANT)

        assertEquals("toujours deux doses à venir", 2, travauxEnFile().size)
        assertEquals(
            "rappel de penta1 repoussé au 02/03/2026 à 09:00",
            Duration.between(MAINTENANT, LocalDateTime.of(2026, 3, 2, 9, 0)),
            delaiDe("penta1"),
        )
    }

    // --- Saisie d'une dose (US-B5 scénario 2, déclencheur SaisieVaccinViewModel) ---

    /**
     * **Le scénario qui compte** : « vaccin saisi avant le rappel » (US-B5).
     *
     * Le rappel de la dose saisie disparaît de la file — il n'est pas seulement ignoré au
     * réveil par la relecture du worker, il n'est plus programmé du tout. Et la dose qui en
     * dépend prend sa place : `penta2` passe de `EnAttente` à `AVenir`, son rappel entre en
     * file alors qu'il n'existait pas avant la saisie. Un rappel remplace l'autre, le compte
     * reste à deux.
     */
    @Test
    fun saisirLaDoseRetireSonRappel() = runBlocking {
        enfants.enregistrer(SOA)
        planificateur.replanifier(ID_ENFANT)
        assertNotNull("le rappel doit exister avant la saisie", travailDe("penta1"))
        assertNull("penta2 est en attente avant la saisie", travailDe("penta2"))

        enfants.enregistrerAdministration(PENTA1_DE_SOA)
        planificateur.replanifier(ID_ENFANT)

        assertNull("le rappel de la dose saisie doit disparaître", travailDe("penta1"))
        assertNotNull("penta2 n'attend plus penta1", travailDe("penta2"))
        assertNotNull("rr1 n'est pas concernée", travailDe("rr1"))
        assertEquals(2, travauxEnFile().size)
    }

    /**
     * La dose enchaînée est reprogrammée depuis la date **réelle** de la précédente (R1).
     *
     * Le pendant chiffré du test ci-dessus, et le scénario « dose dépendante d'une dose
     * précédente » de US-B2 : `penta2` était théoriquement prévue le 26/03, calculée depuis
     * la date théorique de `penta1`. La 1re dose saisie le 03/02, elle tombe le 03/03 — 28
     * jours après la date réelle — et son rappel le 28/02 à 09:00.
     */
    @Test
    fun apresSaisie_leRappelDeLaDoseDependanteRepartDeLaDateReelle() = runBlocking {
        enfants.enregistrer(SOA)
        enfants.enregistrerAdministration(PENTA1_DE_SOA)
        planificateur.replanifier(ID_ENFANT)

        assertEquals(
            "rappel de penta2 le 28/02/2026 à 09:00, soit 3 jours avant le 03/03",
            Duration.between(MAINTENANT, LocalDateTime.of(2026, 2, 28, 9, 0)),
            delaiDe("penta2"),
        )
    }

    /**
     * Supprimer la saisie reprogramme son rappel (US-B4).
     *
     * Le pendant du test précédent : la dose redevient à venir, son rappel revient en file et
     * `penta2` retourne en attente.
     */
    @Test
    fun supprimerLaSaisieReprogrammeSonRappel() = runBlocking {
        enfants.enregistrer(SOA)
        enfants.enregistrerAdministration(PENTA1_DE_SOA)
        planificateur.replanifier(ID_ENFANT)
        assertNull(travailDe("penta1"))

        enfants.supprimerAdministration(PENTA1_DE_SOA.id)
        planificateur.replanifier(ID_ENFANT)

        assertNotNull("la dose supprimée redevient à venir", travailDe("penta1"))
        assertNull("penta2 retourne en attente de penta1", travailDe("penta2"))
        assertEquals(2, travauxEnFile().size)
    }

    // --- Suppression de l'enfant (US-B1, déclencheur EditionEnfantViewModel) ---

    /**
     * « Ses vaccins administrés et ses rappels sont supprimés avec lui » (US-B1).
     *
     * Les doses partent par la cascade SQL, les travaux non : ils ne sont dans aucune table
     * du carnet. C'est `annulerTous` — le chemin retenu par `EditionEnfantViewModel`, qui ne
     * peut plus lire un enfant effacé — qui les retire par leur étiquette de groupe.
     *
     * La seconde moitié du test vérifie la promesse de la KDoc de `replanifier` : appelée sur
     * un enfant absent du carnet, elle dégénère proprement en annulation au lieu d'échouer.
     */
    @Test
    fun supprimerLEnfantNeLaisseAucunRappel() = runBlocking {
        enfants.enregistrer(SOA)
        planificateur.replanifier(ID_ENFANT)
        assertEquals(2, travauxEnFile().size)

        enfants.supprimer(ID_ENFANT)
        planificateur.annulerTous(ID_ENFANT)

        assertEquals("aucun rappel ne survit à l'enfant", 0, travauxEnFile().size)

        planificateur.replanifier(ID_ENFANT)
        assertEquals("replanifier un enfant absent n'enfile rien", 0, travauxEnFile().size)
    }

    // --- Idempotence (R4) ---

    /**
     * Règle R4 : « on peut tout replanifier sans doublon ».
     *
     * Trois appels de suite, et le compte ne bouge pas. Deux protections se superposent — le
     * `cancelAllWorkByTag` en tête de `replanifier` et la politique `REPLACE` de chaque
     * `enqueueUniqueWork` — d'où la vérification par nom unique autant que par compte total :
     * c'est ce qui rend l'appel sûr après **chaque** écriture, sans se demander si c'est la
     * première fois.
     */
    @Test
    fun troisReplanificationsDeSuite_neCreentAucunDoublon() = runBlocking {
        enfants.enregistrer(SOA)

        repeat(3) { planificateur.replanifier(ID_ENFANT) }

        assertEquals("toujours deux rappels, pas six", 2, travauxEnFile().size)
        assertEquals("un seul travail vivant pour penta1", 1, travauxSous("penta1").size)
        assertEquals("un seul travail vivant pour rr1", 1, travauxSous("rr1").size)
    }

    // --- Lecture de la file ---------------------------------------------------

    /**
     * Attend que WorkManager ait fini de traiter ce que le planificateur vient de lui demander.
     *
     * `replanifier` ne bloque jamais : `cancelAllWorkByTag` et `enqueueUniqueWork` rendent
     * chacun une `Operation` qu'il ignore, et c'est le bon comportement — un écran n'a aucune
     * raison d'attendre l'écriture de la base de WorkManager. Un test, si : sans cette
     * barrière, une assertion pourrait lire la file avant que la mise en file n'y soit.
     *
     * Toutes ces opérations passent par l'exécuteur **sérialisé** de WorkManager. En demander
     * une de plus et attendre sa fin, c'est donc attendre la fin des précédentes. L'étiquette
     * est volontairement inconnue : l'annulation ne porte sur rien et ne sert qu'à faire la
     * queue derrière elles.
     */
    private fun attendreWorkManager() {
        workManager.cancelAllWorkByTag(ETIQUETTE_SENTINELLE).result.get()
    }

    /**
     * Les travaux de rappel **encore vivants** pour cet enfant.
     *
     * Le filtre sur `isFinished` n'est pas cosmétique : `cancelAllWorkByTag` et la politique
     * `REPLACE` laissent derrière eux des travaux à l'état `CANCELLED`, qui portent toujours
     * l'étiquette de groupe. Les compter serait compter les replanifications passées.
     */
    private fun travauxEnFile(): List<WorkInfo> {
        attendreWorkManager()
        return workManager.getWorkInfosByTag(etiquetteTravauxEnfant(ID_ENFANT)).get()
            .filter { !it.state.isFinished }
    }

    /** Les travaux vivants portant le nom unique de R4 pour cette dose (au plus un attendu). */
    private fun travauxSous(vaccinId: String): List<WorkInfo> {
        attendreWorkManager()
        return workManager
            .getWorkInfosForUniqueWork(
                nomTravailRappel(ID_ENFANT, vaccinId, TypeRappel.AVANT_ECHEANCE),
            )
            .get()
            .filter { !it.state.isFinished }
    }

    /** Le rappel principal de cette dose, ou `null` s'il n'est pas programmé. */
    private fun travailDe(vaccinId: String): WorkInfo? = travauxSous(vaccinId).firstOrNull()

    /**
     * Le délai initial du rappel de cette dose. Échoue bruyamment s'il n'y en a pas.
     *
     * `WorkInfo.initialDelayMillis` demande **WorkManager 2.9 ou plus** ; le catalogue est en
     * 2.10.0. C'est le seul point de ce fichier qui dépende d'une API récente : si un jour la
     * version du catalogue redescend, seuls les trois tests de délai sont à revoir, les
     * autres ne lisent que `state`.
     */
    private fun delaiDe(vaccinId: String): Duration {
        val travail = travailDe(vaccinId) ?: error("Aucun rappel en file pour « $vaccinId »")
        return Duration.ofMillis(travail.initialDelayMillis)
    }

    private companion object {
        const val ID_ENFANT: String = "enfant-de-test-b12"

        /** Étiquette que rien ne porte : elle ne sert qu'à [attendreWorkManager]. */
        const val ETIQUETTE_SENTINELLE: String = "sentinelle-de-test-b12"

        /**
         * L'instant figé de tous les tests : mardi 3 février 2026 à 8 h.
         *
         * Paramètre et non horloge système : les dates prévues, les statuts et les délais en
         * dépendent tous, et un test qui bascule selon le jour où on le lance ne vaut rien.
         */
        val MAINTENANT: LocalDateTime = LocalDateTime.of(2026, 2, 3, 8, 0)

        val SOA = Enfant(
            id = ID_ENFANT,
            prenom = "Soa",
            dateNaissance = LocalDate.of(2026, 1, 15),
            sexe = Sexe.FILLE,
        )

        /** Saisie le jour même, comme le ferait l'écran : R5 refuse une date future. */
        val PENTA1_DE_SOA = VaccinAdministre(
            id = "administration-de-test-b12",
            enfantId = ID_ENFANT,
            vaccinId = "penta1",
            date = LocalDate.of(2026, 2, 3),
            lieu = "CSB2 Ankirihiry",
            lot = null,
        )

        /**
         * Quatre doses, une par situation que R3 doit distinguer au 03/02/2026 pour un enfant
         * né le 15/01/2026 :
         *
         * | dose | prévue le | statut | rappel |
         * |---|---|---|---|
         * | `bcg` | 15/01 | à faire (fenêtre de 30 j) | aucun |
         * | `penta1` | 26/02 | à venir | 23/02 à 09:00 |
         * | `penta2` | 26/03 | en attente de `penta1` | aucun |
         * | `rr1` | 12/10 | à venir | 09/10 à 09:00 |
         *
         * Les délais et tolérances sont ceux du calendrier de démonstration (§B5.1).
         */
        val CALENDRIER: List<VaccinReference> = listOf(
            VaccinReference(
                id = "bcg",
                nom = "BCG",
                dose = "dose unique",
                ordre = 1,
                ageJours = 0,
                dependDe = null,
                toleranceJours = 30,
                description = "À la naissance",
            ),
            VaccinReference(
                id = "penta1",
                nom = "Pentavalent",
                dose = "1re dose",
                ordre = 2,
                ageJours = 42,
                dependDe = null,
                toleranceJours = 14,
                description = "6 semaines",
            ),
            VaccinReference(
                id = "penta2",
                nom = "Pentavalent",
                dose = "2e dose",
                ordre = 3,
                ageJours = 28,
                dependDe = "penta1",
                toleranceJours = 14,
                description = "4 semaines après la 1re dose",
            ),
            VaccinReference(
                id = "rr1",
                nom = "Rougeole-Rubéole",
                dose = "1re dose",
                ordre = 4,
                ageJours = 270,
                dependDe = null,
                toleranceJours = 30,
                description = "9 mois",
            ),
        )
    }
}

/**
 * Calendrier de référence immuable, à la place de `ReferenceRepositoryImpl`.
 *
 * Le planificateur ne lit du contenu de référence que `observerCalendrier()`. Passer par
 * l'implémentation réelle demanderait `SourcesEmbarquees`, `PreferencesLocales` et les trois
 * DAO de référence pour retrouver, au mieux, le calendrier des assets — c'est-à-dire un jeu
 * de données que ces tests ne contrôlent pas.
 */
private class CalendrierFige(private val calendrier: List<VaccinReference>) : ReferenceRepository {

    override fun observerCalendrier(): Flow<List<VaccinReference>> = flowOf(calendrier)

    override fun observerInfosSource(): Flow<InfosSource> = flowOf(
        InfosSource(
            source = "Calendrier de test B12",
            publieLe = LocalDate.of(2026, 1, 1),
            version = 1,
        ),
    )

    /** Jamais appelée par le planificateur : la base des tests est peuplée à la main. */
    override suspend fun chargerEmbarqueSiVide() = Unit
}
