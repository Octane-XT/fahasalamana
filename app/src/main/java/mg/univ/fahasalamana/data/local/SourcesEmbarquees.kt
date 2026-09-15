package mg.univ.fahasalamana.data.local

import android.content.Context
import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import mg.univ.fahasalamana.data.remote.AnnuaireDto
import mg.univ.fahasalamana.data.remote.CalendrierDto
import mg.univ.fahasalamana.data.remote.jsonReference

/**
 * Les deux fichiers de référence embarqués dans `assets/` (§B5.1, B03).
 *
 * Leur seule raison d'être : **le premier lancement doit fonctionner sans réseau**.
 * Une copie de `calendrier.json` et de `csb.json` voyage dans l'APK ; `ReferenceRepository`
 * la charge en base si celle-ci est vide, et c'est tout. Ensuite, la base fait foi, et
 * seule une mise à jour distante (B19) la remplace.
 *
 * Cette classe ne fait que **lire et désérialiser** : aucune décision de chargement,
 * aucune écriture en base. Ce découpage laisse la logique de `chargerEmbarqueSiVide()`
 * testable en la remplaçant par un double, et permet à B19 de réutiliser exactement les
 * mêmes types en changeant uniquement la provenance des octets.
 *
 * Les échecs ne sont pas rattrapés ici : un `IOException` (asset absent) ou un
 * `SerializationException` (fichier corrompu) remonte tel quel. C'est l'appelant qui
 * décide quoi en faire, et lui seul sait s'il peut continuer sans ce contenu.
 */
class SourcesEmbarquees(context: Context) {

    /** Le `Context` applicatif : cette classe est un singleton Koin, elle ne doit pas retenir une activité. */
    private val assets: AssetManager = context.applicationContext.assets

    /** Le calendrier vaccinal embarqué : 16 doses dans la version livrée en B03. */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun calendrier(): CalendrierDto = withContext(Dispatchers.IO) {
        assets.open(FICHIER_CALENDRIER).use { flux ->
            jsonReference.decodeFromStream<CalendrierDto>(flux)
        }
    }

    /**
     * L'annuaire embarqué : 23 régions, 76 districts, 402 centres dans la version livrée.
     *
     * Lu en flux (`decodeFromStream`) et non via un `String` intermédiaire : le fichier
     * pèse environ 115 Ko et il est désérialisé au démarrage de l'application, là où
     * chaque allocation évitée se voit.
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun annuaire(): AnnuaireDto = withContext(Dispatchers.IO) {
        assets.open(FICHIER_ANNUAIRE).use { flux ->
            jsonReference.decodeFromStream<AnnuaireDto>(flux)
        }
    }

    private companion object {
        /** Noms figés : ce sont ceux des fichiers publiés sous `v1/` (§B5.1), copiés tels quels dans `assets/`. */
        const val FICHIER_CALENDRIER = "calendrier.json"
        const val FICHIER_ANNUAIRE = "csb.json"
    }
}
