package thurloe

import java.io.StringReader

import akka.actor.ActorSystem
import akka.util.Timeout
import com.google.api.client.json.jackson2.JacksonFactory
import com.typesafe.config.ConfigFactory
import lenthall.spray.SprayCanHttpService._
import org.broadinstitute.dsde.rawls.google.HttpGooglePubSubDAO
import thurloe.dataaccess.HttpSendGridDAO
import thurloe.notification.NotificationMonitorSupervisor
import thurloe.service.{SwaggerService, ThurloeServiceActor}
import org.broadinstitute.dsde.rawls.util

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object Main extends App {
  // We need an ActorSystem to host our application in
  implicit val system = ActorSystem("thurloe")

  val config = ConfigFactory.load()
  val gcsConfig = config.getConfig("gcs")

  val jsonFactory = JacksonFactory.getDefaultInstance

  val pubSubDAO = new HttpGooglePubSubDAO(
    gcsConfig.getString("clientEmail"),
    gcsConfig.getString("pathToPem"),
    gcsConfig.getString("appName"),
    gcsConfig.getString("serviceProject")
  )

  system.actorOf(NotificationMonitorSupervisor.props(
    util.toScalaDuration(gcsConfig.getDuration("notificationMonitor.pollInterval")),
    util.toScalaDuration(gcsConfig.getDuration("notificationMonitor.pollIntervalJitter")),
    pubSubDAO,
    gcsConfig.getString("notificationMonitor.topicName"),
    gcsConfig.getString("notificationMonitor.subscriptionName"),
    gcsConfig.getInt("notificationMonitor.workerCount"),
    new HttpSendGridDAO))

  // create and start our service actor
  val service = system.actorOf(ThurloeServiceActor.props(ConfigFactory.load()))

  implicit val timeout = Timeout(5.seconds)
  import scala.concurrent.ExecutionContext.Implicits.global

  // Start a new HTTP server on port 8000 with our service actor as the handler.
  service.bindOrShutdown(interface = "0.0.0.0", port = 8000) onSuccess {
    case _ => system.log.info("Thurloe now available for all your key/value pair and notification needs.")
  }
}
