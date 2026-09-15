package mg.univ.fahasalamana.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * Palette de l'application.
 *
 * Deux familles de couleurs, à ne pas mélanger :
 *
 * 1. Les rôles Material 3 (primary, surface, error…), assemblés en schémas clair et sombre
 *    dans Theme.kt. Un écran ne les lit que par MaterialTheme.colorScheme.
 * 2. Les couleurs de statut d'un vaccin, propres au métier (CDC §B7.2). Un écran ne les lit
 *    que par FahasalamanaTheme.couleursStatut : aucune valeur hexadécimale dans un écran.
 *
 * Teinte de base : un bleu-vert « santé », lisible en plein soleil comme en salle d'attente.
 */

// --- Rôles Material 3, thème clair ---

internal val PrimaireClair = Color(0xFF00687A)
internal val SurPrimaireClair = Color(0xFFFFFFFF)
internal val PrimaireConteneurClair = Color(0xFFAEECFF)
internal val SurPrimaireConteneurClair = Color(0xFF001F26)

internal val SecondaireClair = Color(0xFF4B6268)
internal val SurSecondaireClair = Color(0xFFFFFFFF)
internal val SecondaireConteneurClair = Color(0xFFCEE7EE)
internal val SurSecondaireConteneurClair = Color(0xFF061F24)

internal val TertiaireClair = Color(0xFF565D7E)
internal val SurTertiaireClair = Color(0xFFFFFFFF)
internal val TertiaireConteneurClair = Color(0xFFDDE1FF)
internal val SurTertiaireConteneurClair = Color(0xFF131A37)

internal val ErreurClair = Color(0xFFBA1A1A)
internal val SurErreurClair = Color(0xFFFFFFFF)
internal val ErreurConteneurClair = Color(0xFFFFDAD6)
internal val SurErreurConteneurClair = Color(0xFF410002)

internal val FondClair = Color(0xFFFBFCFE)
internal val SurFondClair = Color(0xFF191C1D)
internal val SurfaceClair = Color(0xFFFBFCFE)
internal val SurSurfaceClair = Color(0xFF191C1D)
internal val SurfaceVarianteClair = Color(0xFFDBE4E7)
internal val SurSurfaceVarianteClair = Color(0xFF3F484B)
internal val ContourClair = Color(0xFF6F797B)
internal val ContourVarianteClair = Color(0xFFBFC8CB)
internal val SurfaceInverseClair = Color(0xFF2E3132)
internal val SurSurfaceInverseClair = Color(0xFFEFF1F2)
internal val PrimaireInverseClair = Color(0xFF55D6F4)

// --- Rôles Material 3, thème sombre ---

internal val PrimaireSombre = Color(0xFF55D6F4)
internal val SurPrimaireSombre = Color(0xFF003641)
internal val PrimaireConteneurSombre = Color(0xFF004E5C)
internal val SurPrimaireConteneurSombre = Color(0xFFAEECFF)

internal val SecondaireSombre = Color(0xFFB2CBD2)
internal val SurSecondaireSombre = Color(0xFF1C343A)
internal val SecondaireConteneurSombre = Color(0xFF334A50)
internal val SurSecondaireConteneurSombre = Color(0xFFCEE7EE)

internal val TertiaireSombre = Color(0xFFBEC5EB)
internal val SurTertiaireSombre = Color(0xFF282F4D)
internal val TertiaireConteneurSombre = Color(0xFF3E4565)
internal val SurTertiaireConteneurSombre = Color(0xFFDDE1FF)

internal val ErreurSombre = Color(0xFFFFB4AB)
internal val SurErreurSombre = Color(0xFF690005)
internal val ErreurConteneurSombre = Color(0xFF93000A)
internal val SurErreurConteneurSombre = Color(0xFFFFDAD6)

internal val FondSombre = Color(0xFF191C1D)
internal val SurFondSombre = Color(0xFFE1E3E4)
internal val SurfaceSombre = Color(0xFF191C1D)
internal val SurSurfaceSombre = Color(0xFFE1E3E4)
internal val SurfaceVarianteSombre = Color(0xFF3F484B)
internal val SurSurfaceVarianteSombre = Color(0xFFBFC8CB)
internal val ContourSombre = Color(0xFF899295)
internal val ContourVarianteSombre = Color(0xFF3F484B)
internal val SurfaceInverseSombre = Color(0xFFE1E3E4)
internal val SurSurfaceInverseSombre = Color(0xFF191C1D)
internal val PrimaireInverseSombre = Color(0xFF00687A)

/**
 * Les trois nuances d'un même statut de vaccin.
 *
 * @param principale pastille pleine et icône posées sur la surface de l'écran
 * @param conteneur fond d'une puce ou d'un bandeau
 * @param surConteneur texte et icône posés sur [conteneur]
 */
@Immutable
data class CouleurStatut(
    val principale: Color,
    val conteneur: Color,
    val surConteneur: Color,
)

/**
 * Les cinq statuts de `StatutVaccin` (CDC §B4), un pour un.
 *
 * Correspondance des teintes reprise du CDC §B7.2 : vert = fait, gris = à venir,
 * orange = à faire dans la fenêtre, rouge = en retard, bleu clair = en attente d'une
 * dose précédente. Chaque couleur doit être doublée d'une icône et d'un libellé côté
 * écran (accessibilité, daltonisme) : la couleur seule ne porte jamais l'information.
 *
 * TODO(B06)/TODO(B08) : fonction de correspondance `StatutVaccin` -> [CouleurStatut],
 * à écrire avec l'écran qui l'utilise en premier (le type `StatutVaccin` arrive en B05).
 */
@Immutable
data class CouleursStatut(
    val fait: CouleurStatut,
    val aVenir: CouleurStatut,
    val aFaire: CouleurStatut,
    val enRetard: CouleurStatut,
    val enAttente: CouleurStatut,
)

internal val CouleursStatutClair = CouleursStatut(
    fait = CouleurStatut(
        principale = Color(0xFF2E6B33),
        conteneur = Color(0xFFB7F0B5),
        surConteneur = Color(0xFF002204),
    ),
    aVenir = CouleurStatut(
        principale = Color(0xFF5A6165),
        conteneur = Color(0xFFDCE3E8),
        surConteneur = Color(0xFF1A1C1E),
    ),
    aFaire = CouleurStatut(
        principale = Color(0xFF8A5100),
        conteneur = Color(0xFFFFDCBE),
        surConteneur = Color(0xFF2C1600),
    ),
    enRetard = CouleurStatut(
        principale = Color(0xFFBA1A1A),
        conteneur = Color(0xFFFFDAD6),
        surConteneur = Color(0xFF410002),
    ),
    enAttente = CouleurStatut(
        principale = Color(0xFF00658F),
        conteneur = Color(0xFFC7E7FF),
        surConteneur = Color(0xFF001E2E),
    ),
)

internal val CouleursStatutSombre = CouleursStatut(
    fait = CouleurStatut(
        principale = Color(0xFF9CD49A),
        conteneur = Color(0xFF14521C),
        surConteneur = Color(0xFFB7F0B5),
    ),
    aVenir = CouleurStatut(
        principale = Color(0xFFC3C7CB),
        conteneur = Color(0xFF42474B),
        surConteneur = Color(0xFFDCE3E8),
    ),
    aFaire = CouleurStatut(
        principale = Color(0xFFFFB865),
        conteneur = Color(0xFF683E00),
        surConteneur = Color(0xFFFFDCBE),
    ),
    enRetard = CouleurStatut(
        principale = Color(0xFFFFB4AB),
        conteneur = Color(0xFF93000A),
        surConteneur = Color(0xFFFFDAD6),
    ),
    enAttente = CouleurStatut(
        principale = Color(0xFF86CFFF),
        conteneur = Color(0xFF004C6B),
        surConteneur = Color(0xFFC7E7FF),
    ),
)
