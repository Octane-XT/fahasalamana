package mg.univ.fahasalamana.ui.components

/**
 * Étiquettes posées par `Modifier.testTag` sur les nœuds que les tests d'interface (B21)
 * doivent désigner sans ambiguïté.
 *
 * **Une étiquette de test n'est pas une information d'accessibilité.** `testTag` n'est pas
 * annoncé par TalkBack et ne remplace jamais une `contentDescription`, un libellé écrit ni
 * un `onClickLabel` : ce qui est posé ici ne change rien à ce qu'entend l'utilisateur. Le
 * but est l'inverse — qu'un test cesse de dépendre d'un texte affiché, pour qu'un libellé
 * réécrit pour des raisons éditoriales (règle R7) ne casse pas la recette.
 *
 * Volontairement peu nombreuses : seulement les nœuds que B21 ne pouvait pas atteindre
 * autrement — défilement dirigé vers une ligne précise, matcher rendu ambigu par des libellés
 * répétés (« Ajouter un enfant » sur le bouton flottant *et* sur l'état vide), et statut
 * d'une ligne d'échéancier, qui n'était vérifiable que par son libellé.
 *
 * Les valeurs sont figées : un test les cite en dur ou par ces constantes, jamais les deux.
 */
object EtiquettesTest {

    // --- Mes enfants (B06) ---------------------------------------------------

    /**
     * Bouton d'ajout d'un enfant.
     *
     * **La même étiquette est posée deux fois** : sur le `FloatingActionButton` de la liste
     * remplie et sur le bouton de l'état vide. Un test qui ajoute un enfant n'a donc pas à
     * savoir dans quel état se trouve l'écran — c'est exactement ce que les deux boutons
     * font, et ils portent déjà le même libellé à l'écran.
     */
    const val AJOUTER_ENFANT: String = "enfants_ajouter"

    /** Carte d'un enfant dans la liste, identifiée par l'identifiant de l'enfant. */
    fun carteEnfant(enfantId: String): String = "enfants_carte_$enfantId"

    // --- Fiche enfant (B08) --------------------------------------------------

    /** La `LazyColumn` de l'échéancier : permet `performScrollToNode` au lieu de glissements. */
    const val FICHE_ECHEANCIER: String = "fiche_echeancier"

    /**
     * Une ligne de l'échéancier, identifiée par l'identifiant du vaccin.
     *
     * Le **statut** de la ligne se vérifie sur ce même nœud : la pastille porte le libellé
     * du statut en `contentDescription`, qui remonte dans la sémantique fusionnée de la
     * ligne. Un test écrit donc `assertContentDescriptionContains("Fait")` sur cette
     * étiquette, sans dépendre du texte affiché ni de la couleur de la pastille.
     */
    fun ligneVaccin(vaccinId: String): String = "fiche_ligne_$vaccinId"

    // --- Édition enfant (B07) ------------------------------------------------

    const val EDITION_PRENOM: String = "edition_prenom"

    /** Le champ de date entier, qui ouvre le sélecteur au toucher. */
    const val EDITION_DATE_NAISSANCE: String = "edition_date_naissance"

    /** Bouton de validation du formulaire : « Ajouter l'enfant » ou « Enregistrer les modifications ». */
    const val EDITION_VALIDER: String = "edition_valider"

    // --- Saisie d'un vaccin (B09) --------------------------------------------

    /** Bouton calendrier du champ de date, qui ouvre le sélecteur. */
    const val SAISIE_CHOISIR_DATE: String = "saisie_choisir_date"

    const val SAISIE_ENREGISTRER: String = "saisie_enregistrer"
}
