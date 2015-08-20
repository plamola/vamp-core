package io.vamp.core.persistence

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import io.vamp.common.akka._
import io.vamp.common.notification.NotificationErrorException
import io.vamp.core.model.artifact._
import io.vamp.core.model.event.Event
import io.vamp.core.model.workflow.{ScheduledWorkflow, Workflow}
import io.vamp.core.persistence.PersistenceActor._
import io.vamp.core.persistence.notification.{ArtifactArchivingError, PersistenceOperationFailure}
import io.vamp.core.pulse.PulseActor

import scala.language.postfixOps

object ArchivePersistenceActor extends ActorDescription {
  def props(args: Any*): Props = Props(classOf[ArchivePersistenceActor], args: _*)
}

class ArchivePersistenceActor(persistenceActor: ActorRef) extends DecoratorPersistenceActor(persistenceActor) {

  override protected def infoMap() = Map("archive" -> true)

  override protected def create(artifact: Artifact, source: Option[String], ignoreIfExists: Boolean) = offload(persistenceActor ? Create(artifact, source, ignoreIfExists)) match {
    case a: Artifact => archiveCreate(a, source)
    case e: NotificationErrorException => throw e
    case other => throwException(PersistenceOperationFailure(other))
  }

  override protected def update(artifact: Artifact, source: Option[String], create: Boolean) = offload(persistenceActor ? Update(artifact, source, create)) match {
    case a: Artifact => if (create) archiveCreate(a, source) else archiveUpdate(a, source)
    case e: NotificationErrorException => throw e
    case other => throwException(PersistenceOperationFailure(other))
  }

  override protected def delete(name: String, `type`: Class[_ <: Artifact]) = offload(persistenceActor ? Delete(name, `type`)) match {
    case a: Artifact => archiveDelete(a)
    case e: NotificationErrorException => throw e
    case other => throwException(PersistenceOperationFailure(other))
  }

  private def archiveCreate(artifact: Artifact, source: Option[String]): Artifact =
    if (source.isDefined) archive(artifact, source, s"archiving:create") else artifact

  private def archiveUpdate(artifact: Artifact, source: Option[String]): Artifact =
    if (source.isDefined) archive(artifact, source, s"archiving:update") else artifact

  private def archiveDelete(artifact: Artifact): Artifact = archive(artifact, None, s"archiving:delete")

  private def archive(artifact: Artifact, source: Option[String], archiveTag: String) = {
    tagFor(artifact) match {
      case Some(artifactTag) =>
        val event = Event(Set(artifactTag, archiveTag), source)
        log.debug(s"Archive event with tags: ${event.tags}")
        actorFor(PulseActor) ? PulseActor.Publish(event)
      case _ =>
        reportException(ArtifactArchivingError(artifact))
    }
    artifact
  }

  private def tagFor(artifact: Artifact): Option[String] = artifact.getClass match {
    case t if classOf[Deployment].isAssignableFrom(t) => Some(s"deployments:${artifact.name}")
    case t if classOf[Breed].isAssignableFrom(t) => Some(s"breeds:${artifact.name}")
    case t if classOf[Blueprint].isAssignableFrom(t) => Some(s"blueprints:${artifact.name}")
    case t if classOf[Sla].isAssignableFrom(t) => Some(s"slas:${artifact.name}")
    case t if classOf[Scale].isAssignableFrom(t) => Some(s"scales:${artifact.name}")
    case t if classOf[Escalation].isAssignableFrom(t) => Some(s"escalations:${artifact.name}")
    case t if classOf[Routing].isAssignableFrom(t) => Some(s"routings:${artifact.name}")
    case t if classOf[Filter].isAssignableFrom(t) => Some(s"filters:${artifact.name}")
    case t if classOf[Workflow].isAssignableFrom(t) => Some(s"workflows:${artifact.name}")
    case t if classOf[ScheduledWorkflow].isAssignableFrom(t) => Some(s"scheduled-workflows:${artifact.name}")
    case request => None
  }
}

