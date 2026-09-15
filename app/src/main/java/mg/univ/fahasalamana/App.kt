package mg.univ.fahasalamana

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mg.univ.fahasalamana.data.repository.ReferenceRepository
import mg.univ.fahasalamana.di.appModule
import mg.univ.fahasalamana.platform.creerCanauxNotification
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

/**
 * Point de démarrage de l'application : injection Koin, puis canaux de notification.
 *
 * Déclarée dans le manifeste par android:name=".App".
 */
class App : Application() {

    /**
     * Résolu paresseusement : le délégué n'interroge Koin qu'au premier accès, donc bien
     * après `startKoin`, alors que ce champ est construit avant `onCreate`.
     */
    private val referenceRepository: ReferenceRepository by inject()

    /**
     * Portée du travail de démarrage, détachée du cycle de vie des écrans : l'amorçage du
     * contenu de référence doit se terminer même si l'utilisateur quitte tout de suite.
     *
     * `SupervisorJob` pour qu'un échec n'emporte pas d'éventuelles tâches de démarrage
     * futures. Cette portée n'est jamais annulée : elle vit aussi longtemps que le
     * processus, ce qui est exactement sa durée utile.
     */
    private val porteeDemarrage = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@App)
            workManagerFactory()
            modules(appModule)
        }

        amorcerContenuDeReference()

        // (B10) Canal des rappels de vaccination. Créé ici plutôt qu'à la première
        // notification : un canal doit exister avant d'être utilisé, et `RappelWorker`
        // (B11) peut s'exécuter dans un processus réveillé par WorkManager où aucun écran
        // n'a encore été affiché — `onCreate` de l'Application, lui, est toujours passé.
        // L'appel est direct et non injecté par Koin : la création d'un canal ne dépend que
        // du Context, et faire dépendre le démarrage d'une définition Koin de plus, c'est
        // un plantage au lancement de plus le jour où elle manque.
        creerCanauxNotification(this)
    }

    /**
     * Charge `calendrier.json` et `csb.json` en base au tout premier lancement (B04).
     *
     * **Hors du fil principal** : la lecture de deux fichiers d'`assets/`, leur
     * désérialisation (≈ 115 Ko pour l'annuaire) et l'insertion de 402 lignes n'ont rien à
     * faire dans `onCreate`, qui bloque l'affichage du premier écran tant qu'il n'a pas
     * rendu la main.
     *
     * Les écrans ne dépendent pas de la fin de ce chargement : ils lisent la base par des
     * `Flow`, qui émettront d'eux-mêmes dès que les lignes seront insérées. Au pire, la
     * première fraction de seconde du premier lancement affiche un écran vide.
     *
     * Un échec est journalisé sans faire tomber l'application : un contenu de référence
     * illisible ne doit pas empêcher d'ouvrir le carnet déjà saisi, qui est la donnée
     * précieuse. Il se verra à l'écran (calendrier vide, annuaire vide) et la mise à jour
     * distante (B19) offrira une seconde chance.
     */
    private fun amorcerContenuDeReference() {
        porteeDemarrage.launch {
            runCatching { referenceRepository.chargerEmbarqueSiVide() }
                .onFailure { erreur ->
                    Log.e(ETIQUETTE, "Chargement des contenus de référence embarqués impossible", erreur)
                }
        }
    }

    private companion object {
        const val ETIQUETTE = "Fahasalamana"
    }
}
