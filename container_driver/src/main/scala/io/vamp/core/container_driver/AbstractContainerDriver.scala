package io.vamp.core.container_driver

import io.vamp.common.crypto.Hash
import io.vamp.core.container_driver.notification.{ContainerDriverNotificationProvider, UnsupportedDeployableSchema}
import io.vamp.core.model.artifact.ValueReference
import io.vamp.core.model.resolver.DeploymentTraitResolver
import org.json4s.{DefaultFormats, Extraction, Formats}

import scala.concurrent.ExecutionContext

abstract class AbstractContainerDriver(ec: ExecutionContext) extends ContainerDriver with DeploymentTraitResolver with ContainerDriverNotificationProvider {
  protected implicit val executionContext = ec

  protected val nameDelimiter = "/"
  protected val idMatcher = """^(([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])\\.)*([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])$""".r

  protected def appId(deploymentName: String, breedName: String): String = s"$nameDelimiter${artifactName2Id(deploymentName)}$nameDelimiter${artifactName2Id(breedName)}"

  protected def processable(id: String): Boolean = id.split(nameDelimiter).size == 3

  protected def nameMatcher(id: String): (String, String) => Boolean = { (deployment: String, breed: String) => id == appId(deployment, breed) }

  protected def artifactName2Id(artifactName: String) = artifactName match {
    case idMatcher(_*) => artifactName
    case _ => Hash.hexSha1(artifactName).substring(0, 20)
  }

  protected def validateSchemaSupport(schema: String, enum: Enumeration) = {
    if (!enum.values.exists(en => en.toString.compareToIgnoreCase(schema) == 0))
      throwException(UnsupportedDeployableSchema(schema, enum.values.map(_.toString.toLowerCase).mkString(", ")))
  }

  protected def mergeWithDialect(app: Any, dialect: Any)(implicit formats: Formats = DefaultFormats) = {
    Extraction.decompose(dialect) merge Extraction.decompose(app)
  }

  protected def interpolate[T](dialect: T, valueResolver: ValueReference => String): T = {
    def visit(any: Any): Any = any match {
      case value: String => resolve(value, valueResolver)
      case list: List[_] => list.map(visit)
      case map: scala.collection.Map[_, _] => map.map {
        case (key, value) => key -> visit(value)
      }
      case _ => any
    }

    visit(dialect).asInstanceOf[T]
  }
}
