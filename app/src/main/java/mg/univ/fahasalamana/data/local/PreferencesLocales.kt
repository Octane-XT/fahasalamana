package mg.univ.fahasalamana.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate

/** Un seul fichier de préférences pour l'application ; l'extension est portée par le `Context` applicatif. */
private val Context.fichierPreferences: DataStore<Preferences> by preferencesDataStore(name = "preferences")

/**
 * Les quelques réglages qui ne méritent pas une table (§B5.2).
 *
 * Ce qui vit ici : les versions de contenu de référence déjà chargées, la date de la dernière
 * vérification de mise à jour, et les éléments du verrouillage par code. Ce qui ne vit pas ici :
 * la moindre donnée de santé — enfants et doses reçues sont en base, et nulle part ailleurs.
 *
 * Cette classe n'expose **que des clés, des lectures et des écritures**. Aucune logique :
 * - le hachage SHA-256 salé du code et sa vérification sont écrits en B18 (§B8, point 4) ;
 * - la comparaison `version distante > version locale` est écrite en B19 (§B5.1).
 *
 * Le code en clair n'est jamais stocké : seuls [pinHash] et [pinSel] le sont.
 */
class PreferencesLocales(context: Context) {

    private val datastore = context.applicationContext.fichierPreferences

    /** Un fichier illisible ne doit pas faire tomber l'application : on repart des valeurs par défaut. */
    private val preferences: Flow<Preferences> = datastore.data
        .catch { erreur ->
            if (erreur is IOException) emit(emptyPreferences()) else throw erreur
        }

    // --- Contenu de référence (B04, B19) -------------------------------------

    /** Version du `calendrier.json` chargé en base, [VERSION_ABSENTE] tant que rien n'a été chargé. */
    val calendrierVersion: Flow<Int> = preferences.map { it[Cles.CALENDRIER_VERSION] ?: VERSION_ABSENTE }

    /** Version du `csb.json` chargé en base, [VERSION_ABSENTE] tant que rien n'a été chargé. */
    val annuaireVersion: Flow<Int> = preferences.map { it[Cles.ANNUAIRE_VERSION] ?: VERSION_ABSENTE }

    /** Jour de la dernière vérification de mise à jour, affiché dans les réglages. `null` si jamais vérifié. */
    val derniereVerification: Flow<LocalDate?> = preferences.map { prefs ->
        prefs[Cles.DERNIERE_VERIFICATION]?.let { LocalDate.parse(it) }
    }

    /**
     * Mention de provenance du calendrier chargé en base (§B5.1), affichée telle quelle.
     * Chaîne vide tant qu'aucun contenu n'a été chargé.
     */
    val calendrierSource: Flow<String> = preferences.map { it[Cles.CALENDRIER_SOURCE].orEmpty() }

    /**
     * Jour de publication du calendrier chargé en base, `null` tant qu'aucun contenu n'a été chargé.
     *
     * C'est la date **du fichier publié**, pas celle de son chargement sur l'appareil.
     * Texte ISO en DataStore, comme partout ailleurs dans le projet.
     */
    val calendrierPublieLe: Flow<LocalDate?> = preferences.map { prefs ->
        prefs[Cles.CALENDRIER_PUBLIE_LE]?.let { LocalDate.parse(it) }
    }

    suspend fun enregistrerVersionCalendrier(version: Int) {
        datastore.edit { it[Cles.CALENDRIER_VERSION] = version }
    }

    /**
     * Écrit d'un seul `edit` la version, la source et la date de publication du calendrier
     * qui vient d'entrer en base (B04, et B19 après une mise à jour).
     *
     * À préférer à [enregistrerVersionCalendrier] : les trois valeurs décrivent le même
     * fichier et alimentent le même `InfosSource`. Les écrire séparément ouvrirait une
     * fenêtre où l'écran « À propos des données » annoncerait une version avec la source
     * de la précédente.
     */
    suspend fun enregistrerInfosCalendrier(version: Int, source: String, publieLe: LocalDate) {
        datastore.edit { prefs ->
            prefs[Cles.CALENDRIER_VERSION] = version
            prefs[Cles.CALENDRIER_SOURCE] = source
            prefs[Cles.CALENDRIER_PUBLIE_LE] = publieLe.toString()
        }
    }

    suspend fun enregistrerVersionAnnuaire(version: Int) {
        datastore.edit { it[Cles.ANNUAIRE_VERSION] = version }
    }

    /** Stockée en texte ISO `yyyy-MM-dd`, comme les dates de la base : jamais en epoch. */
    suspend fun enregistrerDerniereVerification(jour: LocalDate) {
        datastore.edit { it[Cles.DERNIERE_VERIFICATION] = jour.toString() }
    }

    // --- Rappels (B10) --------------------------------------------------------

    /**
     * L'explication précédant la demande de `POST_NOTIFICATIONS` a déjà été suivie d'une
     * vraie demande système (Android 13+, CDC §B8).
     *
     * Ce drapeau existe parce qu'Android ne permet pas de distinguer « jamais demandé » de
     * « refusé définitivement » : `shouldShowRequestPermissionRationale()` renvoie `false`
     * dans les deux cas. Sans lui, l'explication reviendrait à chaque ajout d'enfant chez
     * quelqu'un qui a déjà dit non — et la boîte système, elle, ne s'afficherait plus.
     *
     * Il n'est **pas** écrit quand l'utilisateur répond « Plus tard » : reporter n'est pas
     * refuser, et la question a le droit de revenir au prochain enfant ajouté.
     */
    val notificationsDemandeFaite: Flow<Boolean> =
        preferences.map { it[Cles.NOTIFICATIONS_DEMANDE_FAITE] ?: false }

    suspend fun marquerDemandeNotificationsFaite() {
        datastore.edit { it[Cles.NOTIFICATIONS_DEMANDE_FAITE] = true }
    }

    // --- Verrouillage par code (B18) -----------------------------------------

    /** Empreinte SHA-256 salée du code, jamais le code lui-même. `null` si aucun code n'est défini. */
    val pinHash: Flow<String?> = preferences.map { it[Cles.PIN_HASH] }

    /** Sel du hachage, tiré au hasard à la création du code et conservé avec l'empreinte. */
    val pinSel: Flow<String?> = preferences.map { it[Cles.PIN_SEL] }

    val verrouillageActif: Flow<Boolean> = preferences.map { it[Cles.VERROUILLAGE_ACTIF] ?: false }

    /** Instant du dernier passage au premier plan (epoch ms) : au-delà de deux minutes, l'écran de verrouillage revient (§B8). */
    val dernierAcces: Flow<Long> = preferences.map { it[Cles.DERNIER_ACCES] ?: 0L }

    /** Écrit empreinte et sel ensemble : les dissocier rendrait l'empreinte invérifiable. */
    suspend fun enregistrerPin(hash: String, sel: String) {
        datastore.edit { prefs ->
            prefs[Cles.PIN_HASH] = hash
            prefs[Cles.PIN_SEL] = sel
            prefs[Cles.VERROUILLAGE_ACTIF] = true
        }
    }

    suspend fun effacerPin() {
        datastore.edit { prefs ->
            prefs.remove(Cles.PIN_HASH)
            prefs.remove(Cles.PIN_SEL)
            prefs[Cles.VERROUILLAGE_ACTIF] = false
        }
    }

    suspend fun enregistrerDernierAcces(instantMillis: Long) {
        datastore.edit { it[Cles.DERNIER_ACCES] = instantMillis }
    }

    /** Noms de clés figés : les renommer perdrait les réglages des installations existantes. */
    private object Cles {
        val CALENDRIER_VERSION = intPreferencesKey("calendrier_version")
        val CALENDRIER_SOURCE = stringPreferencesKey("calendrier_source")
        val CALENDRIER_PUBLIE_LE = stringPreferencesKey("calendrier_publie_le")
        val ANNUAIRE_VERSION = intPreferencesKey("annuaire_version")
        val DERNIERE_VERIFICATION = stringPreferencesKey("derniere_verification")
        val NOTIFICATIONS_DEMANDE_FAITE = booleanPreferencesKey("notifications_demande_faite")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SEL = stringPreferencesKey("pin_sel")
        val VERROUILLAGE_ACTIF = booleanPreferencesKey("verrouillage_actif")
        val DERNIER_ACCES = longPreferencesKey("dernier_acces")
    }

    companion object {
        /**
         * Aucune version chargée. Les versions publiées commencent à 1 et croissent strictement
         * (§B5.1) : `0` est donc plus petit que toute version distante, et `chargerEmbarqueSiVide()`
         * comme la mise à jour distante se comparent au même entier.
         */
        const val VERSION_ABSENTE: Int = 0
    }
}
