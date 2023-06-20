object Dependencies {

  import sbt.*

  object Evo {
    val catsHelper = "com.evolutiongaming" %% "cats-helper" % "3.6.0"
    val scache     = "com.evolution"       %% "scache"      % "5.1.1"
    val retry      = "com.evolutiongaming" %% "retry"       % "3.0.2"
  }

  object Sttp {
    private val version = "3.8.13"
    private val group   = "com.softwaremill.sttp.client3"
    val core            = group %% "core"               % version
    val http4s          = group %% "http4s-backend"     % version
    val prometheus      = group %% "prometheus-backend" % version exclude ("io.prometheus", "simpleclient")
  }

  object Http4s {

    object Blaze {
      private val version = "0.23.15"
      val client          = "org.http4s" %% "http4s-blaze-client" % version
    }

  }

  object Prometheus {
    private val version = "0.16.0"
    val client          = "io.prometheus" % "simpleclient" % version
  }

}
