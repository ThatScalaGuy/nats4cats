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

import cats.effect.implicits.*
import cats.effect.kernel.{Async, Resource}
import cats.effect.std.Dispatcher

import nats4cats.{Deserializer, Nats, NatsClient, Serializer}

import io.nats.service.{Group, ServiceBuilder, ServiceEndpoint}

abstract class Service[F[_]: Async: Nats: Dispatcher](name: String, version: String) {

  private[this] val endpoints = collection.mutable.Set.empty[Endpoint[F, ?, ?]]

  protected[this] given GroupOpt = None
  protected[this] given builder: ServiceBuilder = new ServiceBuilder()
    .name(name)
    .version(version)

  def run() = for {
    connection <- Async[F].pure(summon[Nats[F]].asInstanceOf[NatsClient[F]].underlying).toResource
    _       = builder.connection(connection)
    _       = endpoints.toList.map(_.build(connection)).foreach(builder.addServiceEndpoint)
    service = builder.build()
    _ <- Resource.make(
      Async[F]
        .fromCompletableFuture(
          Async[F].delay(service.startService())
        )
        .start
    )(_ => Async[F].blocking(service.stop()))
  } yield ()

  object syntax {
    //  def mount(e: Int): Unit = ???
    def description(value: String)(using builder: ServiceBuilder): Unit = {
      builder.description(value)
      ()
    }

    def namespace(name: String)(body: GroupOpt ?=> Unit)(using group: GroupOpt): Unit = {
      given current: GroupOpt = group match {
        case Some(value) => Some(value.appendGroup(new Group(name)))
        case None        => Some(new Group(name))
      }
      body
    }

    def endpoint[I, O](
        name: String
    )(using GroupOpt, Deserializer[F, I], Serializer[F, O]): Endpoint[F[_], I, O] =
      val entry = new Endpoint(name) ~ group(summon[GroupOpt])
      endpoints.addOne(entry)
      entry
  }

  final case class group(value: Option[Group]) extends BuildAction {
    def applyTo(builder: ServiceEndpoint.Builder): ServiceEndpoint.Builder = value match {
      case Some(group) => builder.group(group)
      case None        => builder
    }
  }

  final case class subject(value: String) extends BuildAction {
    def applyTo(builder: ServiceEndpoint.Builder): ServiceEndpoint.Builder =
      builder.endpointSubject(value)
  }

  final case class queue(value: String) extends BuildAction {
    def applyTo(builder: ServiceEndpoint.Builder): ServiceEndpoint.Builder =
      builder.endpointQueueGroup(value)
  }
}

final class TestService[F[_]: Async: Nats: Dispatcher] extends Service[F]("test", "1.0.0") {
  import syntax.*

  description("test service")

  namespace("test") {
    endpoint[String, String]("test2") ~ subject("test2") ~ queue("test") -> {
      case "test" =>
        Async[F].pure("test")
      case "aaaa" =>
        Async[F].pure("aaaa")
    }
  }

}
