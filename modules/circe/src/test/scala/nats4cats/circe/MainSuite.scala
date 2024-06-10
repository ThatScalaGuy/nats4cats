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

package nats4cats.circe

import cats.effect.IO
import cats.effect.kernel.Resource

import nats4cats._
import nats4cats.circe.given

import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import io.circe.{Codec, Json}
import munit.CatsEffectSuite
import org.testcontainers.containers.wait.strategy.Wait

class MainSuite extends CatsEffectSuite with TestContainerForAll {

  override val containerDef = GenericContainer.Def(
    dockerImage = "nats:2.10.3",
    exposedPorts = Seq(4222),
    waitStrategy = Wait.forLogMessage(".*Server is ready.*", 1)
  )

  test("Subscribe and Publish (Json)") {
    val data = Json.obj("hello" -> Json.fromString("world"))
    withContainers { case natsServer: GenericContainer =>
      for {
        nats <- Nats.connectHosts[IO](
          s"nats://localhost:${natsServer.container.getMappedPort(4222)}"
        )
        result <- Resource.eval(IO.deferred[Json])
        _ <- nats.subscribe[Json]("test") { msg =>
          result.complete(msg.value).as(())
        }
        _     <- Resource.eval(nats.publish("test", data))
        value <- Resource.eval(result.get)
      } yield value
    }.use(value => IO.pure(value == data)).assert
  }

  test("Subscribe and Publish (case class)") {
    final case class Foo(bar: String) derives Codec.AsObject
    val data = Foo("baz")

    withContainers { case natsServer: GenericContainer =>
      for {
        nats <- Nats.connectHosts[IO](
          s"nats://localhost:${natsServer.container.getMappedPort(4222)}"
        )
        result <- Resource.eval(IO.deferred[Foo])
        _ <- nats.subscribe[Foo]("test") { msg =>
          result.complete(msg.value).as(())
        }
        _     <- Resource.eval(nats.publish("test", data))
        value <- Resource.eval(result.get)
      } yield value
    }.use(value => IO.pure(value == data)).assert
  }
}
