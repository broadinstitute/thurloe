package thurloe.service

import spray.http.StatusCodes
import spray.routing.HttpService
import thurloe.database.DataAccess

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

trait StatusService extends HttpService {

  val dataAccess: DataAccess
  val getStatus = path("status") {
    get {
      onComplete(dataAccess.status()) {
        case Success(_) =>
          respondWithStatus(StatusCodes.OK) {
            complete {
              ""
            }
          }
        case Failure(e) =>
          respondWithStatus(StatusCodes.InternalServerError) {
            complete {
              s"${e.getMessage()}"
            }
          }
      }
    }
  }
  val statusRoutes = getStatus
}