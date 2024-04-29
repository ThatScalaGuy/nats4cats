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

package nats4cats.unstable.service

import cats.implicits.*

import cats.effect.kernel.Async
import cats.effect.std.Dispatcher

import nats4cats.{Deserializer, Serializer}

import io.nats.client.Connection
import io.nats.service.{Group, ServiceEndpoint, ServiceMessage}
import org.typelevel.otel4s.trace.{SpanKind, Status, Tracer}

import otel4s.given

final case class Endpoint[F[_]: Async, I, O](
    name: String,
    group: Option[Group] = None,
    queueGroup: Option[String] = None,
    subject: Option[String] = None,
    metadata: Map[String, String] = Map.empty,
    handler: Option[Request[I] => F[Either[Throwable, O]]] = None
)(using Deserializer[F, I], Serializer[F, O]) {

  def ~(ext: Extension): Endpoint[F, I, O] = ext.applyTo(this)

  def ->(body: I => F[O])(using S: Service[F]): Unit = {
    val endpoint: Endpoint[F, I, O] = copy(handler = Some(req => body(req.data).attempt))
    S.endpoints.add(endpoint)
    ()
  }

  def -->(body: Request[I] => F[O])(using S: Service[F]): Unit = {
    val endpoint: Endpoint[F, I, O] = copy(handler = Some(req => body(req).attempt))
    S.endpoints.add(endpoint)
    ()
  }

  def @>(body: I => F[Either[Throwable, O]])(using S: Service[F]): Unit = {
    val endpoint: Endpoint[F, I, O] = copy(handler = Some(req => body(req.data)))
    S.endpoints.add(endpoint)
    ()
  }

  def @@>(body: Request[I] => F[Either[Throwable, O]])(using S: Service[F]): Unit = {
    val endpoint: Endpoint[F, I, O] = copy(handler = Some(req => body(req)))
    S.endpoints.add(endpoint)
    ()
  }

  private[this] def handlerF(
      body: Request[I] => F[Either[Throwable, O]],
      subjectPattern: String,
      connection: Connection
  )(message: ServiceMessage)(using Tracer[F]): F[Unit] =
    Tracer[F].joinOrRoot(message.getHeaders()) {
      Tracer[F]
        .spanBuilder(message.getSubject())
        .withSpanKind(SpanKind.Server)
        .build
        .use { span =>
          (for {
            data <- Tracer[F]
              .span("request.deserialize")
              .surround(
                Deserializer[F, I]
                  .deserialize(message.getSubject(), message.getHeaders(), message.getData())
              )
            result <- Tracer[F]
              .span("function")
              .surround(body.apply(Request[I](data, message.getHeaders(), Request.extractFromSubject(message.getSubject(), subjectPattern))))
              .rethrow
            // allow handling for messages without replyTo
            _ <- Async[F].pure(Option(message.getReplyTo())).recover(_ => None).flatMap {
              case Some(replyTo) =>
                for {
                  resultData <- Tracer[F]
                    .span("response.serialize")
                    .surround(
                      Serializer[F, O]
                        .serialize(message.getSubject(), message.getHeaders(), result)
                    )
                  _ <- Tracer[F].span("response.send").surround(Async[F].blocking(message.respond(connection, resultData)))
                  _ <- span.setStatus(Status.Ok)
                } yield ()
              case None => span.setStatus(Status.Ok) *> Async[F].unit // TODO: add logging
            }
          } yield ()).recoverWith {
            case e: ServiceError =>
              for {
                _ <- Tracer[F].span("response.send").surround(Async[F].blocking(message.respondStandardError(connection, e.message, e.code)))
                _ <- span.recordException(e)
                _ <- span.setStatus(Status.Error)
              } yield ()
            case e: Throwable =>
              for {
                _ <- Tracer[F].span("response.send").surround(Async[F].blocking(message.respondStandardError(connection, e.getMessage(), 500)))
                _ <- span.recordException(e)
                _ <- span.setStatus(Status.Error)
              } yield ()
          }
        }
    }

  protected[service] def build(connection: Connection)(using D: Dispatcher[F], T: Tracer[F]): ServiceEndpoint = {
    import scala.jdk.CollectionConverters.*
    val subjectPattern = group.map(_.getSubject() + "." + name).getOrElse(name)
    val fn             = handlerF(handler.get, subjectPattern, connection)
    val builder = ServiceEndpoint
      .builder()
      .endpointName(name)
      .endpointMetadata(metadata.asJava)
      .handler((message) => {
        D.unsafeRunAndForget(fn(message))
      })

    queueGroup.foreach(builder.endpointQueueGroup)
    group.foreach(builder.group)
    subject.foreach(builder.endpointSubject)
    builder.build()
  }
}
