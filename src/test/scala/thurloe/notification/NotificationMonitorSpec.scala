package thurloe.notification

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.broadinstitute.dsde.rawls.google.MockGooglePubSubDAO
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import spray.json._
import thurloe.dataaccess.MockSendGridDAO
import thurloe.service.Notification
import thurloe.service.ApiDataModelsJsonProtocol.notificationFormat

import scala.collection.convert.decorateAsScala._
import scala.concurrent.Await
import scala.concurrent.duration._

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
    import scala.concurrent.ExecutionContext.Implicits.global
    system.actorOf(NotificationMonitorSupervisor.props(10 milliseconds, 0 milliseconds, pubsubDao, topic, "subscription", workerCount, sendGridDAO))

    // NotificationMonitorSupervisor creates the topic, need to wait for it to exist before publishing messages
    awaitCond(pubsubDao.topics.contains(topic), 10 seconds)
    val testNotifications = (for (i <- 0 until workerCount * 4) yield Notification(None, Option(s"$i@foo.com"), None, "valid_notification_id1", Map("x" -> i.toString)))

    // wait for all the messages to be published and throw an error if one happens (i.e. use Await.result not Await.ready)
    Await.result(pubsubDao.publishMessages(topic, testNotifications.map(_.toJson.compactPrint)), Duration.Inf)
    awaitAssert(assertResult(testNotifications.map(n => n.userEmail.get).toSet) {
      sendGridDAO.emails.asScala.map(email => email.getTos.head).toSet
    }, 10 seconds)
    awaitAssert(assertResult(testNotifications.size) { pubsubDao.acks.size() }, 10 seconds)
  }
}
