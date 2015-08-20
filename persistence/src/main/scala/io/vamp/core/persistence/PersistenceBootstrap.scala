package io.vamp.core.persistence

import akka.actor._
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.akka.{ActorSupport, Bootstrap}

class PersistenceExtension(system: ActorSystem) extends Extension {
  implicit val mysystem = system
  private val persistenceDescription = ConfigFactory.load().getString("vamp.core.persistence.storage-type") match {
    case "in-memory" => InMemoryPersistenceActor
    case "elasticsearch" => ElasticsearchPersistenceActor
    case _ => JdbcPersistenceActor
  }

  if (persistenceDescription == ElasticsearchPersistenceActor)
    ActorSupport.actorOf(ElasticsearchPersistenceInitializationActor) ! Start

  private val persistenceDriver = ActorSupport.actorOf(persistenceDescription)
  val persistenceActor = ActorSupport.actorOf(ArchivePersistenceActor, persistenceDescription) ! Start

  // Only here to keep same functionality, but it essentially does nothing.
  sys.addShutdownHook {
    if (persistenceDescription == ElasticsearchPersistenceActor)
      ActorSupport.actorFor(ElasticsearchPersistenceInitializationActor) ! Shutdown

    ActorSupport.actorFor(PersistenceActor) ! Shutdown
  }
}

object PersistenceExtension extends ExtensionId[PersistenceExtension] with ExtensionIdProvider {
  override def lookup() = PersistenceExtension
  override def createExtension(system: ExtendedActorSystem) = new PersistenceExtension(system)
}

trait PersistenceActorProvider { this: Actor =>
  val persistenceActor = PersistenceExtension(context.system).persistenceActor
}

