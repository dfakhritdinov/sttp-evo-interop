package com.evolution.sttp.interop

import sttp.client3.Response

import scala.util.control.NoStackTrace

final case class RetryException[T](toRetry: Response[T]) extends Exception with NoStackTrace
