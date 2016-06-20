package thurloe.service

import spray.http.MediaTypes._
import spray.http.StatusCodes
import spray.routing.HttpService
import thurloe.service.ApiDataModelsJsonProtocol._
import thurloe.dataaccess.HttpSendGridDAO

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

trait NotificationService extends HttpService {

  import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller

  val sendGridDAO = new HttpSendGridDAO

  val postRoute = path("notification") {
    post {
      entity(as[Notification]) { notification =>
        onComplete(sendGridDAO.sendNotification(notification)) {
          case Success(_) =>
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
                s"Unable to send notification: ${e}"
              }
            }
        }
      }
    }
  }

  val notificationRoutes = postRoute
}
