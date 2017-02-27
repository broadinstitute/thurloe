package thurloe.notification

import akka.actor.SupervisorStrategy.{Escalate, Stop}
import akka.actor._
import com.sendgrid.SendGrid.Response
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.rawls.google.GooglePubSubDAO
import org.broadinstitute.dsde.rawls.google.GooglePubSubDAO.PubSubMessage
import thurloe.dataaccess.SendGridDAO
import thurloe.notification.NotificationMonitor.StartMonitorPass
import thurloe.notification.NotificationMonitorSupervisor._
import thurloe.service.Notification
import thurloe.service.ApiDataModelsJsonProtocol.notificationFormat

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

import akka.pattern._

import spray.json._
import spray.json.DefaultJsonProtocol._

/**
 * Created by dvoet on 12/6/16.
 */
object NotificationMonitorSupervisor {
  sealed trait NotificationMonitorSupervisorMessage
  case object Init extends NotificationMonitorSupervisorMessage
  case object Start extends NotificationMonitorSupervisorMessage

  def props(pollInterval: FiniteDuration, pollIntervalJitter: FiniteDuration, pubSubDao: GooglePubSubDAO, pubSubTopicName: String, pubSubSubscriptionName: String, workerCount: Int, sendGridDAO: SendGridDAO)(implicit executionContext: ExecutionContext): Props = {
    Props(new NotificationMonitorSupervisor(pollInterval, pollIntervalJitter, pubSubDao, pubSubTopicName, pubSubSubscriptionName, workerCount, sendGridDAO))
  }
}

class NotificationMonitorSupervisor(val pollInterval: FiniteDuration, pollIntervalJitter: FiniteDuration, pubSubDao: GooglePubSubDAO, pubSubTopicName: String, pubSubSubscriptionName: String, workerCount: Int, sendGridDAO: SendGridDAO)(implicit executionContext: ExecutionContext) extends Actor with LazyLogging {
  import context._

  self ! Init

  override def receive = {
    case Init => init pipeTo self
    case Start => for(i <- 1 to workerCount) startOne()
    case Status.Failure(t) => logger.error("error initializing google group sync monitor", t)
  }

  def init = {
    for {
      _ <- pubSubDao.createTopic(pubSubTopicName)
      _ <- pubSubDao.createSubscription(pubSubTopicName, pubSubSubscriptionName)
    } yield Start
  }

  def startOne(): Unit = {
    logger.info("starting NotificationMonitorActor")
    actorOf(NotificationMonitor.props(pollInterval, pollIntervalJitter, pubSubDao, pubSubSubscriptionName, sendGridDAO))
  }

  override val supervisorStrategy =
    OneForOneStrategy() {
      case e => {
        logger.error("error syncing google group", e)
        // start one to replace the error, stop the errored child so that we also drop its mailbox (i.e. restart not good enough)
        startOne()
        Stop
      }
    }

}

object NotificationMonitor {
  case object StartMonitorPass

  def props(pollInterval: FiniteDuration, pollIntervalJitter: FiniteDuration, pubSubDao: GooglePubSubDAO, pubSubSubscriptionName: String, sendGridDAO: SendGridDAO)(implicit executionContext: ExecutionContext): Props = {
    Props(new NotificationMonitorActor(pollInterval, pollIntervalJitter, pubSubDao, pubSubSubscriptionName, sendGridDAO))
  }
}

class NotificationMonitorActor(val pollInterval: FiniteDuration, pollIntervalJitter: FiniteDuration, pubSubDao: GooglePubSubDAO, pubSubSubscriptionName: String, sendGridDAO: SendGridDAO)(implicit executionContext: ExecutionContext) extends Actor with LazyLogging {
  import context._

  self ! StartMonitorPass

  // fail safe in case this actor is idle too long but not too fast (1 second lower limit)
  setReceiveTimeout(max((pollInterval + pollIntervalJitter) * 10, 1.second))

  private def max(durations: FiniteDuration*): FiniteDuration = durations.max

  override def receive = {
    case StartMonitorPass =>
      // start the process by pulling a message and sending it back to self
      pubSubDao.pullMessages(pubSubSubscriptionName, 1).map(_.headOption) pipeTo self

    case Some(message: PubSubMessage) =>
      val notification = message.contents.parseJson.convertTo[Notification]
      sendGridDAO.sendNotifications(List(notification)).map(responses => (responses.head, message)) pipeTo self

    case None =>
      // there was no message so wait and try again
      val nextTime = pollInterval + pollIntervalJitter * Math.random()
      system.scheduler.scheduleOnce(nextTime.asInstanceOf[FiniteDuration], self, StartMonitorPass)

    case (response: Response, message: PubSubMessage) =>
      pubSubDao.acknowledgeMessagesById(pubSubSubscriptionName, Seq(message.ackId)).map(_ => StartMonitorPass) pipeTo self
      if (!response.getStatus) {
        logger.error(s"could not send notification ${message.contents}, sendgrid code: ${response.getCode}, sendgrid message: ${response.getMessage}")
      }

    case Status.Failure(t) =>
      // an error happened in some future, let the supervisor handle it
      throw t

    case ReceiveTimeout =>
      throw new Exception("NotificationMonitorActor has received no messages for too long")
  }

  override val supervisorStrategy =
    OneForOneStrategy() {
      case e => {
        Escalate
      }
    }
}
