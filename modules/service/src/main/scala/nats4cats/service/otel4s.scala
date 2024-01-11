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

package nats4cats.service

import io.nats.client.impl.Headers
import org.typelevel.otel4s.context.propagation.{TextMapGetter, TextMapUpdater}

import scala.jdk.CollectionConverters.*

object otel4s {
  given TextMapUpdater[Headers] = new TextMapUpdater[Headers] {
    override def updated(carrier: Headers, key: String, value: String): Headers = carrier.add(key, value)
  }

  given TextMapGetter[Headers] = new TextMapGetter[Headers] {
    override def keys(carrier: Headers): Iterable[String]           = carrier.keySet().asScala
    override def get(carrier: Headers, key: String): Option[String] = Option(carrier.getFirst(key))
  }
}
