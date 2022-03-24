package thurloe.service

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import thurloe.database.DataAccess

import scala.util.{Failure, Success}

trait StatusService {
  val dataAccess: DataAccess

  val statusRoute: Route =
    path("status") {
      pathEndOrSingleSlash {
        get {
          onComplete(dataAccess.status()) {
            case Success(_) =>
              complete(HttpEntity(ContentTypes.`application/json`, """{"status": "up"}"""))
            case Failure(e) =>
              complete(StatusCodes.InternalServerError, HttpEntity(ContentTypes.`application/json`, s"""{"status": "down", "error": "${e.getMessage()}"}"""))
          }
        }
      }
    }
}

