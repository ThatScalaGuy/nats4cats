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

package nats4cats

import cats.effect.kernel.Sync

import io.nats.client.impl.Headers

object syntax {
  extension (m: Message[?]) {
    def reply[F[_]: Sync: Nats, A](message: A, headers: Headers = Headers())(using
        Serializer[F, A]
    ): F[Unit] = m.replyTo match {
      case Some(replyTo) => Nats[F].publish(replyTo, message, headers)
      case None          => Sync[F].raiseError(new Exception("No replyTo found"))
    }
  }
}
