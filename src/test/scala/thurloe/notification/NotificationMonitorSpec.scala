package thurloe.notification

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.broadinstitute.dsde.rawls.google.MockGooglePubSubDAO
import org.broadinstitute.dsde.rawls.model.Notifications.{NotificationFormat, WorkspaceAddedNotification, WorkspaceInvitedNotification, WorkspaceRemovedNotification}
import org.broadinstitute.dsde.rawls.model.WorkspaceName
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import thurloe.dataaccess.MockSendGridDAO
import thurloe.database.ThurloeDatabaseConnector
import thurloe.service.{KeyValuePair, UserKeyValuePair, UserKeyValuePairs}

import scala.collection.convert.decorateAsScala._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by dvoet on 12/12/16.
 */
class NotificationMonitorSpec(_system: ActorSystem) extends TestKit(_system) with FlatSpecLike with Matchers with BeforeAndAfterAll {
  def this() = this(ActorSystem("NotificationMonitorSpec"))

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  "NotificationMonitor" should "send notifications" in {
    val pubsubDao = new MockGooglePubSubDAO
    val topic = "topic"

    val workerCount = 10
    val sendGridDAO = new MockSendGridDAO
    system.actorOf(NotificationMonitorSupervisor.props(10 milliseconds, 0 milliseconds, pubsubDao, topic, "subscription", workerCount, sendGridDAO, Map("WorkspaceInvitedNotification" -> "valid_notification_id1"), "foo", ThurloeDatabaseConnector))

    // NotificationMonitorSupervisor creates the topic, need to wait for it to exist before publishing messages
    awaitCond(pubsubDao.topics.contains(topic), 10 seconds)
    val testNotifications = (for (i <- 0 until workerCount * 4) yield WorkspaceInvitedNotification(s"foo$i", s"bar$i"))

    // wait for all the messages to be published and throw an error if one happens (i.e. use Await.result not Await.ready)
    Await.result(pubsubDao.publishMessages(topic, testNotifications.map(NotificationFormat.write(_).compactPrint)), Duration.Inf)
    awaitAssert(testNotifications.map(n => n.recipientUserEmail).toSet should contain theSameElementsAs(sendGridDAO.emails.asScala.map(email => email.getTos.head).toSet), 10 seconds)

    awaitAssert(assertResult(testNotifications.map(n => n.requesterId).toSet) {
      sendGridDAO.emails.asScala.map(email => email.getHeaders.get("Reply-To")).toSet
    }, 10 seconds)
    awaitAssert(assertResult(testNotifications.size) { pubsubDao.acks.size() }, 10 seconds)
  }

  it should "maybe send notifications" in {
    val pubsubDao = new MockGooglePubSubDAO
    val topic = "topic"

    val workerCount = 1
    val sendGridDAO = new MockSendGridDAO
    system.actorOf(NotificationMonitorSupervisor.props(10 milliseconds, 0 milliseconds, pubsubDao, topic, "subscription", workerCount, sendGridDAO, Map("WorkspaceRemovedNotification" -> "valid_notification_id1", "WorkspaceAddedNotification" -> "valid_notification_id1"), "foo", ThurloeDatabaseConnector))

    // NotificationMonitorSupervisor creates the topic, need to wait for it to exist before publishing messages
    awaitCond(pubsubDao.topics.contains(topic), 10 seconds)

    val userId = sendGridDAO.testUserId1
    val workspaceName = WorkspaceName("ws_ns", "ws_n")
    val removedNotification = WorkspaceRemovedNotification(userId, "foo", workspaceName, "a_user_id2")
    val addedNotification = WorkspaceAddedNotification(userId, "foo", workspaceName, "a_user_id2")

    Await.result(ThurloeDatabaseConnector.set(UserKeyValuePairs(userId, Seq(KeyValuePair(addedNotification.key, "false")))), Duration.Inf)

    // wait for all the messages to be published and throw an error if one happens (i.e. use Await.result not Await.ready)
    val testNotifications = Seq(removedNotification, addedNotification)
    Await.result(pubsubDao.publishMessages(topic, testNotifications.map(NotificationFormat.write(_).compactPrint)), Duration.Inf)
    awaitAssert(assertResult(testNotifications.size) { pubsubDao.acks.size() }, 10 seconds)
    assertResult(1) {
      sendGridDAO.emails.size()
    }
  }

  it should "not send notifications when they are off" in {
    val pubsubDao = new MockGooglePubSubDAO
    val topic = "topic"

    val workerCount = 1
    val sendGridDAO = new MockSendGridDAO
    system.actorOf(NotificationMonitorSupervisor.props(10 milliseconds, 0 milliseconds, pubsubDao, topic, "subscription", workerCount, sendGridDAO, Map("WorkspaceRemovedNotification" -> "valid_notification_id1", "WorkspaceAddedNotification" -> "valid_notification_id1"), "foo", ThurloeDatabaseConnector))

    // NotificationMonitorSupervisor creates the topic, need to wait for it to exist before publishing messages
    awaitCond(pubsubDao.topics.contains(topic), 10 seconds)

    val userId = sendGridDAO.testUserId1
    val workspaceName = WorkspaceName("ws_ns", "ws_n")
    val removedNotification = WorkspaceRemovedNotification(userId, "foo", workspaceName, "a_user_id2")
    val addedNotification = WorkspaceAddedNotification(userId, "foo", workspaceName, "a_user_id2")

    Await.result(ThurloeDatabaseConnector.set(UserKeyValuePairs(userId, Seq(KeyValuePair(NotificationMonitor.notificationsOffKey, "true")))), Duration.Inf)

    // wait for all the messages to be published and throw an error if one happens (i.e. use Await.result not Await.ready)
    val testNotifications = Seq(removedNotification, addedNotification)
    Await.result(pubsubDao.publishMessages(topic, testNotifications.map(NotificationFormat.write(_).compactPrint)), Duration.Inf)
    awaitAssert(assertResult(testNotifications.size) { pubsubDao.acks.size() }, 10 seconds)
    assertResult(0) {
      sendGridDAO.emails.size()
    }
  }
}
