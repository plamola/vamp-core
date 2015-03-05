package io.magnetic.vamp_core.rest_api.serializer

import java.time.format.DateTimeFormatter

import io.magnetic.vamp_core.model.artifact.Deployment._
import io.magnetic.vamp_core.model.artifact.{Deployment, DeploymentState}
import io.magnetic.vamp_core.operation.notification.OperationNotificationProvider
import org.json4s._

object DeploymentSerializationFormat extends ArtifactSerializationFormat {
  override def customSerializers: List[ArtifactSerializer[_]] = super.customSerializers :+
    new DeploymentStateSerializer()

  override def fieldSerializers: List[ArtifactFieldSerializer[_]] = super.fieldSerializers :+
    new DeploymentStateFieldSerializer()
}

class DeploymentStateSerializer extends ArtifactSerializer[Deployment.State] with OperationNotificationProvider {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case state: Regular =>
      state.completed match {
        case None => JObject(JField("name", JString(state.getClass.getSimpleName)), JField("initiated", JString(state.initiated.format(DateTimeFormatter.ISO_INSTANT))))
        case Some(completed) => JObject(JField("name", JString(state.getClass.getSimpleName)), JField("initiated", JString(state.initiated.format(DateTimeFormatter.ISO_INSTANT))), JField("completed", JString(completed.format(DateTimeFormatter.ISO_INSTANT))))
      }
    case state: Error =>
      JObject(JField("name", JString(state.getClass.getSimpleName)), JField("initiated", JString(state.initiated.format(DateTimeFormatter.ISO_INSTANT))), JField("notification", JString(message(state.notification))))
  }
}

class DeploymentStateFieldSerializer extends ArtifactFieldSerializer[DeploymentState] {
  //override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] = ignore("state")
}