package io.vamp.core.operation.controller

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka._
import io.vamp.common.notification.NotificationProvider
import io.vamp.core.model.reader._
import io.vamp.core.pulse.PulseActor.{Publish, Query}
import io.vamp.core.pulse.{EventRequestEnvelope, PulseActor}

import scala.concurrent.Future
import scala.language.{existentials, postfixOps}

trait EventApiController {
  this: ActorSupport with FutureSupport with ExecutionContextProvider with NotificationProvider =>

  def publish(request: String)(implicit timeout: Timeout) = {
    val event = EventReader.read(request)
    (actorFor(PulseActor) ? Publish(event)).map(_ => event)
  }

  def query(request: String)(page: Int, perPage: Int)(implicit timeout: Timeout): Future[Any] = {
    actorFor(PulseActor) ? Query(EventRequestEnvelope(EventQueryReader.read(request), page, perPage))
  }
}