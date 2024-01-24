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

package example

import cats.implicits.*

import cats.effect.kernel.Async
import cats.effect.std.{Console, Dispatcher}
import cats.effect.{IO, IOApp}

import nats4cats.Nats
import nats4cats.service.*
import nats4cats.service.otel4s.given

import io.nats.client.impl.Headers
import org.typelevel.otel4s.java.OtelJava
import org.typelevel.otel4s.trace.Tracer

object ServiceExampleMain extends IOApp.Simple {

  def application = for {
    given Nats[IO]       <- Nats.connectHosts[IO]("localhost:4222")
    given Dispatcher[IO] <- Dispatcher.parallel[IO]
    given Tracer[IO]     <- OtelJava.global.flatMap(_.tracerProvider.get("service")).toResource
    _                    <- new EchoService[IO].run()
    _                    <- new PingService[IO].run()
    _                    <- client[IO].toResource
  } yield ()
  override def run: IO[Unit] = application.useForever.void
}

def client[F[_]: Async: Nats: Tracer] = Tracer[F]
  .span("call")
  .surround(for {
    headers <- Tracer[F].propagate(Headers())
    _       <- Nats[F].request[String, String]("test.echo", "Hallo", headers).onError(e => Async[F].delay(println(e)))
  } yield ())

class EchoService[F[_]: Async: Nats: Dispatcher: Console: Tracer] extends Service[F]("EchoService", "1.0.0") {
  import syntax.*
  namespace("test") {
    endpoint[String, String]("echo") -> { case msg =>
      Async[F].pure(msg)
    }
  }
}

class PingService[F[_]: Async: Nats: Dispatcher: Tracer] extends Service[F]("PingService", "1.0.0") {
  import syntax.*

  namespace("accounts") {
    namespace("*") {
      namespace("users") {
        namespace("*") {
          endpoint[String, String]("ping1") --> { case msg =>
            Async[F].pure(msg.subjectSegments.mkString(","))
          }
        }
      }

      endpoint[String, String]("ping2") --> { case msg =>
        Async[F].pure(msg.subjectSegments.mkString(","))
      }
    }
  }

  namespace("test") {
    endpoint[String, String]("ping3") ~ metadata("http.path" -> "/ping/{name}", "http.auth.mode" -> "jwt,explode") --> {
      case Request("ping", _, list) =>
        Tracer[F].span("ping").surround {
          Async[F].pure(list.mkString(","))
        }
      case _ => Async[F].raiseError(new Exception("invalid message"))
    }
  }
}
