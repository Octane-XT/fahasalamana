package mg.univ.fahasalamana.data.repository

import java.time.LocalDate

/*
 * ---------------------------------------------------------------------------
 * CE QUE RAPPORTE UNE VÉRIFICATION DES MISES À JOUR (B19, US-B11)
 * ---------------------------------------------------------------------------
 *
 * Le §B6 nomme `suspend fun mettreAJour(): ResultatSync` sans décrire le type ; le §B0
 * (couche 2) dit ce qu'il doit porter : « un résultat typé (succès / pas de nouvelle
 * version / erreur réseau / format invalide) **plutôt que des exceptions remontées à
 * l'interface** ». Ce fichier est ce type.
 *
 * TROIS DÉCISIONS, ÉCRITES AVANT LE CODE
 * --------------------------------------
 *
 * 1. **Deux issues et non une.** Le calendrier et l'annuaire sont deux fichiers publiés
 *    séparément, avec chacun son `version` (§B5.1). L'un peut avoir une nouvelle version et
 *    pas l'autre ; l'un peut échouer et pas l'autre. Un résultat unique obligerait à choisir
 *    laquelle des deux histoires raconter, et masquerait la moitié de ce qui s'est passé —
 *    typiquement « échec » alors que l'annuaire vient bel et bien d'être mis à jour.
 *
 * 2. **« Vous êtes à jour » n'est pas une erreur.** Le contrat dit que `version` est un
 *    entier strictement croissant et que le contenu local n'est remplacé que si
 *    `distant.version > local.version`. Une version distante égale ou inférieure est le cas
 *    **normal** : c'est ce qui arrive à chaque vérification entre deux publications.
 *    [IssueMiseAJour.DejaAJour] est donc un succès, pas un repli.
 *
 * 3. **Aucune exception ne traverse ce type.** Coupure réseau, JSON tronqué, HTTP 404,
 *    serveur qui répond une page HTML, `schemaVersion` inattendu : tout est rangé dans un
 *    cas. L'écran des réglages n'a donc aucun `try` à écrire, et — c'est le point de la
 *    Definition of Done — **chacun de ces cas laisse la base exactement dans l'état où elle
 *    était**, parce qu'aucun d'eux ne se produit après le début d'un remplacement (voir
 *    `SynchroniseurReference`).
 */

/**
 * Ce qu'est devenu **un** des deux contenus de référence après une vérification.
 *
 * Cinq cas, dont deux succès. La distinction entre [Echec] et [SchemaInconnu] tient à la
 * seule chose qui compte pour l'utilisateur : ce qu'il peut y faire. Un échec se retente
 * plus tard ; un schéma inconnu veut dire que c'est l'**application** qui est trop ancienne,
 * et aucun nombre de nouvelles tentatives n'y changera quoi que ce soit.
 */
sealed interface IssueMiseAJour {

    /**
     * Le fichier publié a été lu et sa version n'est pas plus récente que celle en base :
     * il n'y avait rien à remplacer (§B5.1).
     *
     * Couvre aussi bien `distant.version == local.version` (le cas courant) que
     * `distant.version < local.version` (une publication retirée, un miroir en retard). Les
     * deux se disent de la même façon au parent — « vous êtes à jour » — et surtout les deux
     * se traitent pareil : on ne touche à rien. Remplacer un contenu par une version
     * *antérieure* violerait la règle du §B5.1 aussi sûrement qu'un remplacement sauvage.
     *
     * @param version la version en base, inchangée.
     */
    data class DejaAJour(val version: Int) : IssueMiseAJour

    /**
     * Une version plus récente a été téléchargée, validée, puis substituée à l'ancienne en
     * une seule transaction.
     *
     * @param versionPrecedente la version qui était en base avant ce remplacement, ou
     *   `PreferencesLocales.VERSION_ABSENTE` (0) si aucun contenu n'avait encore été chargé.
     * @param version la version désormais en base.
     * @param publieLe date de publication du fichier retenu (§B5.1), telle qu'affichée dans
     *   « À propos des données ».
     */
    data class Remplace(
        val versionPrecedente: Int,
        val version: Int,
        val publieLe: LocalDate,
    ) : IssueMiseAJour

    /**
     * Le fichier n'a pas pu être obtenu, ou pas pu être enregistré. **Rien n'a changé.**
     *
     * Un seul cas pour plusieurs causes — réseau absent, connexion coupée en cours de
     * route, temps dépassé, réponse HTTP en erreur (404 tant que le dépôt de données n'est
     * pas créé), écriture locale impossible — parce qu'elles appellent toutes exactement la
     * même chose de la part du parent : réessayer plus tard. Les distinguer à l'écran
     * donnerait quatre messages pour une seule action possible.
     *
     * La cause technique n'est volontairement **pas** transportée : un message système ou
     * une trace d'exception n'a rien à faire dans l'interface d'une application de santé
     * (§B8). Elle part dans logcat, en build debug, par l'intercepteur OkHttp.
     */
    data object Echec : IssueMiseAJour

    /**
     * Le fichier a été reçu entièrement mais n'est pas lisible : JSON invalide, champ
     * obligatoire absent, date hors format ISO, ou page HTML servie à la place du fichier.
     * **Rien n'a changé.**
     *
     * Distingué d'[Echec] parce qu'il ne dit pas la même chose : le réseau fonctionne, c'est
     * le contenu publié qui est en cause. Réessayer dans cinq minutes ne servira à rien tant
     * que le fichier n'est pas republié — et c'est une information utile en soutenance comme
     * pour celui qui publie les données.
     */
    data object FormatInvalide : IssueMiseAJour

    /**
     * Le fichier annonce un `schemaVersion` que cette version de l'application ne sait pas
     * lire (§B5.1). **Rien n'a changé.**
     *
     * Le cas ne devrait pas se produire — une évolution incompatible du format publie sous
     * `v2/` et laisse `v1/` en place, précisément pour que les APK déjà installés continuent
     * de fonctionner. Il est traité quand même : si `v1/` venait à être écrasé par erreur
     * avec un fichier de format 2, l'application doit **refuser** ce fichier plutôt que d'en
     * lire les champs qu'elle reconnaît et d'ignorer le sens des autres. Un calendrier
     * vaccinal à moitié compris produirait de fausses dates dans toutes les fiches.
     *
     * @param schemaVersion la valeur trouvée dans le fichier, affichée telle quelle pour que
     *   le parent puisse la citer s'il demande de l'aide.
     */
    data class SchemaInconnu(val schemaVersion: Int) : IssueMiseAJour

    /**
     * Le fichier publié a bien été lu et sa version comparée à celle en base.
     *
     * Sert à décider si la date de « dernière vérification » doit être mise à jour : tant
     * qu'aucun des deux fichiers n'a pu être comparé, **aucune vérification n'a eu lieu**, et
     * inscrire la date du jour laisserait croire à un contrôle qui n'a jamais abouti.
     */
    val comparaisonFaite: Boolean
        get() = this is DejaAJour || this is Remplace

    /** Le contenu local a effectivement été remplacé par une version plus récente. */
    val contenuRemplace: Boolean get() = this is Remplace
}

/**
 * Le compte rendu complet d'un appel à `ReferenceRepository.mettreAJour()` (US-B11).
 *
 * Les deux contenus sont traités indépendamment : chaque champ raconte son propre fichier,
 * et un échec de l'un n'a aucune influence sur l'autre.
 *
 * @param calendrier ce qu'est devenu `calendrier.json`.
 * @param annuaire ce qu'est devenu `csb.json`.
 */
data class ResultatSync(
    val calendrier: IssueMiseAJour,
    val annuaire: IssueMiseAJour,
) {

    /**
     * Le calendrier vaccinal vient d'être remplacé : **les rappels sont à reprogrammer**.
     *
     * C'est la seule condition qui déclenche `PlanificateurRappels.replanifierTout()`
     * (§B8) : un nouveau calendrier change les `ageJours`, les `dependDe` et les tolérances,
     * donc les dates prévues de tous les enfants, donc les rappels. Une mise à jour du seul
     * annuaire ne change aucune date : replanifier serait du travail pour rien.
     */
    val calendrierRemplace: Boolean get() = calendrier.contenuRemplace

    /** Au moins un des deux contenus a changé : il y a quelque chose à annoncer. */
    val contenuRemplace: Boolean get() = calendrier.contenuRemplace || annuaire.contenuRemplace

    /** Les deux fichiers ont été lus et comparés, et aucun n'apportait de nouveauté. */
    val toutEtaitAJour: Boolean
        get() = calendrier is IssueMiseAJour.DejaAJour && annuaire is IssueMiseAJour.DejaAJour

    /**
     * Au moins une vérification a abouti. Faux uniquement si **aucun** des deux fichiers n'a
     * pu être lu et comparé — le cas du téléphone hors réseau.
     */
    val verificationAboutie: Boolean
        get() = calendrier.comparaisonFaite || annuaire.comparaisonFaite

    companion object {

        /**
         * Le compte rendu d'une vérification qui n'a rien pu faire du tout.
         *
         * Filet de sécurité pour l'appelant : `mettreAJour()` s'engage à ne pas lever, mais
         * un `catch` qui doit produire un [ResultatSync] ne doit pas avoir à en inventer un.
         */
        fun echecTotal(): ResultatSync = ResultatSync(
            calendrier = IssueMiseAJour.Echec,
            annuaire = IssueMiseAJour.Echec,
        )
    }
}
