package mg.univ.fahasalamana.data.local

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Conversions de types pour Room (§B5.2).
 *
 * Une seule règle ici, et elle n'est pas négociable : une date métier est stockée
 * en **texte ISO `yyyy-MM-dd`**, jamais en nombre de jours ni en epoch. Trois raisons :
 *
 * 1. le fichier d'export (B16) est relu par un humain : « 2026-01-15 » est lisible,
 *    « 1768435200000 » ne l'est pas ;
 * 2. un epoch suppose un fuseau au moment de la relecture, ce qui décale une date de
 *    naissance d'un jour selon l'endroit où l'appareil se trouve ; le texte ISO n'a
 *    pas de fuseau, il n'en a pas besoin ;
 * 3. les tris SQL (`ORDER BY date`) et les comparaisons (`WHERE date <= :jour`)
 *    fonctionnent tels quels sur ce format, l'ordre lexicographique étant l'ordre
 *    chronologique.
 *
 * Les instants techniques (`creeLe`, `dernierAcces`) restent des `Long` : ce ne sont
 * pas des dates métier, ils ne s'affichent jamais tels quels.
 */
class Convertisseurs {

    @TypeConverter
    fun versTexte(date: LocalDate?): String? = date?.format(FORMAT_ISO)

    @TypeConverter
    fun versDate(texte: String?): LocalDate? = texte?.let { LocalDate.parse(it, FORMAT_ISO) }

    private companion object {
        /** `yyyy-MM-dd`, identique au format des JSON de référence (§B5.1) et de l'export (B16). */
        val FORMAT_ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
