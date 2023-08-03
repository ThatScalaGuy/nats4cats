/*
 * Copyright 2023 ThatScalaGuy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
