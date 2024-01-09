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
