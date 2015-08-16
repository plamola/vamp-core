package io.vamp.core.container_driver.marathon

import com.typesafe.scalalogging.Logger
import io.vamp.common.http.RestClient
import io.vamp.core.container_driver._
import io.vamp.core.container_driver.docker.DockerPortMapping
import io.vamp.core.container_driver.marathon.api.{Docker, _}
import io.vamp.core.container_driver.notification.UndefinedMarathonApplication
import io.vamp.core.model.artifact._
import org.json4s.{Extraction, DefaultFormats, Formats}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

object MarathonDriver {

  object Schema extends Enumeration {
    val Docker, Cmd, Command = Value
  }

  MarathonDriver.Schema.values
}

class MarathonDriver(ec: ExecutionContext, url: String) extends AbstractContainerDriver(ec) {

  private val logger = Logger(LoggerFactory.getLogger(classOf[MarathonDriver]))

  def info: Future[ContainerInfo] = RestClient.get[Any](s"$url/v2/info").map { response =>
    ContainerInfo("marathon", response)
  }

  def all: Future[List[ContainerService]] = {
    logger.debug(s"marathon get all")
    RestClient.get[Apps](s"$url/v2/apps?embed=apps.tasks").map(apps => apps.apps.filter(app => processable(app.id)).map(app => containerService(app)))
  }
  
  def deploy(deploymentName: String, breedName: String, service: DeploymentService, environment: Map[String, String], portMappings: List[DockerPortMapping], update: Boolean, valueProvider: ValueReference => String, dialects: Map[Dialect.Value, Any]) = {
    validateSchemaSupport(service.breed.deployable.schema, MarathonDriver.Schema)

    val id = appId(deploymentName, breedName)
    if (update) logger.info(s"marathon update app: $id") else logger.info(s"marathon create app: $id")

  val app = MarathonApp(id, container(portMappings, service), service.scale.get.instances, service.scale.get.cpu, service.scale.get.memory, environment, cmd(service))
    val dialect  = dialects.getOrElse(Dialect.Marathon, Map.empty)
    val interPolatedDialect = interpolate(dialect, valueProvider)
    val payload = requestPayload(app, interPolatedDialect)

    if (update)
      RestClient.put[Any](s"$url/v2/apps/${app.id}", payload)
    else
      RestClient.post[Any](s"$url/v2/apps", payload)
  }

  private def container(portMappings: List[DockerPortMapping], service: DeploymentService): Option[Container] = service.breed.deployable match {
    case Deployable(schema, Some(definition)) if MarathonDriver.Schema.Docker.toString.compareToIgnoreCase(schema) == 0 => Some(Container(Docker(definition, portMappings)))
    case _ => None
  }

  private def cmd(service: DeploymentService): Option[String] = service.breed.deployable match {
    case Deployable(schema, Some(definition)) if MarathonDriver.Schema.Cmd.toString.compareToIgnoreCase(schema) == 0 || MarathonDriver.Schema.Command.toString.compareToIgnoreCase(schema) == 0 => Some(definition)
    case _ => None
  }

  private def requestPayload(app: MarathonApp, dialect: Any) = {
    (app.container, app.cmd, dialect) match {
      case (None, None, map: Map[_, _]) if map.asInstanceOf[Map[String, _]].get("cmd").nonEmpty =>
      case (None, None, _) => throwException(UndefinedMarathonApplication)
      case _ =>
    }

    mergeWithDialect(dialect, app)
  }

  def undeploy(deployment: String, breed: String) = {
    val id = appId(deployment, breed)
    logger.info(s"marathon delete app: $id")
    RestClient.delete(s"$url/v2/apps/$id")
  }

  private def containerService(app: App): ContainerService =
    ContainerService(nameMatcher(app.id), DefaultScale("", app.cpus, app.mem, app.instances), app.tasks.map(task => ContainerServer(task.id, task.host, task.ports, task.startedAt.isDefined)))
}

