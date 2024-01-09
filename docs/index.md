## nats4cats

Small Scala cats-effect wrapper around the official [nats.java](https://github.com/nats-io/nats.java) library.

### Features
 - Scala 3 support
 - cats-effect 3.5 support

Currently, the following features are supported:
 - Publish
 - Subscribe
 - Request

### Roadmap
*v0.x*
 - Basic Publish/Subscribe/Request
 - Serializer/Deserializer of payloads
 - Scala 2 and 3 support

*v1.x*
 - fs2 integration

*v2.x*
 - JetStream support

*v3.x*
 - Replace nats.java with a pure Scala/cats-effect/fs2-io implementation

### Usage

This library is currently available for Scala3.

To use the latest version, include the following in your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "de.thatscalaguy" %% "nats4cats" % "@VERSION@",
  "de.thatscalaguy" %% "nats4cats-circe" % "@VERSION@" // if you want to use circe integration
   "de.thatscalaguy" %% "nats4cats-service" % "@VERSION@" // if you want to use service integration
)
```

### Quickstart

1.) Add the dependency to your `build.sbt`

2.) Create a connection to the NATS server:

```scala
  import nats4cats.Nats

  for {
    nats <- Nats.connect[IO]() // Create a Resource[F,Nats[F]] managing the connection
  } yield ()
```

3.) Subscribe to a nats topic 

```scala
import nats4cats.Nats
import nats4cats.Serializer.given   // including serializer for string
import nats4cats.Deserializer.given // including deserializer for string

for {
  nats <- Nats.connect[IO]()
  _    <- nats.subscribe[String]("foo") { 
            case Message(data, topic, _, _) => // Message hold data, topic, headers and replyTo(optional)
              IO.println(s"Received message on topic $topic: $data")
          }
  _    <- nats.publish[String]("foo", "Hello World!")
} yield ()
```

### Circe integration

```scala
import nats4cats.circe.given // including circe serializer/deserializer
import io.circe.Codec

final case class Foo(bar: String) derives Codec.AsObject
for {
  nats <- Nats.connect[IO]()
  _    <- nats.subscribe[Foo]("foo") { 
            case Message(data, topic, _, _) => // Message hold data, topic, headers and replyTo(optional)
              IO.println(s"Received message on topic $topic: $data")
          }
  _    <- nats.publish[Foo]("foo", Foo("Hello World!"))
} yield ()
```

### Service implementation

This feature is currently experimental and might change in the future.

```scala
package nats4cats
package service

import cats.effect.IOApp
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.kernel.Async

object Main extends IOApp.Simple {

  def application: Resource[IO, Unit] = for {
    given Nats[IO] <- Nats.connectHosts[IO]("nats://localhost:4222")
    _              <- MainService[IO].run()
  } yield ()
  def run: IO[Unit] = application.useForever.void
}

class MainService[F[_]: Async](using Nats[F]) extends BaseService[F] {
  override def name: String        = "test"
  override def description: String = "test"
  override def version: String     = "0.0.1"
  override def endpoints: List[BaseEndpoint[F, ?, ?]] =
    List(new EchoEndpoint[F], new PingEndpoint[F])
}

trait TestGroup {
  def group: String = "example"
}

class EchoEndpoint[F[_]: Async: Nats] extends BaseEndpoint[F, String, String] with TestGroup {
  override def name: String = "echo"
  override def receive: PartialFunction[String, F[String]] = { case str =>
    Async[F].pure(str)
  }
}

class PingEndpoint[F[_]: Async: Nats] extends BaseEndpoint[F, String, String] with TestGroup {
  override def name: String = "ping"
  override def receive: PartialFunction[String, F[String]] = { case "ping" =>
    Async[F].pure("pong")
  }
}
```