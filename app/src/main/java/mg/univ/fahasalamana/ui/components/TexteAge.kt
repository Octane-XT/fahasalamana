package mg.univ.fahasalamana.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import mg.univ.fahasalamana.R
import mg.univ.fahasalamana.domain.AgeEnfant

/**
 * Âge d'un enfant mis en mots : « 3 jours », « 8 mois », « 2 ans ».
 *
 * Seul endroit du projet qui écrit un âge. La liste des enfants (B06) et la fiche (B08)
 * l'appellent toutes les deux : le même enfant ne peut plus se lire « 1 an » d'un côté et
 * « 18 mois » de l'autre (revue statique du 15/09, point n° 9). Le choix de l'unité est
 * déjà fait par `domain.ageDepuis` ; il ne reste ici qu'à choisir la phrase.
 *
 * `when` sans `else` sur [AgeEnfant] : une unité de plus dans le domaine ferait échouer la
 * compilation ici, au lieu de laisser un âge muet à l'écran.
 */
@Composable
fun texteAge(age: AgeEnfant): String = when (age) {
    AgeEnfant.JourDeNaissance -> stringResource(R.string.age_jour_de_naissance)
    is AgeEnfant.Jours -> pluralStringResource(R.plurals.age_jours, age.jours, age.jours)
    is AgeEnfant.Semaines ->
        pluralStringResource(R.plurals.age_semaines, age.semaines, age.semaines)

    is AgeEnfant.Mois -> pluralStringResource(R.plurals.age_mois, age.mois, age.mois)
    is AgeEnfant.Annees -> pluralStringResource(R.plurals.age_ans, age.annees, age.annees)
}
