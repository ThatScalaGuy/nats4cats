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

import cats.implicits.*

import cats.effect.implicits.*
import cats.effect.kernel.{Async, Resource, Sync}
import cats.effect.std.{Dispatcher, Queue}

import io.nats.client.impl.Headers
import io.nats.client.{Connection, Message => JMessage, Nats => JNats, Options}

import scala.concurrent.duration.*

trait Nats[F[_]] {
  def publish[A](subject: String, value: A, headers: Headers = Headers())(using
      Serializer[F, A]
  ): F[Unit]

  def request[A, B](
      subject: String,
      value: A,
      headers: Headers = Headers(),
      duration: Duration = Duration.Inf
  )(using
      Serializer[F, A],
      Deserializer[F, B]
  ): F[Message[B]]
  def subscribe[B](
      topic: String
  )(handler: Function[Message[B], F[Unit]])(using
      Deserializer[F, B]
  ): Resource[F, Unit]

  def subscribeQueue[B](
      topic: String,
      queue: String
  )(handler: Function[Message[B], F[Unit]])(using
      Deserializer[F, B]
  ): Resource[F, Unit]
}

class NatsClient[F[_]: Async] private[nats4cats] (connection: Connection) extends Nats[F] {
  private[nats4cats] val underlying: Connection = connection
  override def publish[A](subject: String, message: A, headers: Headers)(using
      Serializer[F, A]
  ): F[Unit] = for {
    bytes <- Serializer[F, A].serialize(
      subject,
      headers,
      message
    )
    _ <- Sync[F].blocking(connection.publish(subject, bytes))
  } yield ()

  override def request[A, B](
      subject: String,
      message: A,
      headers: Headers,
      duration: Duration
  )(using
      Serializer[F, A],
      Deserializer[F, B]
  ): F[Message[B]] = for {
    bytes <- Serializer[F, A].serialize(
      subject,
      headers,
      message
    )
    response <- Async[F].fromCompletableFuture(
      Async[F].delay(connection.request(subject, bytes))
    )
    value <- Deserializer[F, B].deserialize(
      response.getSubject(),
      response.getHeaders(),
      response.getData()
    )
  } yield Message[B](value, response.getSubject, response.getHeaders, None)

  override def subscribe[B](topic: String)(
      handler: Message[B] => F[Unit]
  )(using Deserializer[F, B]): Resource[F, Unit] =
    _subscribe[B](topic)(handler, None)

  override def subscribeQueue[B](topic: String, queue: String)(
      handler: Message[B] => F[Unit]
  )(using Deserializer[F, B]): Resource[F, Unit] =
    _subscribe[B](topic)(handler, Some(queue))

  private def _subscribe[B](topic: String)(
      handler: Function[Message[B], F[Unit]],
      queue: Option[String]
  )(using
      Deserializer[F, B]
  ): Resource[F, Unit] = for {
    buffer           <- Resource.eval(Queue.unbounded[F, JMessage])
    effectDispatcher <- Dispatcher.parallel[F](true)
    _ <- Resource.make {
      Async[F]
        .delay(
          connection
            .createDispatcher((msg: JMessage) =>
              effectDispatcher.unsafeRunAndForget(buffer.offer(msg))
            )
        )
        .flatMap { dispatcher =>
          queue match {
            case Some(value) =>
              Async[F].delay(dispatcher.subscribe(topic, value))
            case None => Async[F].delay(dispatcher.subscribe(topic))
          }
        }
    } { dispatcher =>
      Async[F].blocking(connection.closeDispatcher(dispatcher))
    }
    _ <- Resource
      .make(
        buffer.take
          .flatMap(message =>
            for {
              value <- Deserializer[F, B]
                .deserialize(
                  message.getSubject,
                  message.getHeaders,
                  message.getData
                )
            } yield Message[B](
              value,
              message.getSubject,
              message.getHeaders,
              Option(message.getReplyTo())
            )
          )
          .flatMap(handler.apply)
          .foreverM[Unit]
          .start
      )(_.cancel)
  } yield ()
}

object Nats {
  import scala.jdk.DurationConverters.*

  def apply[F[_]](using Nats[F]): Nats[F] = summon[Nats[F]]

  private lazy val defaultOptions: Options =
    new Options.Builder().server(Options.DEFAULT_URL).build();

  /** Connect to a NATS server
    *
    * @param hosts
    *   List of NATS servers
    * @return
    *   A resource that will connect to the NATS server when acquired and disconnect when released
    */
  def connectHosts[F[_]: Async](
      hosts: String*
  ): Resource[F, Nats[F]] = {
    val options = new Options.Builder().servers(hosts.toArray).build()
    connect[F](options)
  }

  /** Connect to a NATS server
    *
    * @param hosts
    *   Comma separated list of NATS servers
    * @param timeout
    *   Timeout for draining the connection (Default: 30 seconds)
    * @return
    *   A resource that will connect to the NATS server when acquired and disconnect when released
    */
  def connectHosts[F[_]: Async](
      hosts: String,
      timeout: FiniteDuration = Duration(30, SECONDS)
  ): Resource[F, Nats[F]] = {
    val options = new Options.Builder().servers(hosts.split(",")).build()
    connect[F](options)
  }

  /** Connect to a NATS server
    *
    * @param options
    *   NATS connection options
    * @param timeout
    *   Timeout for draining the connection (Default: 30 seconds)
    * @return
    *   A resource that will connect to the NATS server when acquired and disconnect when released
    */
  def connect[F[_]: Async](
      options: Options = defaultOptions,
      timeout: FiniteDuration = Duration(30, SECONDS)
  ): Resource[F, Nats[F]] = for {
    connection <- Resource.make(
      Async[F].blocking(JNats.connect(options))
    ) { conn =>
      Async[F]
        .fromCompletableFuture(
          Async[F]
            .delay(conn.drain(timeout.toJava))
        )
        .void
    }
  } yield new NatsClient[F](connection)
}
