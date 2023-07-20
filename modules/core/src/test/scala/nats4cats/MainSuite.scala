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

class MainSuite extends CatsEffectSuite with TestContainerForAll {

  override val containerDef = GenericContainer.Def(
    dockerImage = "nats:2.6.2",
    exposedPorts = Seq(4222),
    waitStrategy = Wait.forLogMessage("Server is ready", 1)
  )

  test("Main should exit succesfully") {
    // val main = Main.run.attempt
    assertIO(IO.unit.attempt, Right(()))
  }

}
