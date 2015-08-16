package io.vamp.core.container_driver

import _root_.io.vamp.common.akka._
import akka.actor.Props
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.vitals.InfoRequest
import io.vamp.core.container_driver.docker.DockerPortMapping
import io.vamp.core.container_driver.notification.{ContainerDriverNotificationProvider, ContainerResponseError, UnsupportedContainerDriverRequest}
import io.vamp.core.model.artifact.{Dialect, ValueReference, DeploymentService}
import io.vamp.core.model.resolver.{DeploymentTraitResolver}

import scala.concurrent.duration._

object ContainerDriverActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.core.container-driver.response-timeout").seconds)

  def props(args: Any*): Props = Props(classOf[ContainerDriverActor], args: _*)

  trait ContainerDriveMessage

  object All extends ContainerDriveMessage

  case class Deploy(deploymentName: String, breedName: String, service: DeploymentService, environment: Map[String, String], portMappings: List[DockerPortMapping], update: Boolean, valueProvider: ValueReference => String, dialects: Map[Dialect.Value, Any]) extends ContainerDriveMessage

  case class Undeploy(deploymentName: String, breedName: String) extends ContainerDriveMessage

}

class ContainerDriverActor(driver: ContainerDriver) extends CommonReplyActor with ContainerDriverNotificationProvider with DeploymentTraitResolver {

  import io.vamp.core.container_driver.ContainerDriverActor._

  implicit val timeout = ContainerDriverActor.timeout

  override protected def requestType: Class[_] = classOf[ContainerDriveMessage]

  override protected def errorRequest(request: Any): RequestError = UnsupportedContainerDriverRequest(request)

  def reply(request: Any) = try {
    request match {
      case InfoRequest => offload(driver.info, classOf[ContainerResponseError])
      case All => offload(driver.all, classOf[ContainerResponseError])
      case Deploy(deploymentName, breedName, service, environment, portMappings, update, valueProvider, dialects) =>
        offload(driver.deploy(deploymentName, breedName, service, environment, portMappings, update, valueProvider, dialects), classOf[ContainerResponseError])
      case Undeploy(deploymentName, breedName) => offload(driver.undeploy(deploymentName, breedName), classOf[ContainerResponseError])
      case _ => unsupported(request)
    }
  } catch {
    case e: Throwable => reportException(ContainerResponseError(e))
  }
}

