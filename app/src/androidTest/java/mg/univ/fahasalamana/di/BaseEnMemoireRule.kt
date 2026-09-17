package mg.univ.fahasalamana.di

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import mg.univ.fahasalamana.data.local.AppDatabase
import mg.univ.fahasalamana.data.local.ReferenceDao
import mg.univ.fahasalamana.data.local.SourcesEmbarquees
import mg.univ.fahasalamana.data.local.versEntites
import mg.univ.fahasalamana.data.remote.CalendrierDto
import mg.univ.fahasalamana.data.repository.CentreRepository
import mg.univ.fahasalamana.data.repository.CentreRepositoryImpl
import mg.univ.fahasalamana.data.repository.EnfantRepository
import mg.univ.fahasalamana.data.repository.EnfantRepositoryImpl
import mg.univ.fahasalamana.data.repository.ReferenceRepository
import mg.univ.fahasalamana.data.repository.ReferenceRepositoryImpl
import org.junit.rules.ExternalResource
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Remplace la base de données de l'application par une base **en mémoire**, le temps d'un
 * test instrumenté (tâche B21).
 *
 * ## Pourquoi pas simplement `createAndroidComposeRule<MainActivity>()` sur la vraie base
 *
 * Parce qu'un test d'interface qui ajoute un enfant l'ajoute alors pour de bon : il reste
 * dans `fahasalamana.db` après la fin du test, s'accumule d'une exécution à l'autre et rend
 * le test suivant dépendant du précédent (« Mes enfants » n'est plus vide, le bouton
 * d'ajout n'est plus celui de l'état vide). Ce sont en plus des **données de santé**
 * fictives écrites dans le fichier de l'utilisateur de l'appareil de démonstration. Une
 * base en mémoire repart vide à chaque test et disparaît avec lui.
 *
 * ## Pourquoi un module Koin surchargé, et non un `Application` de test
 *
 * Koin est démarré par `App.onCreate`, donc **avant** le premier test : il n'y a pas de
 * fenêtre pour appeler `startKoin` soi-même. La solution propre serait un runner
 * d'instrumentation qui instancie une `Application` de test sans amorçage — mais cela se
 * déclare par `testInstrumentationRunner` dans `app/build.gradle.kts`, hors du périmètre
 * de B21 (qui n'écrit que dans `src/androidTest/`). C'est signalé dans le rapport de tâche.
 *
 * On surcharge donc les définitions existantes : [loadKoinModules] réenregistre les mêmes
 * clés (`allowOverride` est vrai par défaut depuis Koin 3.2) et les prochaines résolutions
 * repartent des définitions de test. Deux conséquences dont il faut avoir conscience :
 *
 * - **toute la chaîne** doit être redéclarée, base, DAO *et* repositories : un repository
 *   déjà construit garderait sinon les DAO de la vraie base ;
 * - l'amorçage lancé par `App.onCreate` a déjà commencé à remplir la **vraie** base avec
 *   `calendrier.json` et `csb.json`. Il continue sur son coin, sans effet sur les tests :
 *   ce n'est que du contenu de référence, jamais une donnée personnelle.
 *
 * Le module est **reconstruit à chaque test** (fonction et non `val` de fichier) : un
 * `Module` retient ses fabriques, et réenregistrer le même objet rendrait la même base
 * d'un test à l'autre — exactement ce que l'on cherche à éviter.
 *
 * ## Ce que la règle met en place
 *
 * Le calendrier de référence est chargé depuis `assets/calendrier.json`, par le même
 * `SourcesEmbarquees` que l'application. Il est aussi exposé en [calendrier] : les tests y
 * lisent le nombre de doses attendu au lieu de l'écrire en dur.
 *
 * L'annuaire des centres n'est **pas** chargé : 402 lignes dont aucun écran testé ici n'a
 * besoin.
 */
class BaseEnMemoireRule : ExternalResource() {

    /**
     * Le calendrier embarqué, tel que l'application le lit.
     *
     * Disponible dès `before()`, donc dans tout `@Test`.
     */
    lateinit var calendrier: CalendrierDto
        private set

    private var base: AppDatabase? = null

    override fun before() {
        loadKoinModules(moduleDeTest())

        val koin = GlobalContext.get()
        base = koin.get<AppDatabase>()

        runBlocking {
            calendrier = koin.get<SourcesEmbarquees>().calendrier()
            koin.get<ReferenceDao>().remplacerCalendrier(calendrier.versEntites())
        }
    }

    override fun after() {
        // L'activité est déjà fermée : sa règle est imbriquée à l'intérieur de celle-ci
        // (RuleChain.outerRule(cette règle).around(règle Compose)), et les `after` se
        // dénouent de l'intérieur vers l'extérieur. Plus personne ne lit la base.
        base?.close()
        base = null
    }
}

/**
 * Les définitions de `appModule` qui touchent à la base, réécrites sur une base en mémoire.
 *
 * Les autres définitions de l'application restent en place et sont réutilisées telles
 * quelles : `SourcesEmbarquees`, `PreferencesLocales`, `CalculateurEcheancier`, l'horloge
 * du jour et tous les ViewModels.
 *
 * L'horloge n'est volontairement **pas** remplacée par une date figée : le formulaire
 * d'ajout refuse une date de naissance postérieure à `aujourdHui`, et le sélecteur Material
 * ouvre, lui, sur le mois réel de l'appareil. Figer l'horloge à une date inventée rendrait
 * impossible de choisir une date de naissance valide dans ce sélecteur.
 */
private fun moduleDeTest(): Module = module {

    // --- Base de données et DAO ---
    single {
        Room.inMemoryDatabaseBuilder(androidContext(), AppDatabase::class.java).build()
    }
    single { get<AppDatabase>().enfantDao() }
    single { get<AppDatabase>().vaccinAdministreDao() }
    single { get<AppDatabase>().vaccinReferenceDao() }
    single { get<AppDatabase>().centreDao() }
    single { get<AppDatabase>().referenceDao() }

    // --- Repositories ---
    // Redéclarés bien qu'inchangés : ils capturent des DAO dans leur constructeur, et ceux
    // de la vraie base survivraient à la surcharge de la seule définition de `AppDatabase`.
    single<ReferenceRepository> {
        ReferenceRepositoryImpl(
            vaccinReferenceDao = get(),
            centreDao = get(),
            referenceDao = get(),
            sources = get(),
            preferences = get(),
            // (B19) Le repository de référence sait aussi aller chercher une mise à jour :
            // le vrai client est injecté, les tests n'appellent simplement pas mettreAJour().
            api = get(),
        )
    }
    single<CentreRepository> { CentreRepositoryImpl(centreDao = get()) }
    single<EnfantRepository> {
        EnfantRepositoryImpl(
            // (B17) La fusion d'un carnet importé écrit les deux tables personnelles dans
            // une seule transaction : le repository a besoin de la base en mémoire du test.
            base = get(),
            enfantDao = get(),
            vaccinAdministreDao = get(),
        )
    }
}
