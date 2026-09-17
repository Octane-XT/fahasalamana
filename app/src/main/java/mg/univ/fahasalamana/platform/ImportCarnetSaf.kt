package mg.univ.fahasalamana.platform

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/*
 * ---------------------------------------------------------------------------
 * ENTRÉE D'UN CARNET PAR LE STORAGE ACCESS FRAMEWORK (B17, §B8 point 2)
 * ---------------------------------------------------------------------------
 *
 * Pendant en lecture de `ExportCarnetSaf.kt`, et mêmes règles :
 * - **aucune permission de stockage** n'est déclarée ni demandée. `ACTION_OPEN_DOCUMENT`
 *   (`ActivityResultContracts.OpenDocument` côté AndroidX) donne un accès en lecture à **un
 *   seul document**, celui que l'utilisateur vient de désigner, et rien d'autre ;
 * - **aucun parcours de dossier** : l'application ne va pas chercher un carnet dans
 *   `Downloads` ni ailleurs, elle ne lit que ce qu'on lui tend ;
 * - **l'`Uri` n'est pas conservée** (`takePersistableUriPermission`) : l'autorisation vaut
 *   pour cette lecture, elle ne devient pas un accès durable à l'espace de l'utilisateur ;
 * - **rien n'est copié ni déplacé** : le fichier reste où il est, l'application en lit le
 *   texte et le referme. C'est ce que dit le message affiché après l'import.
 *
 * Le fichier lu vient de l'extérieur : il peut être n'importe quoi (une photo, un fichier
 * tronqué par un transfert Bluetooth, un carnet écrit par une version ultérieure). Rien ici
 * ne l'interprète — `analyserCarnet()` (`domain/ImportCarnet.kt`) s'en charge, et refuse
 * avant toute écriture en base.
 */

/**
 * Types de documents proposés par le sélecteur.
 *
 * Trois et non un seul : le fichier a beau être écrit en `application/json` par l'export,
 * il arrive sur l'autre téléphone par un transfert (Bluetooth, câble, messagerie) qui lui
 * réattribue un type d'après son extension ou son contenu. Selon la version d'Android et le
 * fournisseur de documents, un `.json` se présente en `application/json`, en `text/plain`
 * ou en `application/octet-stream` — et un type absent de cette liste rendrait le carnet
 * **grisé, donc inatteignable**, dans le sélecteur.
 *
 * Pas de filtre universel « tous les fichiers » pour autant : ouvrir la galerie entière
 * n'aiderait pas le parent à retrouver son carnet. Un fichier qui passerait quand même le
 * filtre sans être un carnet ressort en « fichier non reconnu », sans rien modifier.
 */
val MIMES_CARNET_IMPORT: Array<String> = arrayOf(
    MIME_CARNET,
    "text/plain",
    "application/octet-stream",
)

/**
 * Taille maximale acceptée pour un carnet, en octets.
 *
 * Le document est choisi par l'utilisateur : rien n'empêche de désigner une vidéo de 2 Go,
 * qui serait chargée en mémoire d'un téléphone d'entrée de gamme (§B9 : la cible est
 * l'entrée de gamme). 4 Mio laissent une marge considérable — un carnet de dix enfants pèse
 * quelques dizaines de kilo-octets — et transforment un plantage mémoire en une `IOException`,
 * donc en message « Le fichier n'a pas été lu ».
 */
const val TAILLE_MAX_CARNET_OCTETS: Int = 4 * 1024 * 1024

/**
 * Lit le texte du document choisi par l'utilisateur.
 *
 * Une classe et non une fonction de haut niveau, exactement comme [EcrivainDocument] : le
 * `Context` est une dépendance injectée par Koin (`single { LecteurDocument(androidContext()) }`),
 * ce qui garde le ViewModel exempt d'API Android et permet à un test de prendre sa place.
 */
class LecteurDocument(private val context: Context) {

    /**
     * Lit [source] en UTF-8 et rend son texte.
     *
     * - **UTF-8 explicite** : les prénoms portent des accents, et le codage par défaut de la
     *   plateforme n'est pas garanti identique à celui du téléphone qui a écrit le fichier ;
     * - **lecture bornée** à [TAILLE_MAX_CARNET_OCTETS], par blocs plutôt que d'un coup ;
     * - **marque d'ordre des octets retirée** : un carnet passé par un éditeur de texte de
     *   bureau peut commencer par un BOM UTF-8, invisible à l'œil, qui ferait échouer le
     *   décodage JSON dès le premier caractère ;
     * - **`Dispatchers.IO`** : déclenché depuis l'interface, comme l'écriture.
     *
     * @throws IOException si le document ne peut pas être ouvert (fichier supprimé entre le
     *   choix et la lecture, support retiré, autorisation révoquée) ou s'il dépasse la
     *   taille maximale. L'appelant en fait un message d'échec ; il n'y a rien à rattraper ici.
     */
    suspend fun lire(source: Uri): String = withContext(Dispatchers.IO) {
        val flux = context.contentResolver.openInputStream(source)
            ?: throw IOException("Le document choisi n'a pas pu être ouvert en lecture.")

        val octets = flux.use { entree ->
            val accumulateur = ByteArrayOutputStream()
            val morceau = ByteArray(8 * 1024)
            while (true) {
                val lus = entree.read(morceau)
                if (lus == -1) break
                accumulateur.write(morceau, 0, lus)
                if (accumulateur.size() > TAILLE_MAX_CARNET_OCTETS) {
                    throw IOException("Le document choisi dépasse la taille d'un carnet.")
                }
            }
            accumulateur.toByteArray()
        }

        String(octets, Charsets.UTF_8).trimStart(MARQUE_ORDRE_OCTETS)
    }
}

/**
 * Marque d'ordre des octets (BOM) UTF-8, telle qu'elle apparaît une fois le texte décodé :
 * le point de code U+FEFF.
 *
 * Écrite en point de code et non en séquence d'échappement dans une chaîne : le caractère
 * est invisible, et personne ne doit avoir à deviner ce qui se trouve entre deux guillemets
 * apparemment collés.
 */
private val MARQUE_ORDRE_OCTETS: Char = Char(0xFEFF)
