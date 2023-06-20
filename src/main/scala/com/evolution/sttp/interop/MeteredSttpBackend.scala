package com.evolution.sttp.interop

import cats.effect.syntax.all.*
import cats.effect.{Async, Resource, Sync}
import cats.syntax.all.*
import io.prometheus.client.{CollectorRegistry, Gauge}
import sttp.client3.prometheus.PrometheusBackend
import sttp.client3.{Request, Response}

import scala.concurrent.duration.*

object MeteredSttpBackend {

  def apply[F[_]: Async, P, C <: EvoContext[F]](
    delegate: EvoSttpBackend[F, P, C],
    registry: CollectorRegistry,
    global:   Labels,
  ): Resource[F, EvoSttpBackend[F, P, EvoContext.Metered[F]]] =
    for {
      _ <- delegate.state.traverse { state =>
        val gauge = Gauge
          .build()
          .name("sttp_http4s_wait_queue_depth")
          .help("A number of messages which are waiting to be sent because there are no idle connections")
          .labelNames(global.toSeq.map { case (k, _) => k }*)
          .register(registry)
        val log = for {
          queueDepth <- state.waitQueueDepth
          _ <- Sync[F].delay {
            gauge
              .labels(global.toSeq.map { case (_, v) => v }*)
              .set(queueDepth.toDouble)
          }
        } yield {}
        log.delayBy(1.second).foreverM.background
      }
    } yield new EvoSttpBackend[F, P, EvoContext.Metered[F]] {

      import PrometheusBackend.*
      import sttp.client3.prometheus.*
      import syntax.*

      val histogramBuckets: List[Double] = List(0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.0, 4.0, 8.0)

      def reqLabels(req: Request[?, ?]): List[(String, String)] =
        global
          .updated("method", req.method.method.toUpperCase)
          .updated("request", req.metricLabelValue.getOrElse(""))
          .toList

      def reqResLabels(req: Request[?, ?], res: Response[?]): List[(String, String)] =
        reqLabels(req).toMap
          .updated(
            "status",
            res.code match {
              case s if s.isInformational => "1xx"
              case s if s.isSuccess       => "2xx"
              case s if s.isRedirect      => "3xx"
              case s if s.isClientError   => "4xx"
              case s if s.isServerError   => "5xx"
              case _                      => res.code.toString
            },
          )
          .toList

      override private[interop] def backend = PrometheusBackend(
        delegate,
        requestToHistogramNameMapper = req => HistogramCollectorConfig(DefaultHistogramName, reqLabels(req), histogramBuckets).some,
        requestToInProgressGaugeNameMapper = req => CollectorConfig(DefaultRequestsInProgressGaugeName, reqLabels(req)).some,
        responseToSuccessCounterMapper = (req, res) => CollectorConfig(DefaultSuccessCounterName, reqResLabels(req, res)).some,
        responseToErrorCounterMapper = (req, res) => CollectorConfig(DefaultSuccessCounterName, reqResLabels(req, res)).some,
        requestToFailureCounterMapper = (req, _) => CollectorConfig(DefaultFailureCounterName, reqLabels(req)).some,
        requestToSizeSummaryMapper = req => CollectorConfig(DefaultRequestSizeName, reqLabels(req)).some,
        responseToSizeSummaryMapper = (req, res) => CollectorConfig(DefaultResponseSizeName, reqResLabels(req, res)).some,
        collectorRegistry = registry,
      )

      override private[interop] def state   = delegate.state
      override private[interop] def log     = delegate.log
      override private[interop] def context = EvoContext.Metered[F](registry, global)

    }

}
