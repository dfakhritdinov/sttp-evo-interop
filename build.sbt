import Dependencies.*

ThisBuild / scalaVersion := "2.13.11"
ThisBuild / scalacOptions ++= Seq(
  "-release:17",
  "-Xsource:3",
  "-Wconf:msg=\\$implicit\\$:s", // https://github.com/oleg-py/better-monadic-for/issues/50#issuecomment-788150296
)
ThisBuild / javacOptions ++= Seq("-source", "17", "-target", "17")

ThisBuild / organization := "com.evolution"
ThisBuild / name         := "sttp-interop"

ThisBuild / libraryDependencies := Seq(
  Evo.catsHelper,
  Evo.scache,
  Evo.retry,
  Sttp.core,
  Sttp.http4s,
  Sttp.prometheus,
  Http4s.Blaze.client,
  Prometheus.client,
)
