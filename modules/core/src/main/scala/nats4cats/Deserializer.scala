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

import io.nats.client.impl.Headers
import cats.Applicative
import cats.implicits.*
import cats.effect.kernel.Sync
import cats.instances.char
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import nats4cats.Deserializer

trait Deserializer[F[_], A] {
  def deserialize(topic: String, headers: Headers, data: Array[Byte]): F[A]
  def map[B](f: A => B): Deserializer[F, B]
  def flatMap[B](f: A => Deserializer[F, B]): Deserializer[F, B]
  def attempt: Deserializer[F, Either[Throwable, A]]
  def option: Deserializer[F, Option[A]]

}

object Deserializer {

  def apply[F[_], A](using Deserializer[F, A]): Deserializer[F, A] = summon

  def instance[F[_]: Sync, A](
      fn: (String, Headers, Array[Byte]) => F[A]
  ): Deserializer[F, A] = new Deserializer[F, A] {

    override def attempt: Deserializer[F, Either[Throwable, A]] =
      instance { (topic, headers, data) =>
        fn(topic, headers, data).attempt
      }

    override def map[B](f: A => B): Deserializer[F, B] = instance {
      (topic, headers, data) =>
        fn(topic, headers, data).map(f)
    }

    override def flatMap[B](f: A => Deserializer[F, B]): Deserializer[F, B] =
      instance { (topic, headers, data) =>
        fn(topic, headers, data).flatMap(a =>
          f(a).deserialize(topic, headers, data)
        )
      }

    override def deserialize(
        topic: String,
        headers: Headers,
        data: Array[Byte]
    ): F[A] = fn(topic, headers, data)

    override def option: Deserializer[F, Option[A]] = instance {
      (topic, headers, data) =>
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
  given [F[_]: Sync]: Deserializer[F, String] = string[F]()
}
