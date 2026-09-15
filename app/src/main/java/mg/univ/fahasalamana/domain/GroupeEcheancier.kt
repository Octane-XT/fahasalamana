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
 * Doses saisies pour cet enfant dont le vaccin ne figure dans **aucune ligne** du [calendrier].
 *
 * C'est la contrepartie d'affichage de la séparation « contenu de référence / données
 * personnelles » (CDC §B5.2) : une mise à jour du calendrier (B19) remplace `vaccins_reference`
 * en bloc, sans jamais toucher à `vaccins_administres`, et la clé étrangère a été retirée
 * exprès pour que la dose survive à la disparition de sa référence. Le CDC exige qu'elle soit
 * « conservée **et** affichée » : sans cette fonction, une dose correctement saisie par une mère
 * disparaîtrait de la fiche de son enfant du jour au lendemain, sans explication.
 *
 * [CalculateurEcheancier.echeancier] ne peut pas la produire : il parcourt le calendrier, et une
 * dose absente du calendrier n'a plus ni nom, ni dose, ni date prévue à afficher. Il ne reste
 * que ce que le parent a lui-même saisi — une date —, et c'est tout ce que la fiche montre.
 *
 * Fonction **pure**, comme [grouperParAge] : c'est du calcul, pas de l'affichage.
 *
 * Deux gardes reprises de [CalculateurEcheancier] :
 * - les doses d'un autre enfant sont ignorées (donnée de santé : la fiche de Faly ne montre
 *   jamais une ligne de Soa, même si l'appelant se trompe de liste) ;
 * - si un carnet importé (B17) contient deux saisies pour le même vaccin, la plus ancienne
 *   date l'emporte et la dose n'apparaît qu'une fois, comme dans l'échéancier.
 *
 * @return les doses concernées, de la plus ancienne à la plus récente ; liste vide dans le cas
 *   normal, où tout ce qui a été saisi figure encore au calendrier.
 */
fun dosesHorsCalendrier(
    enfant: Enfant,
    administres: List<VaccinAdministre>,
    calendrier: List<VaccinReference>,
): List<VaccinAdministre> {
    val idsDuCalendrier = calendrier.mapTo(HashSet(), VaccinReference::id)

    return administres
        .filter { it.enfantId == enfant.id && it.vaccinId !in idsDuCalendrier }
        .groupBy(VaccinAdministre::vaccinId)
        .map { (_, doses) -> doses.minBy(VaccinAdministre::date) }
        // `vaccinId` départage deux doses de même date : l'ordre d'affichage ne doit pas
        // dépendre de l'ordre de lecture de la base.
        .sortedWith(compareBy<VaccinAdministre>({ it.date }, { it.vaccinId }))
}

/**
 * Nombre de doses que l'enfant a **réellement reçues**, pour le bandeau de résumé de la
 * fiche (« 12 faits »).
 *
 * Complète [ResumeEnfant] (règle R6), qui compte les retards et les doses à faire mais pas
 * celles déjà reçues : ce type est partagé avec l'écran « Mes enfants » et n'est pas modifié ici.
 *
 * Les lignes [StatutVaccin.Fait] de l'échéancier ne suffisent pas : elles s'arrêtent au
 * calendrier. Une dose qui en est sortie ([dosesHorsCalendrier]) a bien été reçue et doit
 * continuer de compter, sinon le compteur baisserait tout seul après une mise à jour des
 * références (B19) — ce que le parent lirait comme une dose perdue.
 *
 * @param horsCalendrier sortie de [dosesHorsCalendrier] pour le même enfant et le même
 *   calendrier que [echeancier] : les deux listes sont disjointes par construction, aucune
 *   dose n'est comptée deux fois.
 */
fun nbFaits(echeancier: List<LigneEcheancier>, horsCalendrier: List<VaccinAdministre>): Int =
    echeancier.count { it.statut is StatutVaccin.Fait } + horsCalendrier.size

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
