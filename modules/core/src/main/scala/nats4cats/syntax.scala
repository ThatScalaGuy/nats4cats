package nats4cats

import cats.effect.kernel.Sync
import io.nats.client.impl.Headers

object syntax {
  extension (m: Message[?]) {
    def reply[F[_]: Sync: Nats, A](message: A, headers: Headers = Headers())(using
        Serializer[F, A]
    ): F[Unit] = m.replyTo match {
      case Some(replyTo) => Nats[F].publish(replyTo, message, headers)
      case None          => Sync[F].raiseError(new Exception("No replyTo found"))
    }
  }
}
