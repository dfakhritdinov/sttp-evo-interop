package com.evolution.sttp.interop

import cats.effect.{Async, Clock, Resource}
import cats.syntax.all.*
import cats.{Applicative, MonadThrow}
import com.evolution.scache.Cache
import com.evolutiongaming.catshelper.Log
import com.evolutiongaming.retry.Retry
import io.prometheus.client.CollectorRegistry
import sttp.client3.{Request, RequestT, Response, SttpBackend}

import scala.util.Try

object syntax {

  implicit class Syntax[F[_], P](val delegate: SttpBackend[F, P]) extends AnyVal {

    def evo[C <: EvoContext[F]](implicit F: Applicative[F]): EvoSttpBackend[F, P, EvoContext[F]] =
      delegate match {
        case evo: EvoSttpBackend[F, P, C] => evo
        case sttp =>
          new EvoSttpBackend[F, P, EvoContext[F]] {
            override private[interop] def backend = sttp
            override private[interop] def state   = none
            override private[interop] def log     = Log.empty[F]
            override private[interop] def context = EvoContext.Empty()
          }
      }

    def logged(implicit F: MonadThrow[F], C: Clock[F], L: Log[F]): Resource[F, EvoSttpBackend[F, P, EvoContext[F]]] =
      evo.logged

    def metered(backend: String, registry: CollectorRegistry)(implicit F: Async[F]): Resource[F, EvoSttpBackend[F, P, EvoContext.Metered[F]]] =
      evo.metered(backend, registry)

    def cached(cache: Cache[F, Request[?, P], Response[?]])(implicit F: Applicative[F]): Resource[F, EvoSttpBackend[F, P, EvoContext[F]]] =
      evo.cached(cache)

    def retried(
      retry:      Retry[F],
      retryIf:    Response[?] => Boolean = _.isServerError,
    )(implicit F: MonadThrow[F]): Resource[F, EvoSttpBackend[F, P, EvoContext[F]]] =
      evo.retried(retry, retryIf)

  }

  implicit class EvoSyntax[F[_], P, C <: EvoContext[F]](val delegate: EvoSttpBackend[F, P, C]) extends AnyVal {

    def logged(implicit F: MonadThrow[F], C: Clock[F], L: Log[F]): Resource[F, EvoSttpBackend[F, P, C]] =
      LoggingSttpBackend(delegate)

    def metered(backend: String, registry: CollectorRegistry)(implicit F: Async[F]): Resource[F, EvoSttpBackend[F, P, EvoContext.Metered[F]]] =
      MeteredSttpBackend(delegate, registry, Map("backend" -> backend))

    def cached(cache: Cache[F, Request[?, P], Response[?]])(implicit F: Applicative[F]): Resource[F, EvoSttpBackend[F, P, C]] =
      CachedSttpBackend(delegate, cache)

    def retried(retry: Retry[F], retryIf: Response[?] => Boolean = _.isServerError)(implicit F: MonadThrow[F]): Resource[F, EvoSttpBackend[F, P, C]] =
      RetriedSttpBackend(delegate, retry, retryIf)
  }

  implicit class EvoMeteredSyntax[F[_], P](val delegate: EvoSttpBackend[F, P, EvoContext.Metered[F]]) extends AnyVal {

    def resourced(resource: String)(implicit F: Async[F]): Resource[F, EvoSttpBackend[F, P, EvoContext.Metered[F]]] =
      MeteredSttpBackend(
        delegate,
        delegate.context.registry,
        delegate.context.labels.updated("resource", resource),
      )

  }

  implicit class RequestSyntax[U[_], T, -R](val request: RequestT[U, T, R]) extends AnyVal {

    def metricLabel(value: String): RequestT[U, T, R] =
      request.tag(requestMetricLabelTag, value)

    private[interop] def metricLabelValue: Option[String] = for {
      any <- request.tag(requestMetricLabelTag)
      tag <- Try(any.asInstanceOf[String]).toOption
    } yield tag

    def mdc(key: String, value: String): RequestT[U, T, R] =
      request.tag(key, value)

    def mdc(mdc: Map[String, String]): RequestT[U, T, R] =
      mdc.foldLeft(request) { case (r, (k, v)) => r.tag(k, v) }

    def mdc(mdc: Log.Mdc): RequestT[U, T, R] =
      mdc.context.toList.flatMap(_.toNel.toList).foldLeft(request) { case (r, (k, v)) => r.tag(k, v) }

    private[interop] def mdc: Log.Mdc =
      Log.Mdc.fromMap(request.tags.map { case (k, v) => k -> v.toString })

  }
}
