package mg.univ.fahasalamana.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.ExperimentalSerializationApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

/*
 * ---------------------------------------------------------------------------
 * ACCÈS RÉSEAU AUX FICHIERS DE RÉFÉRENCE PUBLIÉS (B19, §B5.1)
 * ---------------------------------------------------------------------------
 *
 * Le seul usage du réseau dans toute l'application, et il ne porte que du **contenu
 * public** : un calendrier vaccinal et un annuaire de centres de santé. Aucun enfant,
 * aucune dose administrée ne passe par ici — c'est la raison pour laquelle la permission
 * `INTERNET` du manifeste porte le commentaire qu'elle porte (§B8, point 1).
 *
 * Deux requêtes, `GET`, sans en-tête, sans authentification, sans corps : les fichiers sont
 * servis en clair par `raw.githubusercontent.com`. Il n'y a rien à envoyer, donc rien à
 * fuiter.
 *
 * **Les types de retour sont ceux de B04** (`CalendrierDto`, `AnnuaireDto`). C'est le même
 * format que les copies d'`assets/`, et c'est tout l'intérêt du contrat du §B5.1 : le
 * fichier embarqué et le fichier distant sont interchangeables, donc `MappageReference` et
 * `ReferenceDao.remplacerCalendrier` servent les deux chemins sans une ligne de plus.
 *
 * Ce fichier ne décide de rien : il télécharge. La comparaison de version, la validation du
 * `schemaVersion` et le remplacement transactionnel sont dans `SynchroniseurReference`.
 */

/**
 * Racine des fichiers de référence publiés (§B5.1).
 *
 * **TODO(B19) — URL à remplacer avant la soutenance.** Le dépôt de données
 * `fahasalamana-data` n'est pas encore créé (point n° 4 du suivi `TACHES.md`) : aucune
 * organisation GitHub n'est arrêtée, donc aucune URL `raw.githubusercontent.com` ne répond
 * aujourd'hui. Le segment `ORGANISATION-A-DEFINIR` est un **marqueur volontairement
 * impossible à confondre avec un vrai nom** : tant qu'il est là, « Vérifier les mises à
 * jour » finit sur un échec réseau propre (HTTP 404), sans rien changer en base.
 *
 * Ce qu'il faudra faire, dans l'ordre :
 *  1. créer le dépôt public `<organisation>/fahasalamana-data` ;
 *  2. y publier `v1/calendrier.json` et `v1/csb.json` (les fichiers de B03, déjà dans
 *     `app/src/main/assets/`) avec un `version` **supérieur** à celui des copies embarquées,
 *     sans quoi la démonstration répondra « vous êtes à jour » et ne montrera rien ;
 *  3. remplacer `ORGANISATION-A-DEFINIR` ci-dessous, et rien d'autre.
 *
 * La barre oblique finale est exigée par Retrofit pour une `baseUrl`. Le préfixe `v1/` n'est
 * volontairement **pas** ici mais dans les deux chemins de [ReferenceApi] : c'est la version
 * du *format* que cette version de l'application sait lire, elle se lit donc à côté du nom
 * du fichier. Un futur `v2/` se verra dans le diff, pas caché au milieu d'une URL.
 */
const val URL_BASE_REFERENCES: String =
    "https://raw.githubusercontent.com/ORGANISATION-A-DEFINIR/fahasalamana-data/main/"

/**
 * Les deux fichiers de référence, tels que publiés (§B5.1).
 *
 * Fonctions `suspend` renvoyant directement le DTO, et non `Response<…>` ni `Call<…>` :
 * Retrofit lit alors **tout** le corps de la réponse et le désérialise avant de rendre la
 * main. C'est la propriété sur laquelle repose la Definition of Done de B19 (« un échec
 * réseau sans effet sur la base ») : une connexion coupée à mi-téléchargement, un JSON
 * tronqué ou une page HTML d'erreur lèvent **ici**, c'est-à-dire avant que le moindre
 * remplacement en base ait commencé. Un statut HTTP hors 2xx lève une `HttpException`, pour
 * la même raison et au même endroit.
 */
interface ReferenceApi {

    /** Le calendrier vaccinal publié (≈ 4 Ko). */
    @GET("v1/calendrier.json")
    suspend fun calendrier(): CalendrierDto

    /** L'annuaire des centres publié (≈ 115 Ko). */
    @GET("v1/csb.json")
    suspend fun annuaire(): AnnuaireDto
}

/**
 * Construit le client des fichiers de référence.
 *
 * Fonction et non `object` : l'instance est un singleton **Koin** (`single { … }`), comme
 * tout le reste du projet, et l'URL reste un paramètre pour qu'un test ou une démonstration
 * puisse viser un autre dépôt sans recompiler la constante.
 *
 * Trois choix, et leurs raisons :
 *
 * - **Le convertisseur réutilise [jsonReference]**, le `Json` de B04 : `ignoreUnknownKeys`
 *   est la clause de compatibilité ascendante du §B5.1, et elle doit valoir pour le fichier
 *   distant exactement comme pour le fichier embarqué. Un second `Json` configuré autrement
 *   ferait diverger les deux chemins de lecture au premier champ ajouté.
 * - **Le type de contenu déclaré est `application/json`** alors que `raw.githubusercontent.com`
 *   sert ses fichiers en `text/plain`. Sans effet : le convertisseur de Jake Wharton ne filtre
 *   pas les réponses sur leur type, il désérialise le corps quel qu'il soit. Ce type sert aux
 *   requêtes, et il n'y en a aucune ici.
 * - **Les délais sont explicites.** Les valeurs par défaut d'OkHttp sont de 10 s de connexion
 *   et **aucune** limite de lecture : sur un réseau qui accepte la connexion puis n'envoie
 *   plus rien, le bouton « Vérifier les mises à jour » tournerait indéfiniment. Les valeurs
 *   ci-dessous laissent le temps aux 115 Ko de l'annuaire sur une connexion lente, ce qui est
 *   le cas d'usage réel de ce projet.
 *
 * `HttpLoggingInterceptor` journalise la requête **en build debug seulement** : son niveau
 * vient du jumeau `src/debug` / `src/release` de `JournalReseau.kt`, même procédé qu'en B11
 * pour le délai de démonstration. Rien de personnel n'y transite, mais une application de
 * santé n'a pas à écrire dans logcat en production.
 */
// `asConverterFactory` est marquée expérimentale dans la version 1.0.0 du convertisseur,
// comme `decodeFromStream` l'est dans `SourcesEmbarquees` (B04) : même opt-in explicite,
// limité à la fonction qui en a besoin.
@OptIn(ExperimentalSerializationApi::class)
fun construireReferenceApi(urlBase: String = URL_BASE_REFERENCES): ReferenceApi {
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // Plafond de l'appel entier, redirections et lecture du corps comprises. C'est lui
        // qui borne vraiment le téléchargement ; `SynchroniseurReference` pose par-dessus un
        // `withTimeout` un peu plus large, comme dernier filet si OkHttp restait bloqué.
        .callTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().setLevel(NIVEAU_JOURNAL_RESEAU))
        .build()

    return Retrofit.Builder()
        .baseUrl(urlBase)
        .client(client)
        .addConverterFactory(jsonReference.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ReferenceApi::class.java)
}
