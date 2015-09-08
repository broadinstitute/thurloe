package thurloe.service

import akka.actor.{ActorRefFactory, ActorLogging}

import spray.routing._
import scala.util.{Try, Success}

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class ThurloeServiceActor extends HttpServiceActor with ActorLogging {

  val thurloeService = new ThurloeService {
    val dataAccess = ThurloeDatabaseConnector
    def actorRefFactory = context
  }

  // TODO: Relies on swagger existing in resources/swagger/
  val swaggerSite = HostedResource("swagger/index.html", "swagger", "swagger", context)

  val apiSpecYaml = new HostedResource("thurloe-api.yaml", "", "thurloe-api", context)

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(thurloeService.routes ~ swaggerSite.routes ~ apiSpecYaml.routes)
}

case class HostedResource(resource: String, resourceDirectory: String, path: String, actorRefFactorye: ActorRefFactory) extends HttpService {

  implicit val actorRefFactory = actorRefFactorye
  val routes =
    path(path) { getFromResource(resource) } ~
      getFromResourceDirectory(resourceDirectory)
}

case object ThurloeDatabaseConnector extends DataAccess {
  
  // TODO: Fill THESE in with database access implementation.
  def keyLookup(key: String) = Success(KeyValuePair("yek", "eulav"))
  def collectAll() = Success(Seq(
    KeyValuePair("key1", "Bob Loblaw's Law Blog"),
    KeyValuePair("key2", "Blah blah blah blah blah")))
  def setKeyValuePair(keyValuePair: KeyValuePair): Try[Unit] = Success(())
  def deleteKeyValuePair(key: String): Try[Unit] = Success()
}