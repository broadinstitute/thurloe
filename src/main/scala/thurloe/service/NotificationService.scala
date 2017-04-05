package thurloe.service

import spray.http.MediaTypes._
import spray.http.StatusCodes
import spray.routing.HttpService
import thurloe.service.ApiDataModelsJsonProtocol._
import thurloe.dataaccess.{NotificationException, SendGridDAO, HttpSendGridDAO}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


trait NotificationService extends HttpService {
  val sendGridDAO: SendGridDAO

  import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller

  val postRoute = path("notification") {
    post {
      entity(as[List[Notification]]) { notifications =>
        onComplete(sendGridDAO.sendNotifications(notifications)) {
          case Success(_) =>
            complete(StatusCodes.OK)
          case Failure(err: NotificationException) =>
            respondWithStatus(err.statusCode) {
              complete {
                s"Unable to send notifications: ${err.getMessage}"
              }
            }

          case Failure(err) =>
            respondWithStatus(StatusCodes.InternalServerError) {
              complete {
                s"Unable to send notifications: ${err.getMessage}"
              }
            }
        }
      }
    }
  }

  val notificationRoutes = postRoute
}
