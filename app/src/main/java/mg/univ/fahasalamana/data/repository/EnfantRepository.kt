package mg.univ.fahasalamana.data.repository

import kotlinx.coroutines.flow.Flow
import mg.univ.fahasalamana.data.local.EnfantAvecVaccins
import mg.univ.fahasalamana.domain.Enfant
import mg.univ.fahasalamana.domain.VaccinAdministre

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

    /*
     * TODO(B16) / TODO(B17) — export et import du carnet, tâches de Dev A.
     *
     * Signatures prévues par le §B6, à ajouter ici telles quelles :
     *
     *     suspend fun importer(carnet: CarnetExport): ResultatImport
     *     suspend fun exporter(): CarnetExport
     *
     * Elles ne sont pas déclarées tant que `CarnetExport` et `ResultatImport` n'existent
     * pas : ces deux types sont le format de fichier de l'export (B16) et le rapport de
     * fusion de l'import (B17), et leur forme relève de ces tâches. Les inventer ici pour
     * que la signature compile obligerait à les refaire — c'est le même choix que celui
     * fait en B04 pour `ReferenceRepository.mettreAJour()` et son `ResultatSync`.
     *
     * Le reste est déjà en place : `EnfantDao.lireToutAvecVaccins` lit tout le carnet en
     * une fois pour l'export, et `enregistrerTous` / `upsertTous` fusionnent par
     * identifiant à l'import.
     */
}
