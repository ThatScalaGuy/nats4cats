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

import io.nats.service.*
import scala.jdk.CollectionConverters.*
import nats4cats.Deserializer
import cats.effect.kernel.Resource
import cats.effect.std.{Dispatcher, Queue}
import cats.effect.kernel.Async
import cats.effect.implicits.*
import nats4cats.Serializer
import cats.implicits.*
import nats4cats.Nats
import nats4cats.NatsClient
import io.nats.client.Connection

abstract class BaseEndpoint[F[_]: Async: Nats, I, O](using Deserializer[F, I], Serializer[F, O]) {
  def name: String
  def group: String
  def subject: String           = this.name
  def queue: String             = "q" // default queue group based on the NATS Java client
  def meta: Map[String, String] = Map.empty

  def receive: PartialFunction[I, F[O]]

  private def handlerExecuter(
      connection: Connection
  ): Resource[F, Function1[ServiceMessage, Unit]] = for {
    effectDispatcher <- Dispatcher.parallel[F](true)
    queue            <- Queue.unbounded[F, ServiceMessage].toResource
    _ <- Resource.make(
      queue.take
        .flatMap(message =>
          (for {
            data <- Deserializer[F, I]
              .deserialize(message.getSubject(), message.getHeaders(), message.getData())
            result <- receive.apply(data)
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
        )
        .foreverM[Unit]
        .start
    )(_.cancel)
  } yield (msg: ServiceMessage) => effectDispatcher.unsafeRunSync(queue.offer(msg))

  protected[service] def build(): Resource[F, ServiceEndpoint] =
    for {
      connection <- Async[F].pure(summon[Nats[F]].asInstanceOf[NatsClient[F]].underlying).toResource
      dispatch   <- handlerExecuter(connection)
    } yield ServiceEndpoint
      .builder()
      .endpointName(this.name)
      .endpointSubject(this.subject)
      .endpointMetadata(this.meta.asJava)
      .endpointQueueGroup(this.queue)
      .group(new Group(this.group))
      .handler(msg => dispatch(msg))
      .build()
}
