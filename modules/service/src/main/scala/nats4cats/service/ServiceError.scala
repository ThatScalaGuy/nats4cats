package nats4cats
package service

class ServiceError(val code: Int, val message: String) extends Exception(s"$code - $message")

final class InternalServerError extends ServiceError(500, "Internal Server Error")

final class VerboseInternalServerError(cause: Throwable)
    extends ServiceError(500, s"Internal Server Error - ${cause.getLocalizedMessage()}")
