package mg.univ.fahasalamana.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import mg.univ.fahasalamana.MainActivity
import mg.univ.fahasalamana.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Notifications de rappel de vaccination (tâche B10, CDC §B8, US-B5).
 *
 * Ce fichier ne décide de rien : il ne sait ni quand notifier, ni s'il faut encore le
 * faire. Le « quand » est calculé par `CalculateurEcheancier.rappelsAProgrammer()` (R3)
 * puis enfilé par `PlanificateurRappels` (TODO(B11)) ; le « faut-il encore » est une
 * relecture de la base par `RappelWorker` juste avant l'appel à `afficherRappel`
 * (règle 9 de CLAUDE.md). Ici, on construit et on affiche — rien d'autre.
 */

/** Canal unique des rappels (CDC §B8). Identifiant figé : le changer créerait un second canal. */
const val CANAL_RAPPELS: String = "rappels_vaccins"

/**
 * Identifiant numérique commun à toutes les notifications de rappel.
 *
 * Ce sont les **étiquettes** qui les distinguent ([etiquetteRappel]), pas cet entier : une
 * étiquette est une chaîne, donc sans collision possible, là où deux identifiants dérivés
 * du `hashCode()` d'un UUID peuvent se percuter — et faire disparaître le rappel d'un
 * enfant au moment où celui d'un autre s'affiche.
 */
private const val ID_RAPPEL: Int = 1

private const val ETIQUETTE_LOG: String = "Fahasalamana"

/** « 12/02 », comme dans le scénario de US-B5. */
private val FORMAT_JOUR_MOIS: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM", Locale.FRENCH)

/** « 12/02/2026 », pour une échéance déjà passée, où l'année compte. */
private val FORMAT_JOUR_COMPLET: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)

/**
 * Crée le canal de notification des rappels. Appelée une fois, depuis `App.onCreate`.
 *
 * `IMPORTANCE_DEFAULT` conformément au §B8 : la notification fait un son court et apparaît
 * dans le volet, mais ne s'impose pas en surimpression au milieu de l'écran, ce que ferait
 * `IMPORTANCE_HIGH`. Un rappel à trois jours d'échéance n'est pas une urgence.
 *
 * `lockscreenVisibility = VISIBILITY_PRIVATE` : justification détaillée sur
 * [NotificationHelper.afficherRappel]. C'est la valeur **par défaut** proposée à
 * l'utilisateur dans les réglages Android du canal ; celle de la notification elle-même est
 * posée à la construction, parce qu'un canal déjà créé n'est plus modifiable par le code —
 * seuls son nom et sa description le sont. Sur un téléphone où l'application est déjà
 * installée, changer cette ligne ne changerait donc rien.
 *
 * L'opération est idempotente : la rappeler à chaque démarrage ne coûte rien et ne
 * réinitialise pas les réglages que l'utilisateur aurait modifiés.
 */
fun creerCanauxNotification(context: Context) {
    val canal = NotificationChannel(
        CANAL_RAPPELS,
        context.getString(R.string.notif_canal_rappels_nom),
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = context.getString(R.string.notif_canal_rappels_description)
        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        // Pas de LED clignotante : le rappel n'a pas à réclamer l'attention en continu.
        enableLights(false)
        setShowBadge(true)
    }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
}

/** Vrai à partir d'Android 13, où `POST_NOTIFICATIONS` doit être demandée à l'exécution. */
fun permissionNotificationsRequise(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** Vrai si la permission est accordée — ou si la version d'Android ne la demande pas. */
fun permissionNotificationsAccordee(context: Context): Boolean = !permissionNotificationsRequise() ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
    PackageManager.PERMISSION_GRANTED

/**
 * Ce qu'une notification de rappel a besoin de savoir, et rien de plus.
 *
 * Du texte déjà composé plutôt qu'un `VaccinReference` : le libellé d'une dose
 * (« Pentavalent 1re dose ») est une affaire d'affichage, déjà résolue par l'appelant, et
 * cette classe n'a aucune raison de connaître le calendrier de référence.
 *
 * @param joursRestants nombre de jours entre aujourd'hui et [prevuLe], calculé au moment
 *   d'afficher et non au moment de programmer : WorkManager a le droit de réveiller le
 *   worker avec plusieurs heures de retard (§B8), et un rappel annonçant « dans 3 jours »
 *   le jour même de l'échéance serait faux. Zéro ou négatif est un cas normal, traité.
 */
data class ContenuRappel(
    val enfantId: String,
    val vaccinId: String,
    val prenomEnfant: String,
    val libelleVaccin: String,
    val prevuLe: LocalDate,
    val joursRestants: Long,
)

/**
 * Construit et affiche les notifications de rappel (B10).
 *
 * Sans état : une simple façade au-dessus de `NotificationManagerCompat`. Peut être
 * construite à la demande comme injectée par Koin — c'est ce dernier chemin qu'utilisera
 * `RappelWorker` (TODO(B11)).
 */
class NotificationHelper(context: Context) {

    /**
     * Contexte applicatif, jamais celui d'une activité : cette classe est retenue par une
     * composition (bloc de test des réglages) et le sera par un worker (B11) ; garder un
     * `Activity` fuirait tout l'écran à la première rotation.
     */
    private val context: Context = context.applicationContext

    /**
     * Affiche — ou remplace — le rappel d'une dose. Renvoie `false` si rien n'a été affiché.
     *
     * **Visibilité sur l'écran verrouillé : `VISIBILITY_PRIVATE` (CDC §B8).**
     *
     * Le texte du rappel nomme un enfant et un vaccin. Mis bout à bout, ces deux éléments
     * forment une donnée de santé rattachée à une personne identifiée, et le téléphone d'un
     * foyer est souvent posé sur une table, partagé, prêté. Laisser « Faly : Pentavalent
     * 1re dose » s'afficher en clair sur l'écran de veille, c'est publier cette donnée à
     * toute personne qui passe, sans que le parent l'ait jamais décidé.
     *
     * Les trois possibilités, et pourquoi c'est celle du milieu :
     * - `VISIBILITY_PUBLIC` montrerait le détail à quiconque regarde l'écran éteint. Écarté.
     * - `VISIBILITY_SECRET` masquerait la notification **entièrement** sur l'écran
     *   verrouillé. Écarté aussi, et c'est le choix le moins évident : un rappel qu'on ne
     *   voit qu'après avoir déverrouillé puis ouvert le volet est un rappel qu'on rate, ce
     *   qui vide R3 de son intérêt.
     * - `VISIBILITY_PRIVATE` avec une [versionEcranVerrouille] écrite à la main : l'écran de
     *   veille montre « Rappel de vaccination — ouvrez l'application », sans prénom ni
     *   vaccin ; le détail apparaît une fois le téléphone déverrouillé. Le parent est
     *   prévenu, personne d'autre n'apprend rien.
     *
     * Sans `setPublicVersion`, Android remplacerait le contenu par son propre « Contenu
     * masqué » : correct, mais muet sur ce qu'il y a à faire. D'où une version publique
     * rédigée, avec le même ton factuel que le reste (R7).
     *
     * `setLocalOnly(true)` prolonge la même idée : la notification ne part pas vers une
     * montre connectée ou un ordinateur appairé, où elle échapperait aux protections du §B8.
     */
    @SuppressLint("MissingPermission") // [notificationsAutorisees] vérifie POST_NOTIFICATIONS juste avant.
    fun afficherRappel(contenu: ContenuRappel): Boolean {
        if (!notificationsAutorisees()) {
            Log.i(ETIQUETTE_LOG, "Rappel non affiché : notifications non autorisées")
            return false
        }

        val etiquette = etiquetteRappel(contenu.enfantId, contenu.vaccinId)
        return try {
            NotificationManagerCompat.from(context).notify(etiquette, ID_RAPPEL, construireRappel(contenu))
            true
        } catch (refus: SecurityException) {
            // Course possible : la permission peut être retirée entre la vérification et
            // l'appel. Un rappel manqué ne doit pas faire tomber le worker qui l'affiche.
            Log.w(ETIQUETTE_LOG, "Rappel refusé par le système", refus)
            false
        }
    }

    /**
     * Retire le rappel d'une dose s'il est encore affiché.
     *
     * Utile à B12 : quand le parent saisit la dose, le rappel déjà tombé dans le volet n'a
     * plus lieu d'être. Même clé que le `uniqueWorkName` du travail annulé au même moment.
     */
    fun annulerRappel(enfantId: String, vaccinId: String) {
        NotificationManagerCompat.from(context).cancel(etiquetteRappel(enfantId, vaccinId), ID_RAPPEL)
    }

    /**
     * Vrai si une notification a une chance d'être vue : permission accordée **et**
     * notifications non coupées par l'utilisateur dans les réglages d'Android.
     *
     * Les deux conditions sont distinctes : on peut avoir accordé `POST_NOTIFICATIONS` puis
     * désactivé les notifications de l'application, ou n'avoir jamais eu à l'accorder
     * (Android 12 et avant) et les avoir coupées quand même.
     */
    fun notificationsAutorisees(): Boolean =
        permissionNotificationsAccordee(context) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun construireRappel(contenu: ContenuRappel): Notification {
        val titre = context.getString(
            R.string.notif_rappel_titre,
            contenu.prenomEnfant,
            contenu.libelleVaccin,
        )
        val texte = texteEcheance(contenu)

        return NotificationCompat.Builder(context, CANAL_RAPPELS)
            .setSmallIcon(R.drawable.ic_rappel_vaccination)
            .setContentTitle(titre)
            .setContentText(texte)
            // Le volet déplié porte l'action à faire, que la ligne repliée n'a pas la place
            // d'afficher (« Trouver un CSB », scénario de US-B5).
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notif_rappel_detail, texte)),
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(versionEcranVerrouille())
            .setLocalOnly(true)
            .setAutoCancel(true)
            .setContentIntent(intentFicheEnfant(contenu.enfantId))
            .build()
    }

    /** Ce que voit l'écran verrouillé : aucun prénom, aucun vaccin, juste de quoi agir. */
    private fun versionEcranVerrouille(): Notification =
        NotificationCompat.Builder(context, CANAL_RAPPELS)
            .setSmallIcon(R.drawable.ic_rappel_vaccination)
            .setContentTitle(context.getString(R.string.notif_rappel_masque_titre))
            .setContentText(context.getString(R.string.notif_rappel_masque_texte))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    private fun texteEcheance(contenu: ContenuRappel): String = when {
        contenu.joursRestants > 0 -> {
            val jours = contenu.joursRestants.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            context.resources.getQuantityString(
                R.plurals.notif_rappel_dans_jours,
                jours,
                jours,
                contenu.prevuLe.format(FORMAT_JOUR_MOIS),
            )
        }

        contenu.joursRestants == 0L ->
            context.getString(R.string.notif_rappel_aujourdhui, contenu.prevuLe.format(FORMAT_JOUR_MOIS))

        // Échéance déjà passée : worker réveillé en retard, ou téléphone resté éteint. Ton
        // factuel et jamais de reproche (R7) — la même phrase que celle de la fiche.
        else ->
            context.getString(R.string.notif_rappel_depasse, contenu.prevuLe.format(FORMAT_JOUR_COMPLET))
    }

    /**
     * Ouverture de la fiche de l'enfant, avec la pile de retour `MesEnfants → FicheEnfant`
     * reconstruite (CDC §B7.1).
     *
     * Le lien profond est porté par l'`Intent` lui-même (`ACTION_VIEW` +
     * `fahasalamana://enfant/<id>`) et non par un extra : c'est le `NavHost` qui le consomme
     * au moment où il installe son graphe, et qui empile alors la destination de départ du
     * graphe — `MesEnfants` — sous `FicheEnfant`. Le retour depuis la fiche mène donc à la
     * liste des enfants, et non hors de l'application.
     *
     * `FLAG_ACTIVITY_CLEAR_TASK` (posé ici ; `FLAG_ACTIVITY_NEW_TASK` est ajouté par
     * [TaskStackBuilder]) : la tâche repart de zéro au lieu de déposer la fiche au-dessus de
     * ce qui était ouvert. C'est ce que fait `NavDeepLinkBuilder` d'AndroidX, pour deux
     * raisons — la pile reconstruite est alors exactement celle qu'on vient de décrire, et
     * l'`Intent` est traité par `onCreate`, donc par le `NavHost`, sans avoir à intercepter
     * `onNewIntent` depuis un composable qui n'y a pas accès. Le prix est assumé : une
     * saisie en cours est perdue si l'on touche la notification au même instant.
     *
     * `FLAG_IMMUTABLE` : obligatoire depuis Android 12, et de toute façon correct — le
     * système n'a rien à ajouter à cet `Intent`.
     *
     * Le `requestCode` dérive de l'identifiant pour que deux enfants n'aient pas le même
     * `PendingIntent`. Ce n'est qu'une précaution : les URI diffèrent déjà, et c'est l'URI,
     * non les extras, que le système compare.
     */
    private fun intentFicheEnfant(enfantId: String): PendingIntent {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(lienFicheEnfant(enfantId)),
            context,
            MainActivity::class.java,
        ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

        return requireNotNull(
            TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(intent)
                .getPendingIntent(
                    enfantId.hashCode(),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
        ) { "PendingIntent introuvable, alors que FLAG_UPDATE_CURRENT en garantit un" }
    }
}

/*
 * TODO(B11) — `platform/PlanificateurRappels.kt` et `platform/RappelWorker.kt`.
 *
 * C'est ici, dans le même paquet, que vient la suite. Ce que B10 laisse prêt :
 *
 *  - `CalculateurEcheancier.rappelsAProgrammer()` (R3) produit déjà la liste des `Rappel`
 *    à enfiler, avec leur `dateHeure` d'émission ; `initialDelay = dateHeure - maintenant`.
 *  - [etiquetteRappel] donne le `uniqueWorkName` de R4 — `"rappel-<enfantId>-<vaccinId>"` —
 *    avec `ExistingWorkPolicy.REPLACE` ; le rappel « fenêtre bientôt fermée » y ajoute le
 *    suffixe `-fenetre` (voir la KDoc de `domain/Rappel.kt`).
 *  - `RappelWorker` relit la base avant d'appeler [NotificationHelper.afficherRappel]
 *    (règle 9 de CLAUDE.md), compose le libellé de la dose et calcule `joursRestants` à
 *    cet instant-là, pas à la programmation.
 *  - `NotificationHelper` s'injecte par Koin : `single { NotificationHelper(androidContext()) }`
 *    reste à ajouter dans `di/AppModule.kt`, sous la section « Plateforme ».
 *
 * Aucun `WorkRequest` n'est enfilé par B10 : un écran ne programme jamais de travail
 * lui-même, il appellera `PlanificateurRappels.replanifier(enfantId)` (TODO(B12)).
 */
