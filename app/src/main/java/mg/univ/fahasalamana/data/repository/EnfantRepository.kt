package mg.univ.fahasalamana.data.repository

import kotlinx.coroutines.flow.Flow
import mg.univ.fahasalamana.data.local.EnfantAvecVaccins
import mg.univ.fahasalamana.domain.CarnetExport
import mg.univ.fahasalamana.domain.Enfant
import mg.univ.fahasalamana.domain.ResultatImport
import mg.univ.fahasalamana.domain.VaccinAdministre
import java.time.LocalDate

/**
 * Les enfants du carnet et leurs doses reçues (§B6).
 *
 * **Données personnelles de santé** : ce repository est le seul chemin d'écriture vers les
 * tables `enfants` et `vaccins_administres`. Rien de ce qu'il lit ne part sur le réseau ;
 * la seule sortie prévue est l'export explicite via le SAF (B16, règle 7 de CLAUDE.md).
 *
 * Deux conventions, communes aux trois repositories du projet :
 * - **lecture en `Flow`**, Room restant la source de vérité : une saisie enregistrée
 *   depuis un autre écran se voit sans rafraîchissement manuel (US-B2, dernier scénario) ;
 * - **écriture en `suspend`**, jamais sur le fil principal.
 *
 * Les lectures renvoient [EnfantAvecVaccins] — l'enfant **et** ses doses en une seule
 * émission — et non deux flux séparés : le `CalculateurEcheancier` a besoin des deux pour
 * produire un échéancier, et les lire séparément ferait clignoter l'écran entre les deux
 * émissions. La conversion vers les types de `domain` (`toDomain()`) se fait côté
 * ViewModel, comme dans l'exemple du §B6.
 *
 * Contrat partagé avec Dev B (`FicheEnfantViewModel`, B08) : **ne pas modifier ces
 * signatures sans le prévenir.**
 */
interface EnfantRepository {

    /**
     * Tous les enfants du carnet, avec leurs doses reçues.
     *
     * Ordre renvoyé : prénom croissant (ordre stable décidé en SQL par `EnfantDao`). Le
     * tri par nombre de retards de l'écran « Mes enfants » (US-B8) se fait plus haut, à
     * partir des `ResumeEnfant` calculés par le domaine : la base ne connaît pas les statuts.
     */
    fun observerTous(): Flow<List<EnfantAvecVaccins>>

    /** Un enfant et ses doses. Émet `null` si l'enfant vient d'être supprimé : l'écran affiche « Introuvable » (§B6). */
    fun observer(id: String): Flow<EnfantAvecVaccins?>

    /**
     * Crée ou met à jour un enfant, selon que son identifiant existe déjà (B07).
     *
     * L'identifiant est un UUID fixé par l'appelant (`nouvelIdentifiant()`), jamais
     * renuméroté : c'est lui qui permet la fusion par identifiant à l'import (B17).
     */
    suspend fun enregistrer(enfant: Enfant)

    /** Supprime l'enfant **et ses doses reçues**, par la cascade déclarée sur `vaccins_administres` (US-B1, scénario « suppression »). */
    suspend fun supprimer(id: String)

    /**
     * Enregistre une dose reçue, en création comme en correction (B09).
     *
     * Une seule dose par couple (enfant, vaccin), règle R5 : une deuxième saisie pour le
     * même couple remplace la précédente au lieu de créer un doublon.
     */
    suspend fun enregistrerAdministration(v: VaccinAdministre)

    /** Supprime une dose saisie par erreur (US-B4). */
    suspend fun supprimerAdministration(id: String)

    /**
     * Tout le carnet de ce téléphone, sous la forme écrite dans le fichier d'export
     * (US-B9, scénario 1).
     *
     * **Une lecture, rien d'autre** : ni écriture de fichier, ni choix d'emplacement, ni
     * message. L'écriture dans le document désigné par l'utilisateur est faite par
     * `platform/ExportCarnetSaf.kt`, à partir du texte produit par `ecrireCarnet()`.
     * C'est ce découpage qui permet de tester le format en JVM sans Android.
     *
     * @param jour jour de l'export, repris tel quel dans [CarnetExport.exporteLe] **et**
     *   dans le nom du fichier proposé (`nomFichierCarnet`), pour que les deux ne puissent
     *   pas désigner deux jours différents.
     *
     *   Ce paramètre est un **écart assumé au §B6**, qui écrit `exporter(): CarnetExport`.
     *   Le jour courant vient de l'horloge injectée (`platform/HorlogeJour.kt`) partout
     *   dans le projet, jamais d'un `LocalDate.now()` enfoui dans une couche basse :
     *   sans lui, ce repository lirait l'heure système, et l'export deviendrait le seul
     *   endroit du code où la date du jour n'est ni injectée ni testable.
     */
    suspend fun exporter(jour: LocalDate): CarnetExport

    /**
     * Fusionne un carnet importé avec celui de ce téléphone (US-B9, scénario 2 ; §B6).
     *
     * **Le carnet reçu est déjà validé** : c'est `analyserCarnet()` qui décide qu'un texte
     * est un carnet lisible d'un format connu, et elle le fait avant tout appel ici. Un
     * fichier illisible ou d'une version inconnue n'atteint donc jamais la base.
     *
     * **La fusion n'écrase rien** : elle n'ajoute que les enfants et les doses absents de
     * ce téléphone, reconnus par leur identifiant (et, pour une dose, par le couple
     * (enfant, vaccin) de la règle R5). La décision et ses raisons sont écrites en tête de
     * `domain/ImportCarnet.kt` ; la règle elle-même y vit, sous forme de fonction pure, ce
     * repository ne faisant que lire l'état courant, appliquer le plan et rendre le rapport.
     *
     * **Ne programme aucun rappel** : l'appel à `PlanificateurRappels.replanifierTout()`
     * (§B8) appartient à l'appelant, comme pour tous les autres déclencheurs de B12 — un
     * repository ne connaît pas WorkManager.
     *
     * @return le rapport à afficher au parent : ajoutés, fusionnés, ignorés.
     */
    suspend fun importer(carnet: CarnetExport): ResultatImport
}
