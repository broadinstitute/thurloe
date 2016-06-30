package thurloe

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import lenthall.spray.SprayCanHttpService._
import thurloe.service.{SwaggerService, ThurloeServiceActor}

import scala.concurrent.duration._
object Main extends App {
  // We need an ActorSystem to host our application in
  implicit val system = ActorSystem("thurloe")

  // create and start our service actor
  val service = system.actorOf(ThurloeServiceActor.props(ConfigFactory.load()))

  implicit val timeout = Timeout(5.seconds)
  import scala.concurrent.ExecutionContext.Implicits.global

  // Start a new HTTP server on port 8000 with our service actor as the handler.
  service.bindOrShutdown(interface = "0.0.0.0", port = 8000) onSuccess {
    case _ => system.log.info("Thurloe now available for all your key/value pair and notification needs.")
  }}
