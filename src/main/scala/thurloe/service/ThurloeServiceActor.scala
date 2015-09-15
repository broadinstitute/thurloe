package thurloe.service

import akka.actor.{ActorRefFactory, ActorLogging}

import spray.routing._
import thurloe.database.{ThurloeDatabaseConnector, DataAccess}
import scala.util.{Try, Success}

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class ThurloeServiceActor extends HttpServiceActor with ActorLogging {

  val thurloeService = new ThurloeService {
    val dataAccess = ThurloeDatabaseConnector
    def actorRefFactory = context
  }

  // TODO: Relies on swagger existing in resources/swagger/
  val swaggerSite = HostedResource("swagger/index.html", Option("swagger"), "swagger", context)

  val apiSpecYaml = new HostedResource("yaml/thurloe-api.yaml", None, "thurloe-api", context)

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(thurloeService.routes ~ swaggerSite.routes ~ apiSpecYaml.routes)
}

case class HostedResource(
      resource: String,
      resourceDirectory: Option[String],
      path: String,
      actorRefFactoryImp: ActorRefFactory) extends HttpService {
  implicit val actorRefFactory = actorRefFactoryImp
  val routes = resourceDirectory match {
    case Some(rd) => path(path) { getFromResource(resource) } ~ getFromResourceDirectory(rd)
    case None => path(path) { getFromResource(resource) }
  }
}

