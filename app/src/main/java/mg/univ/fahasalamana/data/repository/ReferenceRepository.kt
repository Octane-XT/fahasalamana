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

    /**
     * Vérifie s'il existe une version plus récente des contenus de référence publiés, et
     * l'installe le cas échéant (US-B11, §B5.1).
     *
     * Déclenchée par le seul bouton « Vérifier les mises à jour » de l'écran Réglages :
     * l'application ne vérifie **jamais** d'elle-même, ni au démarrage, ni en tâche de fond.
     * C'est une application hors ligne qui emprunte le réseau sur demande explicite, et un
     * contrôle automatique consommerait les données de quelqu'un qui ne l'a pas demandé.
     *
     * Le calendrier et l'annuaire sont traités **indépendamment** : l'un peut avoir une
     * nouvelle version et pas l'autre, et l'échec de l'un n'annule pas l'autre. [ResultatSync]
     * porte donc une issue par fichier.
     *
     * **Ne lève pas** (§B0, couche 2) : réseau absent, temps dépassé, JSON illisible,
     * `schemaVersion` inattendu sont des cas de [IssueMiseAJour], pas des exceptions. Et dans
     * chacun de ces cas la base reste exactement dans l'état où elle était — un contenu n'est
     * remplacé qu'une fois son fichier entièrement téléchargé et validé, et ce remplacement
     * est transactionnel.
     *
     * Comme partout dans ce repository, **aucune donnée personnelle n'est touchée** : ni
     * `enfants`, ni `vaccins_administres` ne sont lues, écrites ou supprimées ici, et rien
     * n'est envoyé sur le réseau — les deux requêtes sont des `GET` sans corps (§B8, point 1).
     *
     * L'appelant qui obtient `ResultatSync.calendrierRemplace` doit replanifier les rappels
     * (`PlanificateurRappels.replanifierTout()`, §B8) : un nouveau calendrier change les dates
     * prévues de tous les enfants. Ce n'est pas fait ici — un repository ne programme pas de
     * `WorkRequest` (règle 9 de CLAUDE.md).
     */
    suspend fun mettreAJour(): ResultatSync
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
