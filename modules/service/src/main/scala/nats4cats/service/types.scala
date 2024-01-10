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

import io.nats.service.{Group, ServiceEndpoint}

trait BuildAction {
  def applyTo(builder: ServiceEndpoint.Builder): ServiceEndpoint.Builder
}

type GroupOpt = Option[Group]

class ServiceError(val code: Int, val message: String) extends Exception(s"$code - $message")

final class InternalServerError extends ServiceError(500, "Internal Server Error")

final class VerboseInternalServerError(cause: Throwable)
    extends ServiceError(500, s"Internal Server Error - ${cause.getLocalizedMessage()}")
