package io.vamp.core.container_driver.marathon.api

import io.vamp.core.container_driver.docker.DockerPortMapping


case class Docker(image: String, portMappings: List[DockerPortMapping], network: String = "BRIDGE")

case class Container(docker: Docker, `type`: String = "DOCKER")

case class MarathonApp(id: String, container: Option[Container], instances: Int, cpus: Double, mem: Double, env: Map[String, String], cmd: Option[String])
