package com.evolution.sttp.interop

import cats.MonadThrow
import cats.effect.Resource
import cats.syntax.all.*
import com.evolutiongaming.retry.Retry
import com.evolutiongaming.retry.Retry.implicits.*
import sttp.capabilities
import sttp.client3.{Request, Response}

object RetriedSttpBackend {

  def apply[F[_]: MonadThrow, P, C <: EvoContext[F]](
    delegate: EvoSttpBackend[F, P, C],
    retry:    Retry[F],
    retryIf:  Response[?] => Boolean,
  ): Resource[F, EvoSttpBackend[F, P, C]] =
    for {
      _ <- Resource.unit[F]
    } yield new EvoSttpBackend[F, P, C] {

      implicit val _retry: Retry[F] = retry

      override private[interop] def backend = delegate
      override private[interop] def state   = delegate.state
      override private[interop] def log     = delegate.log
      override private[interop] def context = delegate.context

      override def send[T, R >: P & capabilities.Effect[F]](request: Request[T, R]): F[Response[T]] =
        delegate
          .send(request)
          .flatMap { response =>
            if (retryIf(response)) RetryException(response).raiseError[F, Response[T]]
            else response.pure[F]
          }
          .retry

    }

}
