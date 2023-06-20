package com.evolution.sttp.interop

import com.evolutiongaming.catshelper.Log
import io.prometheus.client.CollectorRegistry
import sttp.client3.{Request, Response, SttpBackend}
import org.http4s.blaze.client.BlazeClientState
import sttp.capabilities.Effect
import sttp.monad.MonadError

trait EvoSttpBackend[F[_], +P, +C <: EvoContext[F]] extends SttpBackend[F, P] {

  private[interop] def backend: SttpBackend[F, P]
  private[interop] def state:   Option[BlazeClientState[F]]

  private[interop] def log:     Log[F]
  private[interop] def context: C

  override def send[T, R >: P & Effect[F]](request: Request[T, R]): F[Response[T]] = backend.send(request)
  override def close():                                             F[Unit]        = backend.close()
  override def responseMonad:                                       MonadError[F]  = backend.responseMonad
}

trait EvoContext[F[_]]

object EvoContext {

  sealed case class Empty[F[_]]()                                              extends EvoContext[F]
  sealed case class Metered[F[_]](registry: CollectorRegistry, labels: Labels) extends EvoContext[F]

}
