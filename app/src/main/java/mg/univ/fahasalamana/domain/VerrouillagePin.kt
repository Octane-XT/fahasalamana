package mg.univ.fahasalamana.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/*
 * Verrouillage par code — règle de protection n° 4 du §B8, user story US-B10, tâche B18.
 *
 * Trois décisions pures vivent ici, et rien d'autre (CLAUDE.md, règle 1) :
 *  1. **le format d'un code** est-il acceptable ([erreurDePin]) ;
 *  2. **le code saisi correspond-il** à l'empreinte enregistrée ([pinCorrespond]) ;
 *  3. **faut-il reverrouiller** au retour au premier plan ([fautIlReverrouiller]).
 *
 * Aucun accès Android, aucune horloge implicite, aucun texte : tout se teste en JVM
 * (`VerrouillagePinTest`). L'écran vit dans `ui/verrouillage/`, l'observation du cycle de
 * vie du processus dans `platform/GardienVerrouillage.kt`, le stockage dans
 * `data/local/PreferencesLocales.kt`. Seules `java.security` et `javax.crypto` sont
 * utilisées : ce sont des classes de la plateforme Java, présentes aussi bien sur la JVM
 * des tests que sur Android depuis l'API 26 (`minSdk` du projet).
 *
 * ---
 *
 * ## Pourquoi PBKDF2 et pas un SHA-256 salé
 *
 * Le §B8 écrit « hachage SHA-256 salé ». Le sel est indispensable et il est ici ; **un
 * SHA-256 simple ne l'est pas**, et c'est le seul écart assumé au CDC de cette tâche.
 *
 * Un code de 4 à 6 chiffres, ce sont 10 000 à 1 000 000 de valeurs possibles : l'espace
 * entier tient dans une liste. Un SHA-256 se calcule à plusieurs centaines de millions de
 * fois par seconde sur une carte graphique ordinaire ; qui récupère `pin_hash` et `pin_sel`
 * retrouve le code en une fraction de seconde, sel ou pas. Le sel ne sert qu'à empêcher de
 * réutiliser une table précalculée d'une installation à l'autre — il ne coûte rien à
 * l'attaquant sur un seul téléphone.
 *
 * Ce qu'il faut est une fonction **lente et paramétrable**. Trois candidates sérieuses :
 *
 * | Fonction | Disponibilité | Retenue ? |
 * |---|---|---|
 * | Argon2id | aucune implémentation dans la plateforme Android : dépendance native à ajouter | non — un projet universitaire hors ligne ne fait pas entrer une bibliothèque native pour un écran |
 * | scrypt | absente de `javax.crypto` sur Android | non, même raison |
 * | **PBKDF2-HMAC-SHA256** | `SecretKeyFactory`, plateforme Java, Android ≥ 26 | **oui** |
 *
 * PBKDF2 est le plus faible des trois sur le papier (il se parallélise bien sur GPU, là où
 * Argon2 et scrypt coûtent aussi de la mémoire), mais il est **déjà là**, il est un standard
 * (RFC 8018, recommandé par l'ANSSI et l'OWASP à défaut d'Argon2), et il se teste en JVM
 * sans émulateur. Aucune dépendance n'est ajoutée à `libs.versions.toml` pour B18.
 *
 * ## Ce que ce choix achète, et ce qu'il n'achète pas
 *
 * À [ITERATIONS_PBKDF2] itérations, une vérification coûte de l'ordre de 150 à 400 ms sur
 * un téléphone d'entrée de gamme. Conséquences, dans les deux sens :
 *
 * - **Sur l'appareil**, essayer les 10 000 codes à 4 chiffres demanderait des heures, et
 *   l'attaque par l'écran de saisie n'a aucun intérêt.
 * - **Hors de l'appareil**, avec le fichier DataStore en main et du matériel dédié, les
 *   10 000 codes restent énumérables. Le coût passe de « instantané » à « quelques heures de
 *   calcul », ce qui est une gêne, pas une garantie.
 *
 * Autrement dit : ce verrou **protège le carnet d'un membre du foyer qui prend le téléphone
 * en main**, ce qui est exactement le besoin de US-B10 (« téléphone partagé dans le foyer »).
 * Il ne protège de personne ayant accès aux fichiers de l'appareil, et **il ne chiffre
 * rien** : la base Room reste lisible telle quelle. Les textes des réglages le disent en
 * toutes lettres plutôt que de laisser croire à un chiffrement (`strings_verrouillage.xml`).
 *
 * ## Ce qui n'est jamais fait, nulle part
 *
 * Le code en clair n'est ni stocké, ni journalisé, ni exporté, ni envoyé sur le réseau. Il
 * n'existe que le temps d'une saisie, dans l'état du ViewModel, et le `toString()` de cet
 * état est masqué pour qu'aucune trace ne parte dans un rapport de plantage.
 */

/** Longueur minimale d'un code, en chiffres. */
const val LONGUEUR_PIN_MIN: Int = 4

/** Longueur maximale d'un code, en chiffres. */
const val LONGUEUR_PIN_MAX: Int = 6

/**
 * Temps passé hors de l'application au-delà duquel le carnet se reverrouille (§B8, point 4).
 *
 * Deux minutes, et **pas « à chaque bascule »** : consulter le SMS de rappel du centre de
 * santé au milieu d'une saisie ne doit pas obliger à retaper le code. C'est la valeur du
 * CDC ; elle vit ici pour être lue par un test plutôt que recopiée dans le `platform`.
 */
val DELAI_VERROUILLAGE: Duration = Duration.ofMinutes(2)

/**
 * Nombre d'itérations PBKDF2 pour un code créé aujourd'hui.
 *
 * Valeur **de compromis**, choisie pour la cible matérielle du projet (téléphone d'entrée de
 * gamme malgache, cf. contrainte de démarrage à froid du §B9) : au-delà, la vérification
 * devient perceptible à chaque ouverture du carnet, plusieurs fois par jour, pour un gain
 * marginal face à un espace de 10 000 codes. L'OWASP recommande davantage pour un mot de
 * passe de serveur ; la comparaison ne tient pas, le facteur limitant ici est l'entropie du
 * secret, pas le coût du hachage.
 *
 * Cette valeur n'est **pas** figée dans les données : elle est écrite dans l'empreinte
 * elle-même (voir [hacherPin]), donc l'augmenter plus tard ne rend aucun code existant
 * invérifiable — les anciens continuent d'être relus avec leur propre compte d'itérations.
 */
const val ITERATIONS_PBKDF2: Int = 120_000

/** Ce qui empêche un code d'être accepté. Des types, pas des phrases : les textes sont dans `strings_verrouillage.xml`. */
sealed interface ErreurPin {

    /** Aucun chiffre saisi. */
    data object Vide : ErreurPin

    /** Moins de [minimum] chiffres. */
    data class TropCourt(val minimum: Int) : ErreurPin

    /** Plus de [maximum] chiffres. */
    data class TropLong(val maximum: Int) : ErreurPin

    /**
     * Le code contient autre chose que des chiffres.
     *
     * L'écran filtre déjà la frappe et le clavier est numérique : ce cas ne devrait pas se
     * produire à la main. Il reste utile parce qu'il ferme la porte à un appel direct depuis
     * un test ou depuis un futur écran qui oublierait le filtre.
     */
    data object CaracteresInterdits : ErreurPin
}

/**
 * Ce qui est conservé dans DataStore pour un code donné : **jamais le code**.
 *
 * @param hash empreinte au format `pbkdf2-sha256$<itérations>$<empreinte en Base64>`,
 *   telle que produite par [hacherPin] et telle qu'elle est stockée sous la clé `pin_hash`.
 * @param sel sel aléatoire en Base64, stocké sous la clé `pin_sel`. Tiré une fois par code
 *   créé, donc propre à l'installation : deux téléphones avec le même code n'ont pas la
 *   même empreinte.
 */
data class EmpreintePin(val hash: String, val sel: String)

/**
 * Valide le format d'un code, sans rien savoir de celui qui est enregistré.
 *
 * @return `null` quand le code peut être utilisé, sinon la raison du refus.
 */
fun erreurDePin(code: String): ErreurPin? = when {
    code.isEmpty() -> ErreurPin.Vide
    !code.all { it in '0'..'9' } -> ErreurPin.CaracteresInterdits
    code.length < LONGUEUR_PIN_MIN -> ErreurPin.TropCourt(LONGUEUR_PIN_MIN)
    code.length > LONGUEUR_PIN_MAX -> ErreurPin.TropLong(LONGUEUR_PIN_MAX)
    else -> null
}

/** Raccourci de lecture : le code a un format acceptable. C'est ce qui active le bouton de validation. */
fun pinValide(code: String): Boolean = erreurDePin(code) == null

/**
 * Tire un sel neuf, en Base64.
 *
 * [TAILLE_SEL_OCTETS] octets de `SecureRandom` : un sel n'a pas à être secret, il a à être
 * unique. Le paramètre existe pour que les tests puissent fixer la graine et vérifier qu'un
 * même couple (code, sel) redonne la même empreinte.
 */
fun nouveauSel(aleatoire: SecureRandom = SecureRandom()): String {
    val octets = ByteArray(TAILLE_SEL_OCTETS)
    aleatoire.nextBytes(octets)
    return Base64.getEncoder().encodeToString(octets)
}

/**
 * Empreinte PBKDF2-HMAC-SHA256 de [code] avec [sel], au format
 * `pbkdf2-sha256$<itérations>$<empreinte en Base64>`.
 *
 * **Les paramètres voyagent avec l'empreinte.** C'est la convention des fichiers de mots de
 * passe Unix, et elle règle ici un vrai problème : le jour où [ITERATIONS_PBKDF2] est
 * augmenté, ou le jour où PBKDF2 est remplacé par Argon2, les codes déjà créés restent
 * vérifiables puisque chacun porte la recette qui l'a produit. Sans cela, il faudrait une
 * clé DataStore de plus et une migration — pour un utilisateur qui, lui, n'a aucun moyen de
 * redonner son code.
 *
 * Appel **coûteux par construction** (voir [ITERATIONS_PBKDF2]) : à n'appeler que hors du
 * fil principal.
 *
 * @throws IllegalArgumentException si [code] est vide ou [iterations] n'est pas positif.
 */
fun hacherPin(code: String, sel: String, iterations: Int = ITERATIONS_PBKDF2): String {
    require(code.isNotEmpty()) { "Un code vide n'a pas d'empreinte." }
    require(iterations > 0) { "Le nombre d'itérations doit être strictement positif." }
    val octetsDuSel = decoderBase64(sel)
    require(octetsDuSel != null) { "Sel illisible : il doit venir de nouveauSel()." }
    val empreinte = deriver(code, octetsDuSel, iterations)
    return listOf(
        ETIQUETTE_ALGORITHME,
        iterations.toString(),
        Base64.getEncoder().encodeToString(empreinte),
    ).joinToString(SEPARATEUR)
}

/**
 * Crée l'empreinte d'un code neuf : sel tiré au hasard, puis hachage.
 *
 * Point de passage unique entre un code choisi par l'utilisateur et ce qui part dans
 * DataStore ; c'est lui qui garantit qu'un sel n'est jamais réutilisé d'un code à l'autre.
 */
fun creerEmpreintePin(
    code: String,
    aleatoire: SecureRandom = SecureRandom(),
    iterations: Int = ITERATIONS_PBKDF2,
): EmpreintePin {
    val sel = nouveauSel(aleatoire)
    return EmpreintePin(hash = hacherPin(code, sel, iterations), sel = sel)
}

/**
 * Le code saisi correspond-il à l'empreinte enregistrée ?
 *
 * Le nombre d'itérations utilisé est celui **lu dans [EmpreintePin.hash]**, pas
 * [ITERATIONS_PBKDF2] : c'est ce qui permet de relire un code créé par une version
 * antérieure de l'application.
 *
 * La comparaison finale passe par `MessageDigest.isEqual`, qui parcourt les deux tableaux
 * en entier. Un `==` sur les chaînes s'arrêterait au premier octet différent, et la durée de
 * la réponse renseignerait sur le nombre d'octets déjà justes. Le gain est théorique sur un
 * téléphone, mais il coûte un appel de méthode.
 *
 * @return `false` — jamais d'exception — si l'empreinte stockée est illisible : un DataStore
 *   corrompu doit refuser l'ouverture, pas faire tomber l'application ni l'ouvrir.
 */
fun pinCorrespond(code: String, empreinte: EmpreintePin): Boolean {
    if (code.isEmpty()) return false
    val parametres = parametresDe(empreinte.hash) ?: return false
    val sel = decoderBase64(empreinte.sel) ?: return false
    val calcule = deriver(code, sel, parametres.iterations)
    return MessageDigest.isEqual(calcule, parametres.empreinte)
}

/**
 * Faut-il redemander le code au retour au premier plan ?
 *
 * Fonction de décision du §B8, point 4, isolée de `ProcessLifecycleOwner` pour être
 * testable : le `platform` se contente de lui passer l'instant de départ en arrière-plan et
 * l'instant présent.
 *
 * Trois cas répondent « oui » :
 * - **[dernierAcces] nul** : le processus n'a jamais vu l'application partir en arrière-plan.
 *   C'est un démarrage à froid, et le CDC verrouille les démarrages à froid (§B7.1 :
 *   `[*] --> Verrouillage : si PIN activé`).
 * - **[maintenant] antérieur à [dernierAcces]** : l'horloge du téléphone a reculé pendant
 *   l'absence. Reculer l'heure dans les réglages Android est un aller-retour en arrière-plan,
 *   c'est-à-dire précisément le geste que ce délai surveille ; une durée négative ne doit
 *   donc pas valoir « à l'instant ». Dans le doute sur l'horloge, on verrouille.
 * - **délai atteint** : la comparaison est faite avec `>=`, deux minutes pile reverrouillent.
 *
 * @param dernierAcces instant du dernier passage en arrière-plan, `null` si l'application
 *   n'y est pas encore allée dans ce processus.
 * @param maintenant instant présent, passé en paramètre et jamais lu ici : c'est ce qui rend
 *   la fonction pure (même principe que `aujourdHui` dans `ValidationSaisie.kt`).
 * @param delai durée de tolérance, [DELAI_VERROUILLAGE] par défaut.
 */
fun fautIlReverrouiller(
    dernierAcces: Instant?,
    maintenant: Instant,
    delai: Duration = DELAI_VERROUILLAGE,
): Boolean {
    if (dernierAcces == null) return true
    if (maintenant.isBefore(dernierAcces)) return true
    return Duration.between(dernierAcces, maintenant) >= delai
}

// --- Détails d'implémentation ------------------------------------------------

/** Nom JCE de la fonction de dérivation. Présent sur Android à partir de l'API 26 (`minSdk` du projet). */
private const val ALGORITHME = "PBKDF2WithHmacSHA256"

/** Étiquette écrite dans l'empreinte ; c'est elle qui permettra d'en reconnaître une autre plus tard. */
private const val ETIQUETTE_ALGORITHME = "pbkdf2-sha256"

/** Séparateur des trois champs de l'empreinte. Absent de l'alphabet Base64, donc sans ambiguïté. */
private const val SEPARATEUR = "\$"

/** 128 bits de sel : au-delà, on n'achète plus rien contre la réutilisation d'une table. */
private const val TAILLE_SEL_OCTETS = 16

/** Longueur de l'empreinte dérivée, alignée sur la sortie de SHA-256. */
private const val TAILLE_EMPREINTE_BITS = 256

/** Empreinte décodée et paramètres relus dans la chaîne stockée. */
private class ParametresEmpreinte(val iterations: Int, val empreinte: ByteArray)

/**
 * Relit `pbkdf2-sha256$<itérations>$<empreinte>`.
 *
 * @return `null` dès que quelque chose cloche — chaîne tronquée, algorithme inconnu,
 *   itérations non numériques, Base64 invalide. Un `null` fait échouer la vérification, ce
 *   qui est le comportement sûr.
 */
private fun parametresDe(hash: String): ParametresEmpreinte? {
    val champs = hash.split(SEPARATEUR)
    if (champs.size != 3) return null
    if (champs[0] != ETIQUETTE_ALGORITHME) return null
    val iterations = champs[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
    val empreinte = decoderBase64(champs[2]) ?: return null
    return ParametresEmpreinte(iterations = iterations, empreinte = empreinte)
}

/**
 * Base64 tolérant : `null` plutôt qu'une exception sur une valeur abîmée.
 *
 * Un résultat **vide** est traité comme illisible, et ce n'est pas du zèle : ni un sel ni une
 * empreinte ne peuvent légitimement être vides, et `PBEKeySpec` refuse un sel vide par une
 * exception. Sans ce garde-fou, un DataStore abîmé ferait remonter une exception depuis
 * [pinCorrespond], qui a promis de répondre `false`.
 */
private fun decoderBase64(valeur: String): ByteArray? = try {
    Base64.getDecoder().decode(valeur).takeIf { it.isNotEmpty() }
} catch (illisible: IllegalArgumentException) {
    null
}

/**
 * Dérivation proprement dite.
 *
 * `clearPassword()` dans un `finally` : `PBEKeySpec` garde une copie du code dans un tableau
 * de caractères, que le ramasse-miettes peut laisser traîner longtemps en mémoire. L'effacer
 * ne rend pas le code invisible pour autant — la chaîne venue du champ de saisie reste, elle,
 * hors de notre portée — mais il n'y a aucune raison d'ajouter une copie de plus.
 */
private fun deriver(code: String, sel: ByteArray, iterations: Int): ByteArray {
    val specification = PBEKeySpec(code.toCharArray(), sel, iterations, TAILLE_EMPREINTE_BITS)
    return try {
        SecretKeyFactory.getInstance(ALGORITHME).generateSecret(specification).encoded
    } finally {
        specification.clearPassword()
    }
}
