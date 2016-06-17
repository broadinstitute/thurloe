package thurloe.service

import java.net.URLEncoder

import spray.http.HttpHeaders.RawHeader
import spray.http.MediaTypes._
import spray.http.StatusCodes
import spray.json._
import spray.routing.HttpService
import thurloe.database.DatabaseOperation.DatabaseOperation
import thurloe.database.{DataAccess, DatabaseOperation, KeyNotFoundException}
import thurloe.service.ApiDataModelsJsonProtocol._
import thurloe.dataaccess.HttpSendGridDAO

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

trait NotificationService extends HttpService {

  import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller

  val dataAccess: DataAccess
  val ThurloePrefix = "thurloe"
  val Interjection = "Harumph!"

  val sendGridDAO = new HttpSendGridDAO


  private def statusCode(setKeyResponse: DatabaseOperation) = {
    setKeyResponse match {
      case DatabaseOperation.Insert => StatusCodes.Created
      case _ => StatusCodes.OK
    }
  }

  val postRoute = path(ThurloePrefix) {
    post {
      entity(as[Notification]) { notification =>
        respondWithHeader(RawHeader("Location", s"/$ThurloePrefix/notification")) {
          onComplete(sendGridDAO.sendEmail(notification.contactEmail, notification.notificationId)) {
            case Success(setKeyResponse) =>
              respondWithMediaType(`application/json`) {
                respondWithStatus(statusCode(DatabaseOperation.Update)) {
                  complete {
                    ""
                  }
                }
              }
            case Failure(e) =>
              respondWithStatus(StatusCodes.InternalServerError) {
                complete {
                  s"$Interjection $e"
                }
              }
          }
        }
      }
    }
  }

  val notificationRoutes = postRoute
}