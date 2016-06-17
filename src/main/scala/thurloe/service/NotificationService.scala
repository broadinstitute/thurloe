package thurloe.service

import java.net.URLEncoder

import spray.http.HttpHeaders.RawHeader
import spray.http.MediaTypes._
import spray.http.StatusCodes
import spray.json._
import spray.routing.HttpService
import thurloe.database.DatabaseOperation.DatabaseOperation
import thurloe.database.{DataAccess, DatabaseOperation}
import thurloe.service.ApiDataModelsJsonProtocol._
import thurloe.dataaccess.HttpSendGridDAO

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

trait NotificationService extends HttpService {

  import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller

  val sendGridDAO = new HttpSendGridDAO

  val postRoute = path("notification") {
    post {
      entity(as[Notification]) { notification =>
        onComplete(sendGridDAO.sendEmail(sendGridDAO.createEmail(notification.contactEmail, notification.notificationId, notification.substitutions))) {
          case Success(response) =>
            respondWithMediaType(`application/json`) {
              respondWithStatus(StatusCodes.OK) {
                complete {
                  ""
                }
              }
            }
          case Failure(e) =>
            respondWithStatus(StatusCodes.InternalServerError) {
              complete {
                s"Unable to send notification! $e"
              }
            }
        }
      }
    }
  }

  val notificationRoutes = postRoute
}