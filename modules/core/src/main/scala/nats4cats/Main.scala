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

package nats4cats

import cats.effect.{IO, IOApp}
import cats.effect.kernel.Resource
import nats4cats.Serializer.given
import nats4cats.Deserializer.given
import io.nats.client.impl.Headers

object Main extends IOApp.Simple {

  def inLoop(nats: Nats[IO]): IO[Unit] =
    IO.print("In:") >> IO.readLine.flatMap {
      case ":q" => IO.unit
      case ":r" =>
        nats.request[String, String]("baz", "Hello!").map(println) >> inLoop(
          nats
        )
      case line =>
        val parts = line.split(":")
        nats.publish[String](
          parts.head,
          parts.last,
          Headers().add("name", "lallala")
        ) >> inLoop(nats)
    }

  def app = for {
    nats <- Nats.connect[IO]()
    _ <- nats.subscribe[String]("foo") { case Message(data, topic, h, _) =>
      IO.println(s"Received message on topic $topic: $data $h")
    }
    _ <- nats.subscribe[String]("bar") { case Message(data, topic, _, _) =>
      IO.println(s"Received message on topic $topic: $data")
    }
    _ <- nats.subscribe[String]("baz") { case Message(data, topic, _, replyTo) =>
      IO.println(s"Received message on topic $topic: $data") >>
        nats.publish(replyTo.get, "Ok!!!")
    }
    _ <- Resource.eval(inLoop(nats))
  } yield ()

  def run: IO[Unit] =
    app.use(_ => IO.unit)
}
