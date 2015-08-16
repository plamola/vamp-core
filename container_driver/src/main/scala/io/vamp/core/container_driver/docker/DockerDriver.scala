package io.vamp.core.container_driver.docker

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import io.vamp.core.container_driver._
import io.vamp.core.container_driver.docker.wrapper.Create.Response
import io.vamp.core.container_driver.docker.wrapper._
import io.vamp.core.container_driver.docker.wrapper.model._
import io.vamp.core.container_driver.notification.UndefinedDockerImage
import io.vamp.core.model.artifact._
import org.slf4j.LoggerFactory

import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

object DockerDriver {

  object Schema extends Enumeration {
    val Docker = Value
  }

}

class DockerDriver(ec: ExecutionContext) extends AbstractContainerDriver(ec) with DummyScales with ContainerCache {

  override protected val nameDelimiter = "_"

  override protected def appId(deploymentName: String, breedName: String): String = s"/vamp$nameDelimiter${artifactName2Id(deploymentName)}$nameDelimiter${artifactName2Id(breedName)}"

  private val dockerMinimumMemory = 4 * 1024 * 1024

  private val logger = Logger(LoggerFactory.getLogger(classOf[DockerDriver]))

  private val docker = wrapper.Docker()

  private val defaultHost = ConfigFactory.load().getString("vamp.core.router-driver.host")

  def info: Future[ContainerInfo] = docker.info().map {
    logger.debug(s"docker get info :$docker")
    ContainerInfo("docker", _)
  }

  def all: Future[List[ContainerService]] = async {
    logger.debug(s"docker get all")

    // Get all containers & container details
    val details: List[Future[ContainerDetails]] =
      for {
        container: Container <- await(docker.containers.list())
        detail: Future[ContainerDetails] = getContainerDetails(container.id)
      } yield detail

    // Log which container have been found
    for (detail <- details) logContainerDetails(detail)

    val actualDetails: List[ContainerDetails] = await(Future.sequence(details))

    // Update the containerCache with missing containers (should only occur after a restart of core)
    actualDetails.foreach { detail =>
      if (processable(detail.name)) {
        findContainerIdInCache(detail.name) match {
          case None => addContainerToCache(detail.name, Future(detail.id))
          case Some(id) => //ignore
        }
      }
    }

    val containerDetails: List[ContainerService] = details2Services(for {detail <- actualDetails if processable(detail.name)} yield detail)
    logger.trace("[ALL]: " + containerDetails.toString())
    containerDetails
  }

  def deploy(deploymentName: String, breedName: String, service: DeploymentService, environment: Map[String, String], portMappings: List[DockerPortMapping], update: Boolean, valueProvider: ValueReference => String, dialects: Map[Dialect.Value, Any]) = {

    val containerName = appId(deploymentName, breedName)
    findContainerIdInCache(containerName) match {
      case None =>
        logger.info(s"[DEPLOY] Container $containerName does not exist, needs creating")
        validateSchemaSupport(service.breed.deployable.schema, DockerDriver.Schema)
        createAndStartContainer(containerName, environment, portMappings, service, dialects)

      case Some(found) if update =>
        logger.info(s"[DEPLOY] Container $containerName already exists, needs updating")
        addScale(Future(found), service.scale)
        Future(None)
      case Some(found) =>
        logger.warn(s"[DEPLOY] Container $containerName already exists, no action")
        Future(None)
    }
  }

  def undeploy(deployment: String, breedName: String) = {
    val containerName = appId(deployment, breedName)
    logger.info(s"docker delete app: $containerName")
    Future(
      findContainerIdInCache(containerName) match {
        case Some(found) =>
          logger.debug(s"[UNDEPLOY] Container $containerName found, trying to kill it")
          removeScale(containerName)
          val container = docker.containers.get(found)
          container.kill()
          container.delete()
          removeContainerIdFromCache(found)
        case None =>
          logger.debug(s"[UNDEPLOY] Container $containerName does not exist")
      }
    )
  }

  private def details2Services(details: List[ContainerDetails]): List[ContainerService] = {
    val serviceMap: Map[String, List[ContainerDetails]] = details.groupBy(x => x.name)
    serviceMap.map({ deployment =>
      val details = deployment._2.head
      val scale = getScale(details.id)
      val server = detail2Server(details)

      ContainerService(
        matching = nameMatcher(details.name),
        scale = scale,
        servers = (0 until scale.instances).map(i => server.copy(name = s"${server.name}[$i]")).toList)
    }).toList
  }

  private def detail2Server(cd: ContainerDetails): ContainerServer = {
    logger.trace(s"Details2Server containerDetails: $cd")
    ContainerServer(
      name = serverNameFromContainer(cd),
      host = if (cd.config.hostName.isEmpty) defaultHost else cd.config.hostName,
      ports = cd.networkSettings.ports.flatMap(port => port._2.map(e => e.hostPort)).toList,
      deployed = cd.state.running
    )
  }

  private def serverNameFromContainer(container: ContainerDetails): String = {
    val parts = container.name.split(nameDelimiter)
    if (parts.size == 3)
      parts(2)
    else
      container.name
  }

  /**
   * Creates and starts a container
   * If the image is not available, it will be pulled first
   */
    private def createAndStartContainer(containerName: String, environment: Map[String, String], portMappings: List[DockerPortMapping], service: DeploymentService, dialects: Map[Dialect.Value, Any]): Future[_] = async {
    val dialect: Map[Any, Any] = dialects.get(Dialect.Docker) match {
      case Some(entry: AnyRef) if entry.isInstanceOf[collection.Map[_, _]] => entry.asInstanceOf[collection.Map[Any, Any]].toMap
      case _ => Map[Any, Any]()
    }

    val dockerImageName = service.breed.deployable match {
      case Deployable(_, Some(definition)) => definition
      case _ => throwException(UndefinedDockerImage)
    }
    val allImages: List[Image] = await(docker.images.list())
    val taggedImages = allImages.filter(image => image.repoTags.contains(dockerImageName))

    if (taggedImages.isEmpty) {
      pullImage(dockerImageName, dialect)
    }

    val response = createDockerContainer(dialect, containerName, dockerImageName, environment, portMappings)

    addContainerToCache(containerName, getContainerFromResponseId(response))
    addScale(getContainerFromResponseId(response), service.scale)

    startDockerContainer(dialect, getContainerFromResponseId(response), portMappings, service.scale).onFailure { case ex =>
      logger.debug(s"Failed to start docker container: $ex")
      logger.trace(s"${ex.getStackTrace.mkString("\n")}")
    }
  }

  /**
   * Pull images from the repo
   */
  private def pullImage(name: String, dialect: Map[Any, Any]): Unit = {
    docker.images.pull(name, dialect).stream {
      case Pull.Status(msg) => logger.debug(s"[DEPLOY] pulling image $name: $msg")
      case Pull.Progress(msg, _, details) =>
        logger.debug(s"[DEPLOY] pulling image $name: $msg")
        details.foreach { dets =>
          logger.debug(s"[DEPLOY] pulling image $name: ${dets.bar}")
        }
      case Pull.Error(msg, _) => logger.error(s"[DEPLOY] pulling image $name failed: $msg")
    }
  }


  /**
   * Create a docker container (without starting it)
   * Tries to set the cpu shares & memory based on the supplied scale
   */
  private def createDockerContainer(dialect: Map[Any, Any], containerName: String, dockerImageName: String, env: Map[String, String], ports: List[DockerPortMapping]): Future[Response] = async {
    logger.debug(s"createDockerContainer :$containerName")

    var containerPrep = docker.containers.create(dockerImageName, dialect).name(containerName).hostName(defaultHost)

    for (v <- env) {
      logger.trace(s"[CreateDockerContainer] setting env ${v._1} = ${v._2}")
    }
    containerPrep = containerPrep.env(env.toSeq: _*)

    val exposedPorts = ports.map(p => {
      logger.debug(s"[CreateDockerContainer] exposed ports ${p.containerPort}")
      p.containerPort.toString
    })
    containerPrep = containerPrep.exposedPorts(exposedPorts.toSeq: _*)

    await(containerPrep())
  }

  /**
   * Start the container
   */
  private def startDockerContainer(dialect: Map[Any, Any], id: Future[String], ports: List[DockerPortMapping], serviceScale: Option[DefaultScale]): Future[_] = async {
    // Configure the container for starting

    id.onFailure { case ex =>
      logger.debug(s"Failed to create docker container: $ex")
      logger.trace(s"${ex.getStackTrace.mkString("\n")}")
    }

    var startPrep = docker.containers.get(await(id)).start(dialect)


    for (port <- ports) {
      logger.debug(s"[StartContainer] setting port: 0.0.0.0:${port.hostPort} -> ${port.containerPort}/tcp")
      startPrep = startPrep.portBind(wrapper.model.Port.Tcp(port.containerPort), PortBinding.local(port.hostPort))
    }
    startPrep = serviceScale match {
      case Some(scale) => startPrep.cpuShares(scale.cpu.toInt).memory(if (scale.memory.toLong < dockerMinimumMemory) dockerMinimumMemory else scale.memory.toLong)
      case None => startPrep
    }

    await(startPrep())

  }

  /**
   * Get details of a single container
   */
  private def getContainerDetails(id: String): Future[ContainerDetails] = async {
    await(docker.containers.get(id)())
  }

  /**
   * Get the containerId from a response
   */
  private def getContainerFromResponseId(response: Future[Response]): Future[String] = async {
    await(response).id
  }

  /**
   * Creates a nice debug log message
   */
  private def logContainerDetails(detail: Future[ContainerDetails]) = async {
    logger.debug(s"[ALL] name: ${await(detail).name} ${
      if (processable(await(detail).name)) {
        "[Monitored by VAMP]"
      } else {
        "[Non-vamp container]"
      }
    }")
  }

}

