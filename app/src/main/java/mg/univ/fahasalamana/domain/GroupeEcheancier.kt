package mg.univ.fahasalamana.domain

/**
 * Un bloc de l'échéancier affiché sous un même en-tête d'âge (CDC §B7.2 : « Naissance »,
 * « 6 semaines », « 9 mois »…).
 *
 * @param ageJours âge **théorique** de la tranche, en jours depuis la naissance. `null`
 *   quand aucun âge n'est calculable : la dose dont celle-ci dépend a disparu du
 *   calendrier de référence. Ces lignes forment un dernier groupe à part.
 * @param lignes les doses de la tranche, dans l'ordre de publication du calendrier
 *   ([VaccinReference.ordre]).
 */
data class GroupeEcheancier(
    val ageJours: Int?,
    val lignes: List<LigneEcheancier>,
)

/**
 * Regroupe un échéancier par tranche d'âge, pour l'affichage en sections de la fiche enfant.
 *
 * Fonction **pure** (aucune dépendance Android), au même titre que [CalculateurEcheancier] :
 * c'est du calcul, il n'a donc sa place ni dans un ViewModel ni dans un composable.
 *
 * L'âge d'une tranche se lit **dans le calendrier seul**, jamais dans les dates réellement
 * saisies : une dose enchaînée (`dependDe`) compte son délai après l'âge théorique de la dose
 * dont elle dépend. C'est volontaire et c'est la différence avec la règle R1 — si l'on
 * groupait sur [LigneEcheancier.prevuLe], une dose administrée avec deux mois de retard ferait
 * sauter toutes les suivantes dans une autre section, et la fiche se réorganiserait sous les
 * yeux du parent à chaque saisie. Ici, les sections sont stables : seuls les statuts changent.
 *
 * Conséquence assumée : une dose dont la dose précédente a été retirée du calendrier a bien une
 * date prévue (R1 la calcule depuis la date réelle de cette dose si elle a été administrée) mais
 * pas d'âge théorique. Elle atterrit dans le groupe `ageJours == null`, en fin de liste.
 *
 * Ni le calendrier vide, ni une chaîne `dependDe` circulaire ou cassée ne lèvent d'exception :
 * un `calendrier.json` malformé dégrade l'affichage, il ne fait pas tomber l'écran.
 *
 * @param echeancier sortie de [CalculateurEcheancier.echeancier], déjà triée par
 *   [VaccinReference.ordre] ; c'est cette liste qui porte le calendrier de référence.
 * @return les groupes du plus jeune au plus âgé, le groupe sans âge calculable en dernier.
 */
fun grouperParAge(echeancier: List<LigneEcheancier>): List<GroupeEcheancier> {
    val parId = echeancier.associate { it.vaccin.id to it.vaccin }
    val cache = HashMap<String, Int?>()

    return echeancier
        // `groupBy` conserve l'ordre d'arrivée à l'intérieur de chaque groupe : les lignes
        // restent triées par `ordre`, comme les a produites le calculateur.
        .groupBy { ligne -> ageTheorique(ligne.vaccin, parId, cache, HashSet()) }
        .map { (ageJours, lignes) -> GroupeEcheancier(ageJours, lignes) }
        .sortedWith(compareBy(nullsLast<Int>()) { it.ageJours })
}

/**
 * Nombre de doses saisies comme faites, pour le bandeau de résumé de la fiche (« 12 faits »).
 *
 * Complète [ResumeEnfant] (règle R6), qui compte les retards et les doses à faire mais pas
 * celles déjà reçues : ce type est partagé avec l'écran « Mes enfants » et n'est pas modifié ici.
 */
fun nbFaits(echeancier: List<LigneEcheancier>): Int =
    echeancier.count { it.statut is StatutVaccin.Fait }

/**
 * Âge théorique d'une dose, en jours depuis la naissance, calculé en remontant la chaîne
 * `dependDe` dans le calendrier.
 *
 * Même forme que le calcul de date prévue de [CalculateurEcheancier] — mémoïsation et garde
 * contre les cycles — mais sans aucune administration : c'est une propriété du calendrier,
 * pas de l'enfant.
 *
 * @return `null` si la chaîne est cassée (dose attendue absente du calendrier) ou circulaire.
 */
private fun ageTheorique(
    vaccin: VaccinReference,
    parId: Map<String, VaccinReference>,
    cache: MutableMap<String, Int?>,
    enCours: MutableSet<String>,
): Int? {
    if (cache.containsKey(vaccin.id)) return cache[vaccin.id]
    if (!enCours.add(vaccin.id)) return null

    val dependDe = vaccin.dependDe
    val origine: Int? = if (dependDe == null) {
        0
    } else {
        parId[dependDe]?.let { ageTheorique(it, parId, cache, enCours) }
    }
    enCours.remove(vaccin.id)

    val resultat = origine?.plus(vaccin.ageJours)
    cache[vaccin.id] = resultat
    return resultat
}
