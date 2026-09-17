package mg.univ.fahasalamana

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Remplace `Dispatchers.Main` par un dispatcher de test, le temps d'une méthode.
 *
 * **Obligatoire dès qu'un test JVM construit un `ViewModel`** : `viewModelScope` est bâti sur
 * `Dispatchers.Main.immediate`, qui n'existe pas hors Android. Sans cette règle, le scope
 * retombe silencieusement sur un dispatcher d'arrière-plan et le test devient une course
 * entre deux fils — vert sur un poste, rouge sur un autre, sans que rien n'ait changé.
 *
 * Le dispatcher par défaut est [UnconfinedTestDispatcher] : les collecteurs de `Flow`
 * démarrent sur place, et une valeur poussée dans un faux repository a traversé toute la
 * chaîne `combine` au moment où `MutableStateFlow.value = …` rend la main. C'est ce qui
 * permet d'écrire « je pousse, puis j'attends l'état suivant » sans semer des
 * `advanceUntilIdle()` entre chaque ligne. Un test qui aurait besoin de contrôler l'ordre
 * d'ordonnancement passe un [kotlinx.coroutines.test.StandardTestDispatcher] au constructeur.
 *
 * `runTest` réutilise l'ordonnanceur (`TestCoroutineScheduler`) du `Dispatchers.Main` posé
 * ici : le temps virtuel du test et celui du `viewModelScope` sont donc le même, et un
 * `delay()` du ViewModel n'immobilise pas la suite réelle (cas de `SharingStarted.WhileSubscribed`).
 *
 * Règle réutilisable : elle resservira aux ViewModels `SaisieVaccin`, `Centres` et `Reglages`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegleDispatcherPrincipal(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
