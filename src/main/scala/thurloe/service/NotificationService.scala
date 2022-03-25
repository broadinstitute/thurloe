package thurloe.service

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import thurloe.dataaccess.{NotificationException, SendGridDAO}
import thurloe.service.ApiDataModelsJsonProtocol._

import scala.util.{Failure, Success}

trait NotificationService {
  val sendGridDAO: SendGridDAO

  val postRoute: Route =
    path("notification") {
      post {
        entity(as[List[Notification]]) { notifications =>
          onComplete(sendGridDAO.sendNotifications(notifications)) {
            case Success(_) =>
              complete(StatusCodes.OK)
            case Failure(err: NotificationException) =>
              complete(err.statusCode, s"Unable to send notifications: ${err.getMessage}")
            case Failure(err) =>
              complete(StatusCodes.InternalServerError, s"Unable to send notifications: ${err.getMessage}")
          }
        }
      }
    }

  val notificationRoutes = postRoute
}
