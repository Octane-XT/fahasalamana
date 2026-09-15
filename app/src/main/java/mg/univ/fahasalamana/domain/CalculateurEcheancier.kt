package mg.univ.fahasalamana.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Cœur métier de Fahasalamana Zaza : transforme une date de naissance, un
 * calendrier de référence et des doses saisies en un échéancier daté et qualifié.
 *
 * Fonction **pure** : aucune dépendance Android, aucune horloge implicite. La
 * date du jour et l'instant courant sont des paramètres, ce qui rend chaque
 * règle testable en JVM sans émulateur et rend l'affichage reproductible (la
 * fiche enfant fournit sa propre `horlogeJour`, qui émet à minuit et au retour
 * au premier plan).
 *
 * Règles couvertes : R1 (date prévue et chaînage des doses), R2 (statut),
 * R3 et R4 (rappels), R6 (résumé). Le ton des libellés (R7) relève de
 * `strings.xml` : cette classe ne produit aucun texte.
 */
class CalculateurEcheancier {

    /**
     * Échéancier complet de l'enfant, une ligne par dose du [calendrier], triée par
     * [VaccinReference.ordre].
     *
     * Les administrations d'un autre enfant sont ignorées ; une dose administrée
     * qui ne figure plus au [calendrier] ne crée pas de ligne, mais continue
     * d'ancrer la chaîne des doses qui en dépendent (R1).
     *
     * @param aujourdHui date de référence pour les statuts, jamais lue d'une horloge interne.
     */
    fun echeancier(
        enfant: Enfant,
        calendrier: List<VaccinReference>,
        administres: List<VaccinAdministre>,
        aujourdHui: LocalDate,
    ): List<LigneEcheancier> {
        val parId = calendrier.associateBy(VaccinReference::id)
        val datesFaites = datesFaites(enfant, administres)
        val cache = HashMap<String, LocalDate?>()

        return calendrier.sortedBy(VaccinReference::ordre).map { vaccin ->
            val prevuLe = datePrevue(vaccin, enfant.dateNaissance, parId, datesFaites, cache, HashSet())
            LigneEcheancier(
                vaccin = vaccin,
                prevuLe = prevuLe,
                statut = statut(vaccin, prevuLe, datesFaites, aujourdHui),
            )
        }
    }

    /**
     * Synthèse d'un échéancier déjà calculé (R6) : badges de la fiche et tri de la liste.
     *
     * [ResumeEnfant.prochaineEcheance] est la date de la **prochaine dose à faire**, tous
     * statuts non faits confondus. Ne retenir que les dates encore à venir produisait deux
     * lignes contradictoires côte à côte sur la même carte — « 3 en retard » et « Prochain :
     * aucune échéance à venir » — dès qu'un enfant n'avait plus que du retard. Le wireframe
     * §B7.2 montre l'inverse : « 1 en retard · 1 à faire / prochain : RR1 le 28/09 ».
     */
    fun resume(echeancier: List<LigneEcheancier>): ResumeEnfant {
        var nbEnRetard = 0
        var nbAFaire = 0
        val datesNonFaites = mutableListOf<LocalDate>()

        echeancier.forEach { ligne ->
            when (ligne.statut) {
                // Reçue : elle ne compte dans aucun badge et n'est plus une échéance.
                is StatutVaccin.Fait -> return@forEach
                is StatutVaccin.AVenir, is StatutVaccin.EnAttente -> Unit
                is StatutVaccin.AFaire -> nbAFaire++
                is StatutVaccin.EnRetard -> nbEnRetard++
            }
            // Les quatre autres statuts sont autant de doses encore à faire : leur date
            // entre dans le calcul, qu'elle soit devant nous ou déjà dépassée. Une ligne
            // sans date calculable (chaîne de dépendances cassée) n'en donne aucune.
            ligne.prevuLe?.let(datesNonFaites::add)
        }

        return ResumeEnfant(
            nbEnRetard = nbEnRetard,
            nbAFaire = nbAFaire,
            prochaineEcheance = datesNonFaites.minOrNull(),
        )
    }

    /**
     * Rappels à enfiler pour cet échéancier (R3), triés par date d'émission.
     *
     * Le rappel principal n'est produit que pour une ligne [StatutVaccin.AVenir]
     * dont l'émission est encore devant nous : une dose déjà faite, déjà due ou
     * en retard n'en génère aucun. La fonction est donc idempotente — la rejouer
     * après chaque écriture (R4, politique `REPLACE`) ne crée pas de doublon.
     *
     * @param avecRappelFenetre produit en plus le second rappel « fenêtre bientôt
     *   fermée » (R3, Should) pour les lignes encore non faites. Désactivé par
     *   défaut : le planificateur doit alors suffixer son `uniqueWorkName`
     *   (cf. [Rappel]) sous peine d'écraser le rappel principal.
     */
    fun rappelsAProgrammer(
        echeancier: List<LigneEcheancier>,
        maintenant: LocalDateTime,
        avecRappelFenetre: Boolean = false,
    ): List<Rappel> {
        val rappels = mutableListOf<Rappel>()

        echeancier.forEach { ligne ->
            val prevuLe = ligne.prevuLe ?: return@forEach
            val statut = ligne.statut

            if (statut is StatutVaccin.AVenir) {
                val emission = prevuLe.minusDays(JOURS_AVANT_ECHEANCE).atTime(HEURE_RAPPEL)
                if (emission > maintenant) {
                    rappels += Rappel(ligne.vaccin.id, prevuLe, emission, TypeRappel.AVANT_ECHEANCE)
                }
            }

            if (avecRappelFenetre && (statut is StatutVaccin.AVenir || statut is StatutVaccin.AFaire)) {
                val emission = prevuLe
                    .plusDays(ligne.vaccin.toleranceJours.toLong() - JOURS_AVANT_FERMETURE)
                    .atTime(HEURE_RAPPEL)
                if (emission > maintenant) {
                    rappels += Rappel(ligne.vaccin.id, prevuLe, emission, TypeRappel.FENETRE_BIENTOT_FERMEE)
                }
            }
        }

        return rappels.sortedBy(Rappel::dateHeure)
    }

    /**
     * Dates retenues par vaccin pour cet enfant. La contrainte d'unicité
     * (enfant, vaccin) est tenue par la base (R5) ; si un doublon arrive malgré
     * tout — import d'un carnet abîmé —, la plus ancienne date l'emporte plutôt
     * que de faire échouer tout l'écran.
     */
    private fun datesFaites(enfant: Enfant, administres: List<VaccinAdministre>): Map<String, LocalDate> =
        administres
            .filter { it.enfantId == enfant.id }
            .groupBy(VaccinAdministre::vaccinId)
            .mapValues { (_, doses) -> doses.minOf(VaccinAdministre::date) }

    /**
     * Règle R1 — date prévue.
     *
     * Sans dépendance : `dateNaissance + ageJours`. Avec dépendance : on part de la
     * date **réelle** de la dose précédente si elle est faite, sinon de sa propre
     * date théorique, calculée récursivement le long de la chaîne.
     *
     * Renvoie `null` si la chaîne est cassée (dose attendue absente du calendrier
     * et jamais administrée) ou circulaire (donnée de référence invalide) : le
     * calcul ne lève jamais d'exception sur un `calendrier.json` malformé.
     */
    private fun datePrevue(
        vaccin: VaccinReference,
        naissance: LocalDate,
        parId: Map<String, VaccinReference>,
        datesFaites: Map<String, LocalDate>,
        cache: MutableMap<String, LocalDate?>,
        enCours: MutableSet<String>,
    ): LocalDate? {
        if (cache.containsKey(vaccin.id)) return cache[vaccin.id]
        if (!enCours.add(vaccin.id)) return null

        val dependDe = vaccin.dependDe
        val origine: LocalDate? = if (dependDe == null) {
            naissance
        } else {
            // Dose précédente faite : sa date réelle fait foi, même si elle a
            // disparu d'une version plus récente du calendrier.
            // Sinon : date théorique, calculée en remontant la chaîne.
            datesFaites[dependDe]
                ?: parId[dependDe]?.let { datePrevue(it, naissance, parId, datesFaites, cache, enCours) }
        }
        enCours.remove(vaccin.id)

        val resultat = origine?.plusDays(vaccin.ageJours.toLong())
        cache[vaccin.id] = resultat
        return resultat
    }

    /** Règles R1 et R2 — statut d'une dose à la date [aujourdHui]. */
    private fun statut(
        vaccin: VaccinReference,
        prevuLe: LocalDate?,
        datesFaites: Map<String, LocalDate>,
        aujourdHui: LocalDate,
    ): StatutVaccin {
        datesFaites[vaccin.id]?.let { return StatutVaccin.Fait(it) }

        val dependDe = vaccin.dependDe

        // Aucune date calculable : ne survient que sur une dose dépendante, une
        // dose sans dependDe se calculant toujours depuis la naissance.
        if (prevuLe == null) return StatutVaccin.EnAttente(dependDe.orEmpty())

        // R1 : en attente tant que la dose précédente n'est pas faite *et* que la
        // date théorique n'est pas atteinte. Passé cette date, on bascule en
        // À faire / En retard pour que le retard cumulé de la série reste visible.
        if (dependDe != null && !datesFaites.containsKey(dependDe) && aujourdHui < prevuLe) {
            return StatutVaccin.EnAttente(dependDe)
        }

        // R2 : à venir -> dans la fenêtre -> en retard.
        val finFenetre = prevuLe.plusDays(vaccin.toleranceJours.toLong())
        return when {
            aujourdHui < prevuLe ->
                StatutVaccin.AVenir(prevuLe, ChronoUnit.DAYS.between(aujourdHui, prevuLe))

            aujourdHui <= finFenetre ->
                StatutVaccin.AFaire(prevuLe, finFenetre)

            else ->
                StatutVaccin.EnRetard(prevuLe, ChronoUnit.DAYS.between(prevuLe, aujourdHui))
        }
    }

    companion object {
        /** R3 : le rappel principal part 3 jours avant la date prévue. */
        const val JOURS_AVANT_ECHEANCE = 3L

        /** R3 : le rappel « fenêtre bientôt fermée » part 2 jours avant la fin de la tolérance. */
        const val JOURS_AVANT_FERMETURE = 2L

        /** R3 : heure d'émission des rappels, fuseau `Indian/Antananarivo`. */
        val HEURE_RAPPEL: LocalTime = LocalTime.of(9, 0)
    }
}
