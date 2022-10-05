package thurloe.notification

import akka.actor.SupervisorStrategy.{Escalate, Stop}
import akka.actor._
import akka.pattern._
import com.sendgrid.SendGrid.Response
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.google.GooglePubSubDAO
import org.broadinstitute.dsde.workbench.google.GooglePubSubDAO.PubSubMessage
import org.broadinstitute.dsde.workbench.model.Notifications._
import org.broadinstitute.dsde.workbench.model.WorkbenchUserId
import spray.json._
import thurloe.dataaccess.SendGridDAO
import thurloe.database.{DataAccess, KeyNotFoundException, ThurloeDatabaseConnector}
import thurloe.notification.NotificationMonitor.StartMonitorPass
import thurloe.notification.NotificationMonitorSupervisor._

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}

/**
 * Created by dvoet on 12/6/16.
 */
object NotificationMonitorSupervisor {
  sealed trait NotificationMonitorSupervisorMessage
  case object Init extends NotificationMonitorSupervisorMessage
  case object Start extends NotificationMonitorSupervisorMessage

  def props(pollInterval: FiniteDuration,
            pollIntervalJitter: FiniteDuration,
            pubSubDao: GooglePubSubDAO,
            pubSubTopicName: String,
            pubSubSubscriptionName: String,
            workerCount: Int,
            sendGridDAO: SendGridDAO,
            templateIdsByType: Map[String, String],
            fireCloudPortalUrl: String)(implicit executionContext: ExecutionContext): Props =
    Props(
      new NotificationMonitorSupervisor(pollInterval,
                                        pollIntervalJitter,
                                        pubSubDao,
                                        pubSubTopicName,
                                        pubSubSubscriptionName,
                                        workerCount,
                                        sendGridDAO,
                                        templateIdsByType,
                                        fireCloudPortalUrl)
    )
}

class NotificationMonitorSupervisor(val pollInterval: FiniteDuration,
                                    pollIntervalJitter: FiniteDuration,
                                    pubSubDao: GooglePubSubDAO,
                                    pubSubTopicName: String,
                                    pubSubSubscriptionName: String,
                                    workerCount: Int,
                                    sendGridDAO: SendGridDAO,
                                    templateIdsByType: Map[String, String],
                                    fireCloudPortalUrl: String)(implicit executionContext: ExecutionContext)
    extends Actor
    with LazyLogging {

  self ! Init

  override def receive = {
    case Init              => init pipeTo self
    case Start             => for (_ <- 1 to workerCount) startOne()
    case Status.Failure(t) => logger.error("error initializing notification monitor", t)
  }

  def init =
    for {
      _ <- pubSubDao.createTopic(pubSubTopicName)
      _ <- pubSubDao.createSubscription(pubSubTopicName, pubSubSubscriptionName)
    } yield Start

  def startOne(): Unit = {
    logger.info("starting NotificationMonitorActor")
    context.actorOf(
      NotificationMonitor.props(pollInterval,
                                pollIntervalJitter,
                                pubSubDao,
                                pubSubSubscriptionName,
                                sendGridDAO,
                                templateIdsByType,
                                fireCloudPortalUrl,
                                ThurloeDatabaseConnector)
    )
  }

  override val supervisorStrategy =
    OneForOneStrategy() {
      case e => {
        logger.error("error sending notification", e)
        // start one to replace the error, stop the errored child so that we also drop its mailbox (i.e. restart not good enough)
        startOne()
        Stop
      }
    }

}

object NotificationMonitor {
  case object StartMonitorPass

  def props(pollInterval: FiniteDuration,
            pollIntervalJitter: FiniteDuration,
            pubSubDao: GooglePubSubDAO,
            pubSubSubscriptionName: String,
            sendGridDAO: SendGridDAO,
            templateIdsByType: Map[String, String],
            fireCloudPortalUrl: String,
            dataAccess: DataAccess)(implicit executionContext: ExecutionContext): Props =
    Props(
      new NotificationMonitorActor(pollInterval,
                                   pollIntervalJitter,
                                   pubSubDao,
                                   pubSubSubscriptionName,
                                   sendGridDAO,
                                   templateIdsByType,
                                   fireCloudPortalUrl,
                                   dataAccess)
    )

  val notificationsOffKey = "notifications.off"
}

class NotificationMonitorActor(val pollInterval: FiniteDuration,
                               pollIntervalJitter: FiniteDuration,
                               pubSubDao: GooglePubSubDAO,
                               pubSubSubscriptionName: String,
                               sendGridDAO: SendGridDAO,
                               templateIdsByType: Map[String, String],
                               fireCloudPortalUrl: String,
                               dataAccess: DataAccess)(implicit executionContext: ExecutionContext)
    extends Actor
    with LazyLogging {

  self ! StartMonitorPass

  // fail safe in case this actor is idle too long but not too fast (1 second lower limit)
  context.setReceiveTimeout(max((pollInterval + pollIntervalJitter) * 10, 1.second))

  private def max(durations: FiniteDuration*): FiniteDuration = durations.max

  override def receive = {
    case StartMonitorPass =>
      // start the process by pulling a message and sending it back to self
      pubSubDao.pullMessages(pubSubSubscriptionName, 1).map(_.headOption) pipeTo self

    case Some(message: PubSubMessage) =>
      maybeSendNotification(message) pipeTo self

    case None =>
      // there was no message so wait and try again
      val nextTime = pollInterval + pollIntervalJitter * Math.random()
      context.system.scheduler.scheduleOnce(nextTime.asInstanceOf[FiniteDuration], self, StartMonitorPass)

    case (responseOption: Option[_], message: PubSubMessage) =>
      pubSubDao
        .acknowledgeMessagesById(pubSubSubscriptionName, Seq(message.ackId))
        .map(_ => StartMonitorPass) pipeTo self
      responseOption match {
        case Some(response: Response) if !response.getStatus =>
          logger.error(
            s"could not send notification ${message.contents}, sendgrid code: ${response.getCode}, sendgrid message: ${response.getMessage}"
          )
        case _ => // log nothing
      }

    case Status.Failure(t) =>
      // an error happened in some future, let the supervisor handle it
      throw t

    case ReceiveTimeout =>
      throw new Exception("NotificationMonitorActor has received no messages for too long")
  }

  def maybeSendNotification(message: PubSubMessage): Future[(Option[Response], PubSubMessage)] = {
    val notification = message.contents.parseJson.convertTo[Notification]
    lookupShouldNotify(notification) flatMap { shouldNotify =>
      if (shouldNotify) {
        sendGridDAO.sendNotifications(List(toThurloeNotification(notification))).map(_.headOption) recoverWith {
          case e: KeyNotFoundException =>
            logger.info(s"Unable to send notification due to missing key: ${e.getMessage}")
            Future.successful(None)
          case e => throw e
        }
      } else {
        Future.successful(None)
      }
    } map { responseOption => (responseOption, message) }
  }

  def lookupShouldNotify(notification: Notification): Future[Boolean] =
    notification match {
      //For workspace notifications, there are three tiers of preferences to check:
      // 1. has the user disabled *all* notifications for their account?
      //     key format: notifications.off
      // 2. has the user disabled the notification at the type-level?
      //     key format: notifications/<notification class name>
      // 3. has the user disabled the notification for the specified workspace?
      //     key format: notifications/<notification class name>/<workspace namespace>/<workspace name>
      case wsNotification: WorkspaceNotification =>
        val baseKey = s"notifications/${wsNotification.getClass.getSimpleName}"
        booleanLookup(wsNotification.recipientUserId, NotificationMonitor.notificationsOffKey, false) flatMap {
          case false =>
            booleanLookup(wsNotification.recipientUserId, baseKey, true) flatMap {
              case false => Future.successful(false)
              case true  => booleanLookup(wsNotification.recipientUserId, notification.key, true)
            }
          case true => Future.successful(false) // notifications off for this user
        }
      case UserNotification(recipientId) =>
        booleanLookup(recipientId, NotificationMonitor.notificationsOffKey, false) flatMap {
          case false => booleanLookup(recipientId, notification.key, true)
          case true  => Future.successful(false) // notifications off for this user
        }
      case _ => Future.successful(true)
    }

  def booleanLookup(userId: WorkbenchUserId, key: String, defaultValue: Boolean): Future[Boolean] =
    dataAccess.lookup(userId.value, key).map(kvp => kvp.keyValuePair.value.toBoolean).recover {
      case _: KeyNotFoundException     => defaultValue
      case _: IllegalArgumentException => defaultValue
    }

  def workspacePortalUrl(workspaceName: WorkspaceName): String =
    s"$fireCloudPortalUrl/#workspaces/${workspaceName.namespace}/${workspaceName.name}"
  def submissionUrl(workspaceName: WorkspaceName, submissionId: String): String =
    s"$fireCloudPortalUrl/#workspaces/${workspaceName.namespace}/${workspaceName.name}/job_history/$submissionId"
  def groupManagementUrl(groupName: String): String = s"$fireCloudPortalUrl/#groups/${groupName}"
  def bucketUrl(bucketName: String): String = s"https://console.cloud.google.com/storage/browser/${bucketName}"

  def toThurloeNotification(notification: Notification): thurloe.service.Notification = {
    val templateId = templateIdsByType(notification.getClass.getSimpleName)

    notification match {
      case ActivationNotification(recipentUserId) =>
        thurloe.service.Notification(Option(recipentUserId), None, None, templateId, Map.empty, Map.empty, Map.empty)

      case WorkspaceAddedNotification(recipientUserId, accessLevel, workspaceName, workspaceOwnerId) =>
        thurloe.service.Notification(
          Option(recipientUserId),
          None,
          Option(Set(workspaceOwnerId)),
          templateId,
          Map("accessLevel" -> accessLevel,
              "namespace" -> workspaceName.namespace,
              "name" -> workspaceName.name,
              "wsUrl" -> workspacePortalUrl(workspaceName)),
          Map("originEmail" -> workspaceOwnerId),
          Map("userNameFL" -> workspaceOwnerId)
        )

      case WorkspaceInvitedNotification(recipientUserEmail, requesterId, workspaceName, bucketName) =>
        thurloe.service.Notification(
          None,
          Option(recipientUserEmail),
          Option(Set(requesterId)),
          templateId,
          Map("wsName" -> workspaceName.name,
              "wsUrl" -> workspacePortalUrl(workspaceName),
              "bucketName" -> bucketName,
              "bucketUrl" -> bucketUrl(bucketName)),
          Map("originEmail" -> requesterId),
          Map("userNameFL" -> requesterId)
        )

      case WorkspaceRemovedNotification(recipientUserId, accessLevel, workspaceName, workspaceOwnerId) =>
        thurloe.service.Notification(
          Option(recipientUserId),
          None,
          Option(Set(workspaceOwnerId)),
          templateId,
          Map("accessLevel" -> accessLevel,
              "namespace" -> workspaceName.namespace,
              "name" -> workspaceName.name,
              "wsUrl" -> workspacePortalUrl(workspaceName)),
          Map("originEmail" -> workspaceOwnerId),
          Map("userNameFL" -> workspaceOwnerId)
        )

      case AbortedSubmissionNotification(recipientUserid,
                                         workspaceName,
                                         submissionId,
                                         dateSubmitted,
                                         workflowConfiguration,
                                         dataEntity,
                                         workflowCount,
                                         comment) =>
        thurloe.service.Notification(
          Option(recipientUserid),
          None,
          None,
          templateId,
          Map(
            "submissionUrl" -> submissionUrl(workspaceName, submissionId),
            "submissionId" -> submissionId,
            "dateSubmitted" -> dateSubmitted,
            "namespace" -> workspaceName.namespace,
            "name" -> workspaceName.name,
            "wsUrl" -> workspacePortalUrl(workspaceName),
            "workflowConfiguration" -> workflowConfiguration,
            "dataEntity" -> dataEntity,
            "workflowCount" -> workflowCount.toString,
            "comment" -> comment
          ),
          Map.empty,
          Map.empty
        )

      case SuccessfulSubmissionNotification(recipientUserid,
                                            workspaceName,
                                            submissionId,
                                            dateSubmitted,
                                            workflowConfiguration,
                                            dataEntity,
                                            workflowCount,
                                            comment) =>
        thurloe.service.Notification(
          Option(recipientUserid),
          None,
          None,
          templateId,
          Map(
            "submissionUrl" -> submissionUrl(workspaceName, submissionId),
            "submissionId" -> submissionId,
            "dateSubmitted" -> dateSubmitted,
            "namespace" -> workspaceName.namespace,
            "name" -> workspaceName.name,
            "wsUrl" -> workspacePortalUrl(workspaceName),
            "workflowConfiguration" -> workflowConfiguration,
            "dataEntity" -> dataEntity,
            "workflowCount" -> workflowCount.toString,
            "comment" -> comment
          ),
          Map.empty,
          Map.empty
        )

      case FailedSubmissionNotification(recipientUserid,
                                        workspaceName,
                                        submissionId,
                                        dateSubmitted,
                                        workflowConfiguration,
                                        dataEntity,
                                        workflowCount,
                                        comment) =>
        thurloe.service.Notification(
          Option(recipientUserid),
          None,
          None,
          templateId,
          Map(
            "submissionUrl" -> submissionUrl(workspaceName, submissionId),
            "submissionId" -> submissionId,
            "dateSubmitted" -> dateSubmitted,
            "namespace" -> workspaceName.namespace,
            "name" -> workspaceName.name,
            "wsUrl" -> workspacePortalUrl(workspaceName),
            "workflowConfiguration" -> workflowConfiguration,
            "dataEntity" -> dataEntity,
            "workflowCount" -> workflowCount.toString,
            "comment" -> comment
          ),
          Map.empty,
          Map.empty
        )

      case WorkspaceChangedNotification(recipientUserId, workspaceName) =>
        thurloe.service.Notification(Option(recipientUserId),
                                     None,
                                     None,
                                     templateId,
                                     Map("wsName" -> workspaceName.name, "wsUrl" -> workspacePortalUrl(workspaceName)),
                                     Map.empty,
                                     Map.empty)

      case GroupAccessRequestNotification(recipientUserId, groupName, replyTos, requesterId) =>
        thurloe.service.Notification(
          Option(recipientUserId),
          None,
          Option(replyTos),
          templateId,
          Map("groupName" -> groupName, "groupUrl" -> groupManagementUrl(groupName)),
          Map("originEmail" -> requesterId),
          Map("userNameFL" -> requesterId)
        )
    }
  }

  override val supervisorStrategy =
    OneForOneStrategy() {
      case _ => {
        Escalate
      }
    }
}
