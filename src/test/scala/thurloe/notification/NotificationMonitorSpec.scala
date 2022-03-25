package thurloe.notification

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.broadinstitute.dsde.rawls.model.Notifications.{
  NotificationFormat,
  WorkspaceAddedNotification,
  WorkspaceInvitedNotification,
  WorkspaceRemovedNotification
}
import org.broadinstitute.dsde.rawls.model.{RawlsUserEmail, RawlsUserSubjectId, WorkspaceName}
import org.broadinstitute.dsde.workbench.google.mock.MockGooglePubSubDAO
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import thurloe.dataaccess.{MockSendGridDAO, MockSendGridDAOWithException}
import thurloe.database.ThurloeDatabaseConnector
import thurloe.service.{KeyValuePair, UserKeyValuePairs}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

/**
 * Created by dvoet on 12/12/16.
 */
class NotificationMonitorSpec(_system: ActorSystem)
    extends TestKit(_system)
    with AnyFlatSpecLike
    with Matchers
    with BeforeAndAfterAll {
  def this() = this(ActorSystem("NotificationMonitorSpec"))

  override def beforeAll(): Unit =
    super.beforeAll()

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  "NotificationMonitor" should "send notifications" in {
    val pubsubDao = new MockGooglePubSubDAO
    val topic = "topic"

    val workerCount = 10
    val sendGridDAO = new MockSendGridDAO
    system.actorOf(
      NotificationMonitorSupervisor.props(
        10 milliseconds,
        0 milliseconds,
        pubsubDao,
        topic,
        "subscription",
        workerCount,
        sendGridDAO,
        Map("WorkspaceInvitedNotification" -> "valid_notification_id1"),
        "foo"
      )
    )

    // NotificationMonitorSupervisor creates the topic, need to wait for it to exist before publishing messages
    awaitCond(pubsubDao.topics.contains(topic), 10 seconds)
    val testNotifications =
      (for (i <- 0 until workerCount * 4)
        yield WorkspaceInvitedNotification(RawlsUserEmail(s"foo$i"),
                                           RawlsUserSubjectId(s"bar$i"),
                                           WorkspaceName("namespace", "name"),
                                           "some-bucket-name"))

    // wait for all the messages to be published and throw an error if one happens (i.e. use Await.result not Await.ready)
    Await.result(pubsubDao.publishMessages(topic, testNotifications.map(NotificationFormat.write(_).compactPrint)),
                 Duration.Inf)
    awaitAssert(
      testNotifications
        .map(n => n.recipientUserEmail.value)
        .toSet should contain theSameElementsAs (sendGridDAO.emails.asScala.map(email => email.getTos.head).toSet),
      10 seconds
    )

    awaitAssert(
      assertResult(testNotifications.map(n => n.requesterId.value).toSet) {
        sendGridDAO.emails.asScala.map(email => email.getHeaders.get("Reply-To")).toSet
      },
      10 seconds
    )
    awaitAssert(assertResult(testNotifications.size)(pubsubDao.acks.size()), 10 seconds)
  }

  it should "maybe send notifications" in {
    val pubsubDao = new MockGooglePubSubDAO
    val topic = "topic"

    val workerCount = 1
    val sendGridDAO = new MockSendGridDAO
    system.actorOf(
      NotificationMonitorSupervisor.props(
        10 milliseconds,
        0 milliseconds,
        pubsubDao,
        topic,
        "subscription",
        workerCount,
        sendGridDAO,
        Map("WorkspaceRemovedNotification" -> "valid_notification_id1",
            "WorkspaceAddedNotification" -> "valid_notification_id1"),
        "foo"
      )
    )

    // NotificationMonitorSupervisor creates the topic, need to wait for it to exist before publishing messages
    awaitCond(pubsubDao.topics.contains(topic), 10 seconds)

    val userId = sendGridDAO.testUserId1
    val workspaceName = WorkspaceName("ws_ns", "ws_n")
    val removedNotification =
      WorkspaceRemovedNotification(RawlsUserSubjectId(userId), "foo", workspaceName, RawlsUserSubjectId("a_user_id2"))
    val addedNotification =
      WorkspaceAddedNotification(RawlsUserSubjectId(userId), "foo", workspaceName, RawlsUserSubjectId("a_user_id2"))

    Await.result(
      ThurloeDatabaseConnector.set(UserKeyValuePairs(userId, Seq(KeyValuePair(addedNotification.key, "false")))),
      Duration.Inf
    )

    // wait for all the messages to be published and throw an error if one happens (i.e. use Await.result not Await.ready)
    val testNotifications = Seq(removedNotification, addedNotification)
    Await.result(pubsubDao.publishMessages(topic, testNotifications.map(NotificationFormat.write(_).compactPrint)),
                 Duration.Inf)
    awaitAssert(assertResult(testNotifications.size)(pubsubDao.acks.size()), 10 seconds)
    assertResult(1) {
      sendGridDAO.emails.size()
    }
  }

  it should "not send notifications when they are off" in {
    val pubsubDao = new MockGooglePubSubDAO
    val topic = "topic"

    val workerCount = 1
    val sendGridDAO = new MockSendGridDAO
    system.actorOf(
      NotificationMonitorSupervisor.props(
        10 milliseconds,
        0 milliseconds,
        pubsubDao,
        topic,
        "subscription",
        workerCount,
        sendGridDAO,
        Map("WorkspaceRemovedNotification" -> "valid_notification_id1",
            "WorkspaceAddedNotification" -> "valid_notification_id1"),
        "foo"
      )
    )

    // NotificationMonitorSupervisor creates the topic, need to wait for it to exist before publishing messages
    awaitCond(pubsubDao.topics.contains(topic), 10 seconds)

    val userId = sendGridDAO.testUserId1
    val workspaceName = WorkspaceName("ws_ns", "ws_n")
    val removedNotification =
      WorkspaceRemovedNotification(RawlsUserSubjectId(userId), "foo", workspaceName, RawlsUserSubjectId("a_user_id2"))
    val addedNotification =
      WorkspaceAddedNotification(RawlsUserSubjectId(userId), "foo", workspaceName, RawlsUserSubjectId("a_user_id2"))

    Await.result(ThurloeDatabaseConnector.set(
                   UserKeyValuePairs(userId, Seq(KeyValuePair(NotificationMonitor.notificationsOffKey, "true")))
                 ),
                 Duration.Inf)

    // wait for all the messages to be published and throw an error if one happens (i.e. use Await.result not Await.ready)
    val testNotifications = Seq(removedNotification, addedNotification)
    Await.result(pubsubDao.publishMessages(topic, testNotifications.map(NotificationFormat.write(_).compactPrint)),
                 Duration.Inf)
    awaitAssert(assertResult(testNotifications.size)(pubsubDao.acks.size()), 10 seconds)
    assertResult(0) {
      sendGridDAO.emails.size()
    }
  }

  it should "ignore SendGrid notifications when the monitor is unable to send them due to missing key/value pairs" in {
    val pubsubDao = new MockGooglePubSubDAO
    val topic = "topic"

    val workerCount = 1
    val sendGridDAO = new MockSendGridDAOWithException // throws an KeyNotFoundException when calling `sendNotifications`
    system.actorOf(
      NotificationMonitorSupervisor.props(
        10 milliseconds,
        0 milliseconds,
        pubsubDao,
        topic,
        "subscription",
        workerCount,
        sendGridDAO,
        Map("WorkspaceRemovedNotification" -> "valid_notification_id1",
            "WorkspaceAddedNotification" -> "valid_notification_id1"),
        "foo"
      )
    )

    // NotificationMonitorSupervisor creates the topic, need to wait for it to exist before publishing messages
    awaitCond(pubsubDao.topics.contains(topic), 10 seconds)

    val userId = sendGridDAO.testUserId1
    val workspaceName = WorkspaceName("ws_ns", "ws_n")
    val removedNotification =
      WorkspaceRemovedNotification(RawlsUserSubjectId(userId), "foo", workspaceName, RawlsUserSubjectId("a_user_id2"))
    val addedNotification =
      WorkspaceAddedNotification(RawlsUserSubjectId(userId), "foo", workspaceName, RawlsUserSubjectId("a_user_id2"))

    // Make sure notifications are turned on for the test user (notificationsOffKey -> false)
    Await.result(ThurloeDatabaseConnector.set(
                   UserKeyValuePairs(userId, Seq(KeyValuePair(NotificationMonitor.notificationsOffKey, "false")))
                 ),
                 Duration.Inf)

    val testNotifications = Seq(removedNotification, addedNotification)
    Await.result(pubsubDao.publishMessages(topic, testNotifications.map(NotificationFormat.write(_).compactPrint)),
                 Duration.Inf)
    awaitAssert(assertResult(testNotifications.size)(pubsubDao.acks.size()), 10 seconds)
    assertResult(0) {
      sendGridDAO.emails.size()
    }
  }

}
