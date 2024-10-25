package thurloe.notification

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.broadinstitute.dsde.workbench.google.GooglePubSubDAO.MessageRequest
import org.broadinstitute.dsde.workbench.model.Notifications._
import org.broadinstitute.dsde.workbench.model.{WorkbenchEmail, WorkbenchUserId}
import org.broadinstitute.dsde.workbench.google.mock.MockGooglePubSubDAO
import org.mockito.MockitoSugar.mock
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import thurloe.dataaccess.{HttpSamDAO, MockSendGridDAO, MockSendGridDAOWithException}
import thurloe.database.ThurloeDatabaseConnector
import thurloe.service.{KeyValuePair, UserKeyValuePairs}
import org.broadinstitute.dsde.workbench.client.sam
import org.mockito.Mockito.when

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

  val samDao = mock[HttpSamDAO]

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
        "foo",
        samDao
      )
    )

    // NotificationMonitorSupervisor creates the topic, need to wait for it to exist before publishing messages
    awaitCond(pubsubDao.topics.contains(topic), 10 seconds)
    val testNotifications =
      for (i <- 0 until 4) yield {
        val id = WorkbenchUserId(s"bar$i")
        val samUser = new sam.model.User()
        samUser.setId(id.value)
        samUser.setAzureB2CId(id.value)
        samUser.setGoogleSubjectId(id.value)

        when(samDao.getUserById(id.value)).thenReturn(List(samUser))
        WorkspaceInvitedNotification(WorkbenchEmail(s"foo$i"),
                                     id,
                                     WorkspaceName("namespace", "name"),
                                     "some-bucket-name"
        )
      }

    // wait for all the messages to be published and throw an error if one happens (i.e. use Await.result not Await.ready)
    Await.result(
      pubsubDao.publishMessages(
        topic,
        testNotifications.map(notification => MessageRequest(NotificationFormat.write(notification).compactPrint))
      ),
      Duration.Inf
    )
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
            "WorkspaceAddedNotification" -> "valid_notification_id1"
        ),
        "foo",
        samDao
      )
    )

    // NotificationMonitorSupervisor creates the topic, need to wait for it to exist before publishing messages
    awaitCond(pubsubDao.topics.contains(topic), 10 seconds)

    val userId = sendGridDAO.testUserId1
    val samUser1 = new sam.model.User()
    samUser1.setId(userId)
    samUser1.setAzureB2CId(userId)
    samUser1.setGoogleSubjectId(userId)

    val userId2 = "a_user_id2"
    val samUser2 = new sam.model.User()
    samUser2.setId(userId2)
    samUser2.setAzureB2CId(userId2)
    samUser2.setGoogleSubjectId(userId2)

    when(samDao.getUserById(userId)).thenReturn(List(samUser1))
    when(samDao.getUserById(userId2)).thenReturn(List(samUser2))

    val workspaceName = WorkspaceName("ws_ns", "ws_n")
    val removedNotification =
      WorkspaceRemovedNotification(WorkbenchUserId(userId), "foo", workspaceName, WorkbenchUserId("a_user_id2"))
    val addedNotification =
      WorkspaceAddedNotification(WorkbenchUserId(userId), "foo", workspaceName, WorkbenchUserId("a_user_id2"))

    Await.result(
      ThurloeDatabaseConnector.set(UserKeyValuePairs(userId, Seq(KeyValuePair(addedNotification.key, "false")))),
      Duration.Inf
    )

    // wait for all the messages to be published and throw an error if one happens (i.e. use Await.result not Await.ready)
    val testNotifications = Seq(removedNotification, addedNotification)
    Await.result(
      pubsubDao.publishMessages(
        topic,
        testNotifications.map(notification => MessageRequest(NotificationFormat.write(notification).compactPrint))
      ),
      Duration.Inf
    )
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
            "WorkspaceAddedNotification" -> "valid_notification_id1"
        ),
        "foo",
        samDao
      )
    )

    // NotificationMonitorSupervisor creates the topic, need to wait for it to exist before publishing messages
    awaitCond(pubsubDao.topics.contains(topic), 10 seconds)

    val userId = sendGridDAO.testUserId1
    val samUser = new sam.model.User()
    samUser.setId(userId)
    samUser.setAzureB2CId(userId)
    samUser.setGoogleSubjectId(userId)

    when(samDao.getUserById(userId)).thenReturn(List(samUser))

    val workspaceName = WorkspaceName("ws_ns", "ws_n")
    val removedNotification =
      WorkspaceRemovedNotification(WorkbenchUserId(userId), "foo", workspaceName, WorkbenchUserId("a_user_id2"))
    val addedNotification =
      WorkspaceAddedNotification(WorkbenchUserId(userId), "foo", workspaceName, WorkbenchUserId("a_user_id2"))

    Await.result(ThurloeDatabaseConnector.set(
                   UserKeyValuePairs(userId, Seq(KeyValuePair(NotificationMonitor.notificationsOffKey, "true")))
                 ),
                 Duration.Inf
    )

    // wait for all the messages to be published and throw an error if one happens (i.e. use Await.result not Await.ready)
    val testNotifications = Seq(removedNotification, addedNotification)
    Await.result(
      pubsubDao.publishMessages(
        topic,
        testNotifications.map(notification => MessageRequest(NotificationFormat.write(notification).compactPrint))
      ),
      Duration.Inf
    )
    awaitAssert(assertResult(testNotifications.size)(pubsubDao.acks.size()), 10 seconds)
    assertResult(0) {
      sendGridDAO.emails.size()
    }
  }

  it should "not send workspace notifications when they are turned off for a specific workspace" in {
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
        Map("WorkspaceRemovedNotification" -> "valid_notification_id1"),
        "foo",
        samDao
      )
    )

    // NotificationMonitorSupervisor creates the topic, need to wait for it to exist before publishing messages
    awaitCond(pubsubDao.topics.contains(topic), 10 seconds)

    val userId = sendGridDAO.testUserId1
    val workspaceName = WorkspaceName("ws_ns", "ws_n")
    val submissionNotification =
      SuccessfulSubmissionNotification(WorkbenchUserId(userId),
                                       workspaceName,
                                       "some-submission-id",
                                       "some-date",
                                       "some-config",
                                       "some-entity",
                                       15,
                                       "no comment"
      )

    Await.result(
      ThurloeDatabaseConnector.set(
        UserKeyValuePairs(
          userId,
          Seq(
            KeyValuePair(
              s"notifications/SuccessfulSubmissionNotification/${workspaceName.namespace}/${workspaceName.name}",
              "false"
            )
          )
        )
      ),
      Duration.Inf
    )

    // wait for all the messages to be published and throw an error if one happens (i.e. use Await.result not Await.ready)
    val testNotifications = Seq(submissionNotification)
    Await.result(
      pubsubDao.publishMessages(
        topic,
        testNotifications.map(notification => MessageRequest(NotificationFormat.write(notification).compactPrint))
      ),
      Duration.Inf
    )
    awaitAssert(assertResult(testNotifications.size)(pubsubDao.acks.size()), 10 seconds)
    assertResult(0) {
      sendGridDAO.emails.size()
    }
  }

  it should "not send workspace notifications when they are turned off at the notification type-level" in {
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
        Map("WorkspaceRemovedNotification" -> "valid_notification_id1"),
        "foo",
        samDao
      )
    )

    // NotificationMonitorSupervisor creates the topic, need to wait for it to exist before publishing messages
    awaitCond(pubsubDao.topics.contains(topic), 10 seconds)

    val userId = sendGridDAO.testUserId1
    val samUser = new sam.model.User()
    samUser.setId(userId)
    samUser.setAzureB2CId(userId)
    samUser.setGoogleSubjectId(userId)

    when(samDao.getUserById(userId)).thenReturn(List(samUser))

    val workspaceName = WorkspaceName("ws_ns", "ws_n")
    val submissionNotification =
      SuccessfulSubmissionNotification(WorkbenchUserId(userId),
                                       workspaceName,
                                       "some-submission-id",
                                       "some-date",
                                       "some-config",
                                       "some-entity",
                                       15,
                                       "no comment"
      )

    Await.result(
      ThurloeDatabaseConnector.set(
        UserKeyValuePairs(userId, Seq(KeyValuePair(s"notifications/SuccessfulSubmissionNotification", "false")))
      ),
      Duration.Inf
    )

    // wait for all the messages to be published and throw an error if one happens (i.e. use Await.result not Await.ready)
    val testNotifications = Seq(submissionNotification)
    Await.result(
      pubsubDao.publishMessages(
        topic,
        testNotifications.map(notification => MessageRequest(NotificationFormat.write(notification).compactPrint))
      ),
      Duration.Inf
    )
    awaitAssert(assertResult(testNotifications.size)(pubsubDao.acks.size()), 10 seconds)
    assertResult(0) {
      sendGridDAO.emails.size()
    }
  }

  it should "ignore SendGrid notifications when the monitor is unable to send them due to missing key/value pairs" in {
    val pubsubDao = new MockGooglePubSubDAO
    val topic = "topic"

    val workerCount = 1
    val sendGridDAO =
      new MockSendGridDAOWithException // throws an KeyNotFoundException when calling `sendNotifications`
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
            "WorkspaceAddedNotification" -> "valid_notification_id1"
        ),
        "foo",
        samDao
      )
    )

    // NotificationMonitorSupervisor creates the topic, need to wait for it to exist before publishing messages
    awaitCond(pubsubDao.topics.contains(topic), 10 seconds)

    val userId = sendGridDAO.testUserId1
    val workspaceName = WorkspaceName("ws_ns", "ws_n")
    val removedNotification =
      WorkspaceRemovedNotification(WorkbenchUserId(userId), "foo", workspaceName, WorkbenchUserId("a_user_id2"))
    val addedNotification =
      WorkspaceAddedNotification(WorkbenchUserId(userId), "foo", workspaceName, WorkbenchUserId("a_user_id2"))

    // Make sure notifications are turned on for the test user (notificationsOffKey -> false)
    Await.result(ThurloeDatabaseConnector.set(
                   UserKeyValuePairs(userId, Seq(KeyValuePair(NotificationMonitor.notificationsOffKey, "false")))
                 ),
                 Duration.Inf
    )

    val testNotifications = Seq(removedNotification, addedNotification)
    Await.result(
      pubsubDao.publishMessages(
        topic,
        testNotifications.map(notification => MessageRequest(NotificationFormat.write(notification).compactPrint))
      ),
      Duration.Inf
    )
    awaitAssert(assertResult(testNotifications.size)(pubsubDao.acks.size()), 10 seconds)
    assertResult(0) {
      sendGridDAO.emails.size()
    }
  }

}
