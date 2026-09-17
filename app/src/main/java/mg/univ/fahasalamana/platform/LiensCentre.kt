package mg.univ.fahasalamana.platform

/*
 * ---------------------------------------------------------------------------
 * LIEN `tel:` D'UNE FICHE DE CENTRE (tâche B14, US-B6 scénarios 2 et 3)
 * ---------------------------------------------------------------------------
 *
 * Une fonction, et rien d'autre que des chaînes de caractères : le numéro composable d'un
 * centre, tel qu'`ACTION_DIAL` l'attend.
 *
 * **Aucun import Android dans ce fichier**, volontairement. C'est ce qui permet de le
 * tester en JVM (`LiensCentreTest`) sans émulateur, alors que tout le reste de `platform/`
 * demande un appareil. L'`Intent` qui porte cette URI et son lancement vivent à côté, dans
 * `ActionsCentre.kt`, qui est du code Android et n'est pas testable ici.
 *
 * Hors ligne par construction (§B1) : cette fonction ne joint rien, elle écrit une chaîne.
 * C'est le composeur du téléphone qui fait le travail, et c'est l'argument du CDC — aucune
 * permission d'appel, aucun octet de réseau pour joindre un centre.
 */

/**
 * URI `tel:` du composeur, ou `null` si [telephone] ne contient aucun chiffre.
 *
 * L'annuaire publie les numéros avec leurs espaces (« +261 34 00 000 00 ») parce qu'ils
 * sont affichés tels quels (voir `domain/Annuaire.kt`). Une URI, elle, n'accepte pas
 * l'espace : il est retiré ici, au dernier moment, et le modèle reste intact.
 *
 * Le résultat est destiné à `Intent.ACTION_DIAL`, jamais à `ACTION_CALL` : on ouvre le
 * composeur avec le numéro déjà saisi, et c'est le parent qui décide d'appeler.
 * `ACTION_CALL` exigerait la permission `CALL_PHONE`, que le CDC ne prévoit pas, et
 * déclencherait un appel sans confirmation depuis une application de santé.
 */
fun uriTelephone(telephone: String): String? {
    val numero = numeroComposable(telephone)
    return if (numero.isEmpty()) null else "tel:" + numero
}

/**
 * Ne garde d'un numéro publié que ce qu'un composeur sait lire : les chiffres, et le `+`
 * du préfixe international s'il est en tête.
 *
 * Renvoie une chaîne vide s'il ne reste aucun chiffre — champ vide dans l'annuaire, ou
 * mention du genre « — » : il n'y a alors rien à composer, et [uriTelephone] renvoie
 * `null` plutôt qu'un `tel:` creux qui ouvrirait un composeur vide.
 *
 * `in '0'..'9'` et non `isDigit()` : ce dernier accepte aussi les chiffres arabo-indiens
 * et devanagari, qu'aucun composeur ne sait interpréter dans une URI `tel:`.
 *
 * Privée : elle n'a de sens qu'assemblée, et les tests la couvrent à travers [uriTelephone].
 */
private fun numeroComposable(telephone: String): String {
    val brut = telephone.trim()
    val sortie = StringBuilder(brut.length)
    for ((position, caractere) in brut.withIndex()) {
        when {
            caractere in '0'..'9' -> sortie.append(caractere)
            // Le `+` n'a de sens qu'en tête (RFC 3966). Au milieu d'un numéro, c'est une
            // coquille de saisie dans l'annuaire : on le laisse tomber.
            caractere == '+' && position == 0 -> sortie.append(caractere)
            // Espaces, points, tirets, parenthèses, lettres : séparateurs visuels ou bruit.
            else -> Unit
        }
    }
    return if (sortie.any { it in '0'..'9' }) sortie.toString() else ""
}
