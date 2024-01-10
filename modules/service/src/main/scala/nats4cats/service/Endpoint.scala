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

package nats4cats.service

import cats.implicits.*

import cats.effect.kernel.Async
import cats.effect.std.Dispatcher

import nats4cats.{Deserializer, Serializer}

import io.nats.client.Connection
import io.nats.client.impl.Headers
import io.nats.service.{ServiceEndpoint, ServiceMessage}

class Endpoint[F[_]: Async: Dispatcher, I, O](name: String)(using
    Deserializer[F, I],
    Serializer[F, O]
) {
  private[this] val builder = ServiceEndpoint.builder().endpointName(name)
  private[this] var bodyOpt: Option[(Headers, I) => F[Either[Throwable, O]]] = None

  /** Apply a builder action to the endpoint
    *
    * @param action
    * @return
    */
  def ~(action: BuildAction): Endpoint[F, I, O] = {
    action.applyTo(builder)
    this
  }

  /** Set the handler for the endpoint
    *
    * @param body
    */
  def ->(body: I => F[O]): Unit = bodyOpt = Some((_, i) => body(i).attempt)

  /** Set the handler with headers for the endpoint
    *
    * @param body
    */
  def -->(body: (Headers, I) => F[O]): Unit = bodyOpt = Some((h, i) => body(h, i).attempt)

  /** Set the handler for the endpoint
    *
    * @param body
    */
  def @>(body: I => F[Either[Throwable, O]]): Unit = bodyOpt = Some((_, i) => body(i))

  /** Set the handler with headers for the endpoint
    *
    * @param body
    */
  def @@>(body: (Headers, I) => F[Either[Throwable, O]]): Unit =
    bodyOpt = Some((h, i) => body(h, i))

  private def handlerF(body: (Headers, I) => F[Either[Throwable, O]], connection: Connection)(
      message: ServiceMessage
  ): F[Unit] =
    (for {
      data <- Deserializer[F, I]
        .deserialize(message.getSubject(), message.getHeaders(), message.getData())
      result <- body.apply(message.getHeaders(), data).rethrow
      // allow handling for messages without replyTo
      _ <- Async[F].pure(Option(message.getReplyTo())).recover(_ => None).flatMap {
        case Some(replyTo) =>
          for {
            resultData <- Serializer[F, O]
              .serialize(message.getSubject(), message.getHeaders(), result)
            _ <- Async[F].blocking(message.respond(connection, resultData))
          } yield ()
        case None => Async[F].unit // TODO: add logging
      }
    } yield ()).recoverWith {
      case e: ServiceError =>
        Async[F].blocking(
          message.respondStandardError(connection, e.message, e.code)
        )
      case e: Throwable =>
        Async[F].blocking(
          message.respondStandardError(connection, e.getMessage(), 500)
        )
    }

  protected[service] def build(connection: Connection): ServiceEndpoint = {
    bodyOpt.foreach(body => {
      val f = handlerF(body, connection)
      builder.handler((message) => {
        summon[Dispatcher[F]].unsafeRunAndForget(
          f(message)
        )
      })
    })
    builder.build()
  }
}

object Endpoint {}
