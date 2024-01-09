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

import nats4cats.Nats
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import nats4cats.NatsClient
import cats.effect.implicits.*
import cats.implicits.*
import io.nats.service.ServiceBuilder

abstract class BaseService[F[_]: Async](using Nats[F]) {

  def name: String
  def description: String
  def version: String
  def endpoints: List[BaseEndpoint[F, ?, ?]]

  def run(): Resource[F, Unit] = for {
    connection <- Async[F].pure(summon[Nats[F]].asInstanceOf[NatsClient[F]].underlying).toResource
    serviceEndpoints <- endpoints.traverse(_.build())
    serviceBuilder = new ServiceBuilder()
      .connection(connection)
      .name(this.name)
      .description(this.description)
      .version(this.version)

    serviceBuilderWithEndpoints = serviceEndpoints.foldLeft(serviceBuilder) { (service, endpoint) =>
      service.addServiceEndpoint(endpoint)
    }
    service = serviceBuilderWithEndpoints.build()
    _ <- Resource.make(
      Async[F]
        .fromCompletableFuture(
          Async[F].delay(service.startService())
        )
        .start
    )(_ => Async[F].blocking(service.stop()))

  } yield ()
}
