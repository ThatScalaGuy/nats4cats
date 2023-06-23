package nats4cats

import io.nats.client.impl.Headers
import cats.Applicative
import cats.implicits.*
import cats.effect.kernel.Sync
import cats.instances.char
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

trait Serializer[F[_], A] {
  def serialize(topic: String, headers: Headers, data: A): F[Array[Byte]]
}

object Serializer {

  def apply[F[_], A](using Serializer[F, A]): Serializer[F, A] = summon

  def instance[F[_]: Sync, A](
      fn: (String, Headers, A) => F[Array[Byte]]
  ): Serializer[F, A] = new Serializer[F, A] {

    override def serialize(
        topic: String,
        headers: Headers,
        data: A
    ): F[Array[Byte]] = fn(topic, headers, data)
  }

  def lift[F[_]: Sync, A](
      f: A => F[Array[Byte]]
  ): Serializer[F, A] =
    instance((_, _, data) => f(data))

  def identity[F[_]: Sync]: Serializer[F, Array[Byte]] =
    instance((_, _, data) => Sync[F].pure(data))

  def string[F[_]: Sync](
      charset: Charset = StandardCharsets.UTF_8
  ): Serializer[F, String] =
    lift[F, String](data => Sync[F].delay(data.getBytes(charset)))

  given [F[_]: Sync]: Serializer[F, Array[Byte]] = identity[F]
  given [F[_]: Sync]: Serializer[F, String] = string[F]()
}
