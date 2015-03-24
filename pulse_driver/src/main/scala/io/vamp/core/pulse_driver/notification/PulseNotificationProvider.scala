package io.vamp.core.pulse_driver.notification

import java.time.OffsetDateTime

import io.vamp.common.akka.ActorSupport
import io.vamp.common.notification._
import io.vamp.common.pulse.api.Event
import io.vamp.core.pulse_driver.PulseDriverActor

trait PulseNotificationProvider extends LoggingNotificationProvider {
  this: MessageResolverProvider with ActorSupport =>

  def tags: List[String]

  override def info(notification: Notification): Unit = {
    actorFor(PulseDriverActor) ! eventOf(notification, List("notification", "info"))
    super.info(notification)
  }

  override def exception(notification: Notification): Exception = {
    actorFor(PulseDriverActor) ! eventOf(notification, List("notification", "error"))
    super.exception(notification)
  }

  def eventOf(notification: Notification, globalTags: List[String]): Event = notification match {
    case event: PulseEvent => Event(globalTags ++ tags ++ event.tags, notification, OffsetDateTime.now(), event.schema)
    case _ => Event(globalTags ++ tags, notification, OffsetDateTime.now(), "")
  }
}
