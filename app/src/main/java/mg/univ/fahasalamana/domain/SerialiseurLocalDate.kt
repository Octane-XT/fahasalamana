package mg.univ.fahasalamana.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * `LocalDate` <-> texte ISO `yyyy-MM-dd`, le format de `publieLe` dans les deux fichiers.
 *
 * Même règle que côté base (`Convertisseurs`, §B5.2) et que côté export (B16) : une date
 * métier est du texte ISO, jamais un epoch. Une date illisible lève ici plutôt que de
 * laisser passer une valeur approximative — l'appelant (B04, B17, B19) traite l'échec.
 *
 * Vit dans `domain` parce que les deux couches qui s'en servent en dépendent : les DTO du
 * contrat publié (`data/remote`) et le carnet exporté (`domain`). L'inverse ferait dépendre
 * le domaine de la couche données.
 */
object SerialiseurLocalDate : KSerializer<LocalDate> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("mg.univ.fahasalamana.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString(), DateTimeFormatter.ISO_LOCAL_DATE)
}
