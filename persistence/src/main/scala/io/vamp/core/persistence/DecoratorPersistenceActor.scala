package io.vamp.core.persistence

import akka.actor.ActorRef
import akka.pattern.ask
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.vitals.InfoRequest
import io.vamp.core.model.artifact._
import io.vamp.core.persistence.PersistenceActor._

import scala.language.postfixOps

abstract class DecoratorPersistenceActor(persistenceActor: ActorRef) extends PersistenceActor {

  protected def info() = offload(persistenceActor ? InfoRequest) match {
    case map: Map[_, _] => map.asInstanceOf[Map[Any, Any]] ++ infoMap()
    case other => other
  }

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int) = offload(persistenceActor ? All(`type`, page, perPage)).asInstanceOf[ArtifactResponseEnvelope]

  protected def create(artifact: Artifact, source: Option[String], ignoreIfExists: Boolean) = offload(persistenceActor ? Create(artifact, source, ignoreIfExists)).asInstanceOf[Artifact]

  protected def read(name: String, `type`: Class[_ <: Artifact]) = offload(persistenceActor ? Read(name, `type`)).asInstanceOf[Option[Artifact]]

  protected def update(artifact: Artifact, source: Option[String], create: Boolean) = offload(persistenceActor ? Update(artifact, source, create)).asInstanceOf[Artifact]

  protected def delete(name: String, `type`: Class[_ <: Artifact]) = offload(persistenceActor ? Delete(name, `type`)).asInstanceOf[Artifact]

  override protected def start() = persistenceActor ! Start

  override protected def shutdown() = persistenceActor ! Shutdown

  protected def infoMap(): Map[String, Any] = Map()
}