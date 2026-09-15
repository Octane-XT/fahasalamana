package mg.univ.fahasalamana

import android.app.Application
import mg.univ.fahasalamana.di.appModule
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

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@App)
            workManagerFactory()
            modules(appModule)
        }

        // TODO(B10) : création du canal de notification des rappels de vaccination.
        // TODO(B04) : chargement des contenus de référence embarqués au premier lancement.
    }
}
