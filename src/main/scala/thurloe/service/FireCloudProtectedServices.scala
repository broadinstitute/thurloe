package thurloe.service

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.ConfigFactory

// These routes are restricted to access from valid FireCloud clients.
// Ensure that all services under this one provide the required Header.
trait FireCloudProtectedServices extends ThurloeService with NotificationService {

  val fcHeader = "X-FireCloud-Id"
  val config = ConfigFactory.load()
  val fcId = config.getConfig("fireCloud").getString("id")

  val fireCloudProtectedRoutes: Route = optionalHeaderValueByName(fcHeader) {
    case Some(x) if x.equals(fcId) => pathPrefix("api")(keyValuePairRoutes ~ notificationRoutes)
    case Some(_)                   => complete(StatusCodes.BadRequest, s"Invalid '$fcHeader' Header Provided")
    case None                      => complete(StatusCodes.BadRequest, s"Request is missing required HTTP header '$fcHeader'")
  }

}
