package io.vamp.core.container_driver

import io.vamp.core.container_driver.docker.DockerPortMapping
import io.vamp.core.model.artifact.{DefaultScale, DeploymentService, Dialect, ValueReference}

import scala.concurrent.Future


case class ContainerInfo(`type`: String, container: Any)

case class ContainerService(matching: (String, String) => Boolean, scale: DefaultScale, servers: List[ContainerServer])

case class ContainerServer(name: String, host: String, ports: List[Int], deployed: Boolean)


trait ContainerDriver {

  def info: Future[ContainerInfo]

  def all: Future[List[ContainerService]]

  def deploy(deploymentName: String, breedName: String, service: DeploymentService, environment: Map[String, String], portMappings: List[DockerPortMapping], update: Boolean, valueProvider: ValueReference => String, dialects: Map[Dialect.Value, Any]): Future[Any]

  def undeploy(deploymentName: String, breedName: String): Future[Any]
}
