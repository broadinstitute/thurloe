package thurloe.service

import akka.actor.{ActorRefFactory, ActorLogging}
import slick.collection.heterogeneous.HList
import spray.routing

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

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(thurloeService.routes)
}

