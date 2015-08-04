package io.vamp.core.operation.deployment

import akka.actor.Props
import io.vamp.common.akka.{ActorDescription, SchedulerActor}
import io.vamp.core.operation.notification.OperationNotificationProvider

class DeploymentSynchronizationSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  def tick() = actorFor(DeploymentSynchronizationActor) ! DeploymentSynchronizationActor.SynchronizeAll
}

object DeploymentSynchronizationSchedulerActor extends ActorDescription {

  def props(args: Any*): Props = Props[DeploymentSynchronizationSchedulerActor]

}