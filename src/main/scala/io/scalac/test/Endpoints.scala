package io.scalac.test

import sttp.tapir.*
import cats.effect.IO
import io.circe.generic.auto.*
import io.scalac.test.model.{Organization, UserWithContributions, ScalacServiceReply}
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics
import sttp.tapir.swagger.bundle.SwaggerInterpreter

object Endpoints:

  val githubInfo: PublicEndpoint[Organization, Unit, ScalacServiceReply, Any] = endpoint.get
    .in("org")
    .in(path[Organization]("orgName"))
    .in("contributors")
    .out(jsonBody[ScalacServiceReply])

  val githubInfoServerEndpoint: ServerEndpoint[Any, IO] =
    githubInfo.serverLogicSuccess(organization => IO.pure(Logics.githubInfoLogic(organization.name)))

  val apiEndpoints: List[ServerEndpoint[Any, IO]] = List(githubInfoServerEndpoint)

  val docEndpoints: List[ServerEndpoint[Any, IO]] = SwaggerInterpreter()
    .fromServerEndpoints[IO](apiEndpoints, "scalac-githubrank", "1.0.0")

  val prometheusMetrics: PrometheusMetrics[IO] = PrometheusMetrics.default[IO]()
  val metricsEndpoint: ServerEndpoint[Any, IO] = prometheusMetrics.metricsEndpoint

  val all: List[ServerEndpoint[Any, IO]] = apiEndpoints ++ docEndpoints ++ List(metricsEndpoint)
