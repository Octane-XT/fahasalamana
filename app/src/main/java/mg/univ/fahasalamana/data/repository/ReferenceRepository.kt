package mg.univ.fahasalamana.data.repository

import kotlinx.coroutines.flow.Flow
import mg.univ.fahasalamana.domain.VaccinReference
import java.time.LocalDate

/**
 * Le calendrier vaccinal de référence (§B6).
 *
 * Room est la source de vérité : tout ce qui s'affiche vient d'un `Flow` de la base.
 * Les fichiers `assets/` et le réseau ne sont que deux façons d'alimenter cette base,
 * jamais une source de lecture directe pour un écran.
 *
 * **Ce repository ne touche jamais aux données personnelles.** Il n'écrit que dans les
 * quatre tables de contenu de référence, et uniquement par les fonctions transactionnelles
 * de `ReferenceDao` (règle 8 de CLAUDE.md, §B5.2).
 */
interface ReferenceRepository {

    /** Le calendrier complet, dans l'ordre publié. Émet à chaque remplacement du contenu. */
    fun observerCalendrier(): Flow<List<VaccinReference>>

    /** Provenance et version du calendrier en base, pour le bandeau de la fiche enfant (B08) et les réglages (B15). */
    fun observerInfosSource(): Flow<InfosSource>

    /**
     * Charge les contenus embarqués **si et seulement si** la base est vide.
     *
     * Appelée au démarrage (`App.onCreate`). Idempotente : au deuxième lancement elle ne
     * fait que deux `COUNT(*)`. Elle couvre le calendrier **et** l'annuaire : c'est ce
     * repository qui est branché sur `assets/` dans le schéma d'architecture du §B6,
     * `CentreRepository` n'y lisant que la base.
     */
    suspend fun chargerEmbarqueSiVide()

    /*
     * TODO(B19) — mise à jour depuis le réseau, tâche de Dev B.
     *
     * Signature prévue par le §B6, à ajouter ici telle quelle :
     *
     *     suspend fun mettreAJour(): ResultatSync
     *
     * Elle n'est pas déclarée tant que `ResultatSync` n'existe pas : ce type décrit le
     * résultat d'une synchronisation (contenu à jour, contenu remplacé avec les nouvelles
     * versions, échec réseau sans effet sur la base — US-B11) et sa forme relève de B19.
     * L'inventer ici pour que la signature compile obligerait Dev B à le refaire.
     *
     * Le reste est déjà en place pour B19 : les DTO de `data/remote` sont ceux du fichier
     * distant, `MappageReference` les aplatit, `ReferenceDao.remplacerCalendrier` /
     * `remplacerAnnuaire` remplacent en transaction et `PreferencesLocales` garde les
     * versions à comparer.
     */
}

/**
 * De quand date le contenu de référence affiché, et d'où il vient (§B5.1).
 *
 * Affiché dans « À propos des données » : la mention de source porte l'avertissement
 * « calendrier de démonstration, à valider auprès du Ministère de la Santé Publique »,
 * qui doit rester visible tant que la source officielle n'est pas intégrée.
 *
 * Type partagé avec Dev B (écran Réglages, B15) : sa forme est figée, ne pas la modifier
 * sans le prévenir.
 *
 * @param source mention de provenance publiée, affichée telle quelle.
 * @param publieLe jour de publication du contenu, pas celui de son chargement.
 * @param version entier strictement croissant du fichier publié (§B5.1).
 */
data class InfosSource(
    val source: String,
    val publieLe: LocalDate,
    val version: Int,
)
