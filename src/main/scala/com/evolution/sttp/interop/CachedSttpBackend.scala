package com.evolution.sttp.interop

import cats.Applicative
import cats.effect.Resource
import cats.syntax.all.*
import com.evolution.scache.Cache
import sttp.capabilities
import sttp.client3.{Request, Response}

object CachedSttpBackend {

  def apply[F[_]: Applicative, P, C <: EvoContext[F]](
    delegate: EvoSttpBackend[F, P, C],
    cache:    Cache[F, Request[?, P], Response[?]],
  ): Resource[F, EvoSttpBackend[F, P, C]] =
    for {
      _ <- Resource.unit[F]
    } yield new EvoSttpBackend[F, P, C] {
      override private[interop] def backend = delegate
      override private[interop] def state   = delegate.state
      override private[interop] def log     = delegate.log
      override private[interop] def context = delegate.context

      override def send[T, R >: P & capabilities.Effect[F]](request: Request[T, R]): F[Response[T]] =
        cache
          .getOrUpdate(request.asInstanceOf[Request[?, P]])(delegate.send(request).widen[Response[?]])
          .map(_.asInstanceOf[Response[T]])
    }

}
