package org.http4s

import org.log4s.getLogger
import scalaz._, Scalaz._
import scalaz.concurrent.Task

package object server {
  /**
   * A middleware is a function of one [[Service]] to another, possibly of a
   * different [[Request]] and [[Response]] type.  http4s comes with several
   * middlewares for composing common functionality into services.
   *
   * @tparam A the request type of the original service
   * @tparam B the response type of the original service
   * @tparam C the request type of the resulting service
   * @tparam D the response type of the resulting service
   */
  type Middleware[A, B, C, D] = Service[A, B] => Service[C, D]

  object Middleware {
    def apply[A, B, C, D](f: (C, Service[A, B]) => Task[D]): Middleware[A, B, C, D] = {
      service => Service.lift {
        req => f(req, service)
      }
    }
  }

  /**
   * An HTTP middleware converts an [[HttpService]] to another.
   */
  type HttpMiddleware = Middleware[Request, MaybeResponse, Request, MaybeResponse]
  /**
   * An HTTP middleware that authenticates users.
   */
  type AuthMiddleware[T] = Middleware[AuthedRequest[T], MaybeResponse, Request, MaybeResponse]

  /**
    * Old name for SSLConfig
    */
  @deprecated("Use SSLConfig", "2016-12-31")
  type SSLBits = SSLConfig

  object AuthMiddleware {
    def apply[T](authUser: Service[Request, T]): AuthMiddleware[T] = {
      service => service.compose(AuthedRequest(authUser))
    }
    def apply[Err, T](authUser: Service[Request, Err \/ T], onFailure: Kleisli[Task, AuthedRequest[Err], MaybeResponse]): AuthMiddleware[T] = { service =>
      (onFailure ||| service)
        .local({authed: AuthedRequest[Err \/ T] => authed.authInfo.bimap(err => AuthedRequest(err, authed.req), suc => AuthedRequest(suc, authed.req))})
        .compose(AuthedRequest(authUser))
    }
  }

  private[this] val messageFailureLogger = getLogger("org.http4s.server.message-failures")
  def messageFailureHandler(req: Request): PartialFunction[Throwable, Task[Response]] = {
    case mf: MessageFailure =>
      messageFailureLogger.debug(mf)(s"""Message failure handling request: ${req.method} ${req.pathInfo} from ${req.remoteAddr.getOrElse("<unknown>")}""")
      mf.toHttpResponse(req.httpVersion)
  }
}
