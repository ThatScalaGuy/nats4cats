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

import munit.CatsEffectSuite
import com.dimafeng.testcontainers.munit.TestContainerForAll
import com.dimafeng.testcontainers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import cats.effect.IO
import cats.effect.kernel.Resource

class MainSuite extends CatsEffectSuite with TestContainerForAll {

  override val containerDef = GenericContainer.Def(
    dockerImage = "nats:2.10.3",
    exposedPorts = Seq(4222),
    waitStrategy = Wait.forLogMessage(".*Server is ready.*", 1)
  )

  test("Connect to a Nats server") {
    withContainers { case natsServer: GenericContainer =>
      Nats
        .connectHosts[IO](s"nats://localhost:${natsServer.container.getMappedPort(4222)}")
        .use(_ => IO.pure(true))
        .assert
    }
  }

  test("Subscribe and Publish") {
    withContainers { case natsServer: GenericContainer =>
      for {
        nats <- Nats.connectHosts[IO](
          s"nats://localhost:${natsServer.container.getMappedPort(4222)}"
        )
        result <- Resource.eval(IO.deferred[String])
        _ <- nats.subscribe[String]("test") { msg =>
          result.complete(msg.value).as(())
        }
        _     <- Resource.eval(nats.publish("test", "Hello World!"))
        value <- Resource.eval(result.get)
      } yield value
    }.use(value => IO.pure(value == "Hello World!")).assert
  }

  test("Request") {
    withContainers { case natsServer: GenericContainer =>
      for {
        nats <- Nats.connectHosts[IO](
          s"nats://localhost:${natsServer.container.getMappedPort(4222)}"
        )
        _ <- nats.subscribe[String]("test") { msg =>
          nats.publish(msg.replyTo.get, msg.value + "!")
        }
        value <- Resource.eval(nats.request[String, String]("test", "Hello World!"))
      } yield value.value
    }.use(value => IO.pure(value == "Hello World!!")).assert
  }
}
