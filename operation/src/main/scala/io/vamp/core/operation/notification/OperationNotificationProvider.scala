package io.vamp.core.operation.notification

import io.vamp.common.notification.{DefaultPackageMessageResolverProvider, LoggingNotificationProvider}

trait OperationNotificationProvider extends LoggingNotificationProvider with DefaultPackageMessageResolverProvider