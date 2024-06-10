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

import cats.implicits._

import cats.effect.kernel.Sync

import io.nats.client.impl.Headers

import java.nio.charset.{Charset, StandardCharsets}

trait Serializer[F[_], A] {
  def serialize(topic: String, headers: Headers, data: A): F[Array[Byte]]
  def contramap[B](f: B => A): Serializer[F, B]
}

object Serializer {
  class SerializerError[A](val topic: String, val headers: Headers, val data: A, val cause: Throwable)
      extends RuntimeException(s"Failed to serialize message to topic $topic with headers $headers and data $data", cause)

  def apply[F[_], A](using Serializer[F, A]): Serializer[F, A] = summon

  def instance[F[_]: Sync, A](
      fn: (String, Headers, A) => F[Array[Byte]]
  ): Serializer[F, A] = new Serializer[F, A] {

    override def serialize(
        topic: String,
        headers: Headers,
        data: A
    ): F[Array[Byte]] = fn(topic, headers, data).handleErrorWith {
      SerializerError[A](topic, headers, data, _).raiseError[F, Array[Byte]]
    }

    override def contramap[B](f: B => A): Serializer[F, B] = instance { (topic, headers, data) =>
      serialize(topic, headers, f(data))
    }
  }

  def lift[F[_]: Sync, A](
      f: A => F[Array[Byte]]
  ): Serializer[F, A] =
    instance((_, _, data) => f(data))

  def identity[F[_]: Sync]: Serializer[F, Array[Byte]] =
    instance((_, _, data) => Sync[F].pure(data))

  def string[F[_]: Sync](
      charset: Charset = StandardCharsets.UTF_8
  ): Serializer[F, String] =
    lift[F, String](data => Sync[F].delay(data.getBytes(charset)))

  given [F[_]: Sync]: Serializer[F, Array[Byte]] = identity[F]
  given [F[_]: Sync]: Serializer[F, String]      = string[F]()
}
