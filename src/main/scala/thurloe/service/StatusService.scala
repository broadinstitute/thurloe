package thurloe.service

import spray.http.MediaTypes._
import spray.http.StatusCodes
import spray.routing.HttpService
import spray.json.{JsObject, JsString}
import thurloe.database.DataAccess

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object BuildTimeVersion {
  val version = Option(getClass.getPackage.getImplementationVersion)
}

trait StatusService extends HttpService {

  val dataAccess: DataAccess
  val getVersion = path("version") {
    pathEndOrSingleSlash {
      get {
        respondWithMediaType(`application/json`) {
          respondWithStatus(StatusCodes.OK) {
            complete {
              """{"version": """ + BuildTimeVersion.version + """}"""
            }
          }
        }
      }
    }
  }
  val getStatus = path("status") {
    get {
      onComplete(dataAccess.status()) {
        case Success(_) =>
          respondWithMediaType(`application/json`) {
            respondWithStatus(StatusCodes.OK) {
              complete {
                 """{"status": "up"}"""
              }
            }
          }
        case Failure(e) =>
          respondWithMediaType(`application/json`) {
            respondWithStatus(StatusCodes.InternalServerError) {
              complete {
                s"""{"status": "down", "error": "${e.getMessage()}"}"""
              }
            }
          }
      }
    }
  }
  val statusRoute = getStatus ~ getVersion
}

