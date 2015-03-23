package io.vamp.core.pulse_driver

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import io.vamp.common.http.RestClient
import io.vamp.core.model.artifact.{Deployment, DeploymentCluster}

import scala.concurrent.{ExecutionContext, Future}

trait PulseDriver {

  def lastSlaEventTimestamp(deployment: Deployment, cluster: DeploymentCluster): Future[OffsetDateTime]

  def responseTime(deployment: Deployment, cluster: DeploymentCluster, period: Long): Future[Long]
}

class DefaultPulseDriver(ec: ExecutionContext, url: String) extends PulseDriver {
  protected implicit val executionContext = ec

  def lastSlaEventTimestamp(deployment: Deployment, cluster: DeploymentCluster) = Future {
    OffsetDateTime.now().minus(1, ChronoUnit.HOURS)
  }

  def responseTime(deployment: Deployment, cluster: DeploymentCluster, period: Long) =
    RestClient.request[Any](s"GET $url/api/v1/events/get").map(result => result.asInstanceOf[Map[String, BigInt]].get("value").get.toLong)
}