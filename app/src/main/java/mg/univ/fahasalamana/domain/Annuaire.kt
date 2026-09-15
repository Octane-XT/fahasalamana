package mg.univ.fahasalamana.domain

/*
 * ---------------------------------------------------------------------------
 * ANNUAIRE DES CENTRES DE SANTÉ DE BASE (§B5.1, §B6)
 * ---------------------------------------------------------------------------
 *
 * Les trois modèles renvoyés par `CentreRepository` (§B6). Contenu de référence :
 * publié, identique sur tous les appareils, remplacé en bloc par une mise à jour.
 * Aucune donnée personnelle ici — l'annuaire ne sait rien des enfants suivis.
 *
 * Écrits en B04 plutôt qu'en B05 (exception assumée à la règle « `domain/` appartient
 * à Dev B ») : `CentreRepository` les exige et ils étaient absents du paquet livré.
 * Ils sont regroupés dans un seul fichier parce qu'ils ne se lisent que comme une
 * hiérarchie : une région contient des districts, un district contient des centres.
 *
 * Hiérarchie **à plat**, et non imbriquée comme dans `csb.json` : l'écran « Centres »
 * (B13) choisit une région, puis un district, puis affiche les centres de ce district,
 * en trois requêtes distinctes. Charger les 402 centres pour n'en afficher que cinq
 * n'aurait pas de sens ; les clés étrangères de `data/local` portent la relation.
 */

/** Une région (23 dans l'annuaire livré). Premier niveau de choix de l'écran « Centres ». */
data class Region(
    val id: String,
    val nom: String,
)

/**
 * Un district sanitaire, rattaché à une région (76 dans l'annuaire livré).
 *
 * @param regionId identifiant de la région parente. Conservé alors que
 *   `observerDistricts(regionId)` filtre déjà dessus : il permet de remonter d'un
 *   district à sa région sans requête supplémentaire (fil d'Ariane, futur export).
 */
data class District(
    val id: String,
    val regionId: String,
    val nom: String,
)

/**
 * Un centre de santé de base (402 dans l'annuaire livré).
 *
 * @param type niveau du centre tel qu'il est publié (« CSB1 », « CSB2 », « CHRD »…).
 *   Volontairement un `String` et non une énumération : l'annuaire est du contenu
 *   publié qui peut introduire un nouveau niveau sans qu'une version de l'application
 *   sorte, et une valeur inconnue doit s'afficher telle quelle plutôt que faire
 *   échouer la lecture.
 * @param telephone numéro tel que publié, avec ses espaces : il est affiché à
 *   l'identique et passé à `ACTION_DIAL` (B14), jamais normalisé ici.
 * @param horaires texte libre publié, y compris les jours de vaccination.
 */
data class Centre(
    val id: String,
    val districtId: String,
    val nom: String,
    val type: String,
    val telephone: String,
    val horaires: String,
    val adresse: String,
)
