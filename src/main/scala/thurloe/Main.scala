package thurloe

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import com.typesafe.config.ConfigFactory
import io.sentry.{Sentry, SentryOptions}
import org.broadinstitute.dsde.workbench.google.{GoogleCredentialModes, HttpGooglePubSubDAO}
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import org.broadinstitute.dsde.workbench.util.toScalaDuration
import thurloe.dataaccess.auth.CloudServiceAuthTokenProvider
import thurloe.dataaccess.{HttpSamDAO, HttpSendGridDAO}
import thurloe.notification.NotificationMonitorSupervisor
import thurloe.service.ThurloeServiceActor

import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._

object Main extends App {
  val sentryDsn = sys.env.get("SENTRY_DSN")
  sentryDsn.foreach { dsn =>
    val options = new SentryOptions()
    options.setDsn(dsn)
    options.setEnvironment(sys.env.getOrElse("SENTRY_ENVIRONMENT", "unknown"))
    Sentry.init(options)
  }

  // We need an ActorSystem to host our application in
  implicit val system = ActorSystem("thurloe")

  val config = ConfigFactory.load()
  val gcsConfig = config.getConfig("gcs")

  private val cloudAuthProvider = CloudServiceAuthTokenProvider.createProvider(config)

  val samDao = new HttpSamDAO(config, cloudAuthProvider)

  val pem =
    GoogleCredentialModes.Pem(WorkbenchEmail(gcsConfig.getString("clientEmail")),
                              new File(gcsConfig.getString("pathToPem")))
  val pubSubDAO = new HttpGooglePubSubDAO(
    gcsConfig.getString("appName"),
    pem,
    "thurloe",
    gcsConfig.getString("serviceProject")
  )

  private val httpSendGridDAO = new HttpSendGridDAO(samDao)
  system.actorOf(
    NotificationMonitorSupervisor.props(
      toScalaDuration(gcsConfig.getDuration("notificationMonitor.pollInterval")),
      toScalaDuration(gcsConfig.getDuration("notificationMonitor.pollIntervalJitter")),
      pubSubDAO,
      gcsConfig.getString("notificationMonitor.topicName"),
      gcsConfig.getString("notificationMonitor.subscriptionName"),
      gcsConfig.getInt("notificationMonitor.workerCount"),
      httpSendGridDAO,
      config
        .getConfig("notification.templateIds")
        .entrySet()
        .asScala
        .map(entry => entry.getKey -> entry.getValue.unwrapped().toString)
        .toMap,
      config.getString("notification.fireCloudPortalUrl"),
      samDao
    )
  )

  val routes = new ThurloeServiceActor(samDao)

  for {
    binding <- Http().newServerAt("0.0.0.0", 8000).bind(routes.route).recover {
      case t: Throwable =>
        system.log.error("FATAL - failure starting http server", t)
        throw t
    }
    _ = system.log.info("Thurloe now available for all your key/value pair and notification needs.")
    _ <- binding.whenTerminated
    _ <- system.terminate()
  } yield ()
}
