package mg.univ.fahasalamana.data.repository

import kotlinx.coroutines.flow.Flow
import mg.univ.fahasalamana.domain.Centre
import mg.univ.fahasalamana.domain.District
import mg.univ.fahasalamana.domain.Region

/**
 * L'annuaire des centres de santé de base, en lecture seule (§B6).
 *
 * Quatre lectures, aucune écriture : l'annuaire est du contenu publié, chargé depuis
 * `assets/` par `ReferenceRepository.chargerEmbarqueSiVide()` puis remplacé en bloc par
 * une mise à jour (B19). Ce repository ne connaît que la base — c'est exactement ce que
 * montre le schéma du §B6, où seul `ReferenceRepository` est relié aux assets.
 *
 * Les trois niveaux sont interrogés séparément parce que l'écran « Centres » (B13) les
 * parcourt séparément : une région, puis un district, puis ses centres. Charger les 402
 * centres d'un coup pour n'en afficher que quelques-uns serait du gaspillage.
 */
interface CentreRepository {

    /** Les 23 régions, triées par nom. Premier menu déroulant de l'écran « Centres ». */
    fun observerRegions(): Flow<List<Region>>

    /** Les districts d'une région, triés par nom. Liste vide si la région est inconnue. */
    fun observerDistricts(regionId: String): Flow<List<District>>

    /** Les centres d'un district, triés par nom. Liste vide si le district est inconnu : l'écran affiche son état vide. */
    fun observerCentres(districtId: String): Flow<List<Centre>>

    /**
     * Un centre par son identifiant, pour l'écran de détail (B14).
     *
     * Émet `null` si le centre a disparu d'une mise à jour de l'annuaire alors que l'écran
     * était ouvert : l'écran affiche alors « introuvable » plutôt que des champs vides.
     */
    fun observerCentre(id: String): Flow<Centre?>
}
