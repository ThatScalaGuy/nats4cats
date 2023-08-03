package nats4cats.circe

import cats.effect.kernel.Sync
import nats4cats.{Deserializer, Serializer}
import io.circe.{Json, Encoder, Decoder}
import io.circe.parser.parse

given [F[_]: Sync]: Deserializer[F, Json] =
  Deserializer.string().map(parse(_).fold(throw _, identity))
given [F[_]: Sync]: Serializer[F, Json] = Serializer.string().contramap(_.noSpaces)

given [F[_]: Sync, A: Decoder]: Deserializer[F, A] =
  Deserializer[F, Json].map(_.as[A].fold(throw _, identity))
given [F[_]: Sync, A: Encoder]: Serializer[F, A] = Serializer[F, Json].contramap(Encoder[A].apply)
