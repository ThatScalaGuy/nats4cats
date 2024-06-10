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

trait Deserializer[F[_], A] {
  def deserialize(topic: String, headers: Headers, data: Array[Byte]): F[A]
  def evalMap[B](f: A => F[B]): Deserializer[F, B]
  def map[B](f: A => B): Deserializer[F, B]
  def flatMap[B](f: A => Deserializer[F, B]): Deserializer[F, B]
  def attempt: Deserializer[F, Either[Throwable, A]]
  def option: Deserializer[F, Option[A]]

}

object Deserializer {

  class DeserializeError(val topic: String, val headers: Headers, val data: Array[Byte], val cause: Throwable)
      extends RuntimeException(s"Failed to deserialize message from topic $topic with headers $headers and data $data", cause)

  def apply[F[_], A](using Deserializer[F, A]): Deserializer[F, A] = summon

  def instance[F[_]: Sync, A](
      fn: (String, Headers, Array[Byte]) => F[A]
  ): Deserializer[F, A] = new Deserializer[F, A] {

    override def attempt: Deserializer[F, Either[Throwable, A]] =
      instance { (topic, headers, data) =>
        fn(topic, headers, data).attempt
      }

    override def evalMap[B](f: A => F[B]): Deserializer[F, B] = instance { (topic, headers, data) =>
      fn(topic, headers, data).flatMap(f)
    }

    override def map[B](f: A => B): Deserializer[F, B] = instance { (topic, headers, data) =>
      fn(topic, headers, data).map(f)
    }

    override def flatMap[B](f: A => Deserializer[F, B]): Deserializer[F, B] =
      instance { (topic, headers, data) =>
        fn(topic, headers, data).flatMap(a => f(a).deserialize(topic, headers, data))
      }

    override def deserialize(
        topic: String,
        headers: Headers,
        data: Array[Byte]
    ): F[A] = fn(topic, headers, data).recoverWith {
      new DeserializeError(topic, headers, data, _).raiseError[F, A]
    }
    override def option: Deserializer[F, Option[A]] = instance { (topic, headers, data) =>
      fn(topic, headers, data).map(Some(_)).recover { case _ => None }
    }
  }

  def lift[F[_]: Sync, A](
      f: Array[Byte] => F[A]
  ): Deserializer[F, A] =
    instance((_, _, data) => f(data))

  def identity[F[_]: Sync]: Deserializer[F, Array[Byte]] =
    instance((_, _, data) => Sync[F].pure(data))

  def string[F[_]: Sync](
      charset: Charset = StandardCharsets.UTF_8
  ): Deserializer[F, String] =
    lift[F, String](data => Sync[F].delay(new String(data, charset)))

  given [F[_]: Sync]: Deserializer[F, Array[Byte]] = identity[F]
  given [F[_]: Sync]: Deserializer[F, String]      = string[F]()
}
