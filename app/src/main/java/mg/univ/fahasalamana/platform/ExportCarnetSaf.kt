package mg.univ.fahasalamana.platform

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/*
 * ---------------------------------------------------------------------------
 * SORTIE DU CARNET PAR LE STORAGE ACCESS FRAMEWORK (§B8, point 2)
 * ---------------------------------------------------------------------------
 *
 * Le carnet contient des données de santé. Le CDC n'autorise sa sortie de l'appareil que
 * par une **action explicite de l'utilisateur**, et impose le chemin : `ACTION_CREATE_DOCUMENT`,
 * c'est-à-dire `ActivityResultContracts.CreateDocument` côté AndroidX.
 *
 * Ce que cela interdit, et qui n'est donc écrit nulle part dans ce fichier :
 * - **aucune permission de stockage** (`WRITE_EXTERNAL_STORAGE` et suivantes) n'est
 *   déclarée ni demandée : le SAF donne un accès à **un seul document**, celui que
 *   l'utilisateur vient de désigner, et rien d'autre ;
 * - **aucune écriture dans un dossier choisi par l'application** (`Downloads`,
 *   `getExternalFilesDir`, un dossier « Fahasalamana ») : l'application n'a pas à décider
 *   où atterrit un carnet de santé, et un fichier déposé sans que le parent sache où
 *   serait une fuite silencieuse ;
 * - **aucun partage, aucun envoi** : pas d'`ACTION_SEND`, pas de `FileProvider`, pas de
 *   réseau. Le document est écrit là où l'utilisateur l'a demandé, un point c'est tout.
 *
 * L'`Uri` reçue est une permission ponctuelle d'écriture, valable pour cet appel : elle
 * n'est **pas** conservée (`takePersistableUriPermission`), justement pour que
 * l'application ne garde aucun accès durable à un emplacement de l'utilisateur.
 */

/**
 * Type MIME du document créé : du JSON.
 *
 * Passé au contrat `CreateDocument` — c'est lui qui décide de l'extension proposée par le
 * sélecteur de documents quand le fournisseur en ajoute une.
 */
const val MIME_CARNET: String = "application/json"

/**
 * Écrit un texte dans le document choisi par l'utilisateur.
 *
 * Une classe et non une fonction de haut niveau : le `Context` est une dépendance, donc
 * il est injecté par Koin (`single { EcrivainDocument(androidContext()) }`) et un test
 * peut prendre sa place. C'est aussi ce qui garde le ViewModel exempt d'API Android.
 */
class EcrivainDocument(private val context: Context) {

    /**
     * Écrit [contenu] en UTF-8 dans [destination].
     *
     * Trois points qui comptent :
     * - **UTF-8 explicite** : les prénoms portent des accents, et le codage par défaut de
     *   la plateforme n'est pas garanti identique à celui du téléphone qui relira le fichier ;
     * - **mode `"wt"`** (*write, truncate*) : si l'utilisateur désigne un fichier existant
     *   au lieu d'en créer un, le mode `"w"` de nombreux fournisseurs de documents écrit
     *   par-dessus **sans tronquer** — un carnet plus court que le précédent laisserait
     *   derrière lui la fin de l'ancien, et produirait un fichier illisible à l'import ;
     * - **`Dispatchers.IO`** : la seule écriture de fichier de l'application, et elle est
     *   déclenchée depuis l'interface.
     *
     * @throws IOException si le fournisseur de documents refuse d'ouvrir le flux (document
     *   supprimé entre le choix et l'écriture, support retiré, espace insuffisant).
     *   L'appelant en fait un message d'échec ; il n'y a rien à rattraper ici.
     */
    suspend fun ecrire(destination: Uri, contenu: String) {
        withContext(Dispatchers.IO) {
            val flux = context.contentResolver.openOutputStream(destination, "wt")
                ?: throw IOException("Le document choisi n'a pas pu être ouvert en écriture.")
            flux.use { it.write(contenu.toByteArray(Charsets.UTF_8)) }
        }
    }
}
