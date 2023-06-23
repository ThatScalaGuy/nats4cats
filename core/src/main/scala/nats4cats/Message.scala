package nats4cats

import io.nats.client.impl.Headers

final case class Message[A](
    value: A,
    topic: String,
    headers: Headers,
    replyTo: Option[String]
)
