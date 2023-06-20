package com.evolution.sttp.interop

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import com.evolutiongaming.catshelper.Log
import org.http4s.client.middleware.CookieJar
import sttp.capabilities.Effect
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.http4s.Http4sBackend
import sttp.client3.{Request, Response}

import scala.concurrent.duration.*

object EvoSttp {

  final case class Config(
    host:              String,
    port:              Int,
    forceSsl:          Boolean = false,
    maxConnections:    Int = 256,
    maxWaitQueueLimit: Int = 1024,
    // from http4s docs `BlazeClientBuilder.verifyTimeoutRelations`:
    //   It is recommended to configure responseHeaderTimeout < requestTimeout < idleTimeout
    //   or disable some of them explicitly by setting them to Duration.Inf.
    connectingTimeout:     FiniteDuration = 10.seconds, // default: 10.seconds
    responseHeaderTimeout: FiniteDuration = 10.seconds, // default: Duration.Inf
    requestTimeout:        FiniteDuration = 20.seconds, // default: 45.seconds
    idleTimeout:           FiniteDuration = 30.seconds, // default: 1.minute
    maxIdleDuration:       FiniteDuration = 60.seconds, // default: Duration.Inf
    cookieSupport:         Boolean = false,
  )

  def http4s[F[_]: Async](config: Config): Resource[F, EvoSttpBackend[F, Fs2Streams[F], EvoContext[F]]] = {

    import org.http4s.blaze.client.BlazeClientBuilder

    BlazeClientBuilder[F]
      .withIdleTimeout(config.idleTimeout)
      .withConnectTimeout(config.connectingTimeout)
      .withMaxTotalConnections(config.maxConnections)
      .withRequestTimeout(config.requestTimeout)
      .withResponseHeaderTimeout(config.responseHeaderTimeout)
      .withSocketKeepAlive(true)
      .withMaxIdleDuration(config.maxIdleDuration)
      .withMaxWaitQueueLimit(config.maxWaitQueueLimit)
      .resourceWithState
      .evalMap {
        case (client, metrics) =>
          for {
            client <- if (config.cookieSupport) CookieJar.impl(client) else client.pure[F]
          } yield new EvoSttpBackend[F, Fs2Streams[F], EvoContext[F]] {
            override private[interop] def backend = Http4sBackend.usingClient(client)
            override private[interop] def state   = metrics.some
            override private[interop] def log     = Log.empty[F]
            override private[interop] def context = EvoContext.Empty()

            override def send[T, R >: Fs2Streams[F] & Effect[F]](request: Request[T, R]): F[Response[T]] = {
              val uri = request.uri.map { uri =>
                uri
                  .scheme(uri.scheme orElse Some(if (config.forceSsl) "https" else "http"))
                  .host(uri.host getOrElse config.host)
                  .port(uri.port orElse config.port.some)
              }
              val authRequest = request.copy(uri = uri)
              backend.send(authRequest)
            }
          }
      }

  }

}
