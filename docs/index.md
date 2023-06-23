## nats4cats

Small Scala cats-effect wrapper around the official [nats.java](https://github.com/nats-io/nats.java) library.

### Features

Currently, the following features are supported:
 - [x] Publish
 - [x] Subscribe
 - [x] Request

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

This library is currently available for Scala binary and 3.

To use the latest version, include the following in your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "de.thatscalaguy" %% "nats4cats" % "@VERSION@"
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
  nats <- Nats.connectp[IO]()
  _    <- nats.subscribe[String]("foo") { 
            case Message(data, topic, _, _) => // Message hold data, topic, headers and replyTo(optional)
              IO.println(s"Received message on topic $topic: $data")
          }
  _    <- nats.publish[String]("foo", "Hello World!")
} yield ()
```