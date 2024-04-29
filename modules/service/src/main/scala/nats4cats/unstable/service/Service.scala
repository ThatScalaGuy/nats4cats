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

import cats.effect.implicits.*
import cats.effect.kernel.{Async, Resource}
import cats.effect.std.Dispatcher

import nats4cats.{Deserializer, Nats, NatsClient, Serializer}

import io.nats.service.{Group, ServiceBuilder, ServiceEndpoint}
import org.typelevel.otel4s.trace.Tracer

abstract class Service[F[_]: Async: Nats: Dispatcher: Tracer](name: String, version: String) {
  given Service[F]                 = this
  protected[service] val endpoints = collection.mutable.Set.empty[Endpoint[F, ?, ?]]

  protected[this] given GroupList = GroupList(List.empty)
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

    def namespace(name: String*)(body: GroupList ?=> Unit)(using groups: GroupList): Unit = {
      given current: GroupList = groups.copy(groups = groups.groups :++ name)
      body
    }

    def endpoint[I, O](name: String)(using GroupList, Deserializer[F, I], Serializer[F, O]): Endpoint[F[_], I, O] =
      new Endpoint(name, group = summon[GroupList].toGroup)
  }

  final case class subject(value: String) extends Extension {
    override def applyTo[F[_], I, O](endpoint: Endpoint[F, I, O])(using Async[F], Deserializer[F, I], Serializer[F, O]): Endpoint[F, I, O] =
      endpoint.copy(subject = Some(value))
  }

  final case class queue(value: String) extends Extension {
    override def applyTo[F[_], I, O](endpoint: Endpoint[F, I, O])(using Async[F], Deserializer[F, I], Serializer[F, O]): Endpoint[F, I, O] =
      endpoint.copy(queueGroup = Some(value))
  }

  final case class metadata(values: (String, String)*) extends Extension {
    override def applyTo[F[_], I, O](endpoint: Endpoint[F, I, O])(using Async[F], Deserializer[F, I], Serializer[F, O]): Endpoint[F, I, O] =
      endpoint.copy(metadata = endpoint.metadata ++ Map(values: _*))
  }
}
