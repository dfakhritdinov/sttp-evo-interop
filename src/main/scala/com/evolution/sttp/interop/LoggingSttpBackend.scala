package com.evolution.sttp.interop

import cats.MonadThrow
import cats.effect.{Clock, Resource}
import cats.effect.syntax.all.*
import cats.syntax.all.*
import com.evolutiongaming.catshelper.Log
import sttp.capabilities.Effect
import sttp.client3.{Request, Response}
import sttp.model.HeaderNames.SensitiveHeaders

object LoggingSttpBackend {

  def apply[F[_]: MonadThrow: Clock: Log, P, C <: EvoContext[F]](delegate: EvoSttpBackend[F, P, C]): Resource[F, EvoSttpBackend[F, P, C]] =
    for {
      _ <- Resource.unit[F]
    } yield new EvoSttpBackend[F, P, C] {

      import syntax.*

      override private[interop] def backend = delegate
      override private[interop] def state   = delegate.state
      override private[interop] def log     = Log[F]
      override private[interop] def context = delegate.context

      override def send[T, R >: P & Effect[F]](request: Request[T, R]): F[Response[T]] = {
        val mdc = request.mdc
        val res = for {
          _             <- log.debug(s">> ${request.show(includeBody = true, includeHeaders = true, SensitiveHeaders)}", mdc)
          timedResponse <- delegate.send(request).timed
          (took, response) = timedResponse
          res              = response.show(includeBody = true, includeHeaders = false, SensitiveHeaders)
          msg              = s"<< ${f"[${took.toMillis / 1000d}%.3fs]"} ${request.showBasic}, response: $res"
          _ <-
            if (response.isServerError || response.isClientError) log.warn(msg, mdc)
            else log.debug(msg, mdc)
        } yield response
        res.onError { e =>
          log.error(s"<< Exception for ${request.showBasic}", e, mdc)
        }
      }
    }

}
