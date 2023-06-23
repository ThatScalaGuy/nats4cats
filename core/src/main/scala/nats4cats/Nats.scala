package nats4cats

import cats.effect.kernel.Resource
import io.nats.client.{Nats => JNats, Options, Message => JMessage}
import cats.effect.kernel.Sync
import cats.effect.kernel.Async
import scala.concurrent.duration.Duration
import cats.effect.IO
import cats.implicits.*
import cats.effect.syntax.dispatcher
import cats.evidence.As
import cats.effect.std.Queue
import cats.effect.implicits.*
import cats.effect.std.Dispatcher
import io.nats.client.impl.Headers

trait Nats[F[_]] {
  def publish[A](subject: String, value: A)(using Serializer[F, A]): F[Unit]
  def request[A, B](subject: String, value: A)(using
      Serializer[F, A],
      Deserializer[F, B]
  ): F[Message[B]]
  def subscribe[B](
      topic: String
  )(handler: Function[Message[B], F[Unit]])(using
      Deserializer[F, B]
  ): Resource[F, Unit]
}

object Nats {

  private lazy val defaultOptions: Options =
    new Options.Builder().server(Options.DEFAULT_URL).build();
  def connect[F[_]: Async](
      options: Options = defaultOptions
  ): Resource[F, Nats[F]] = for {
    connection <- Resource.make(
      Sync[F].blocking(JNats.connect(options))
    ) { conn =>
      Async[F]
        .fromCompletableFuture(
          Async[F].delay(conn.drain(java.time.Duration.ofSeconds(30)))
        )
        .void
    }
  } yield new Nats[F] {
    override def publish[A](subject: String, message: A)(using
        Serializer[F, A]
    ): F[Unit] = for {
      bytes <- Serializer[F, A].serialize(
        subject,
        Headers(),
        message
      )
      _ <- Sync[F].blocking(connection.publish(subject, bytes))
    } yield ()

    override def request[A, B](subject: String, message: A)(using
        Serializer[F, A],
        Deserializer[F, B]
    ): F[Message[B]] = for {
      bytes <- Serializer[F, A].serialize(
        subject,
        Headers(),
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
        handler: Function[Message[B], F[Unit]]
    )(using
        Deserializer[F, B]
    ): Resource[F, Unit] = for {
      queue <- Resource.eval(Queue.unbounded[F, JMessage])
      effectDispatcher <- Dispatcher.sequential[F](true)
      _ <- Resource.make {
        Async[F].delay(
          connection
            .createDispatcher((msg: JMessage) =>
              effectDispatcher.unsafeRunAndForget(queue.offer(msg))
            )
            .subscribe(topic)
        )
      } { dispatcher =>
        Async[F].blocking(connection.closeDispatcher(dispatcher))
      }
      _ <- Resource
        .make(
          queue.take
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
}
