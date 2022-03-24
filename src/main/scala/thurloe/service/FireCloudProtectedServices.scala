package thurloe.service

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.headerValueByName
import akka.http.scaladsl.server.Route
import com.typesafe.config.ConfigFactory
import akka.http.scaladsl.server.Directives._

// These routes are restricted to access from valid FireCloud clients.
// Ensure that all services under this one provide the required Header.
trait FireCloudProtectedServices extends ThurloeService with NotificationService {

  val fcHeader = "X-FireCloud-Id"
  val config = ConfigFactory.load()
  val fcId = config.getConfig("fireCloud").getString("id")

  val fireCloudProtectedRoutes: Route = headerValueByName(fcHeader) {
    case x if x.equals(fcId) => pathPrefix("api") { keyValuePairRoutes ~ notificationRoutes}
    case _ => complete(StatusCodes.BadRequest, s"Invalid '$fcHeader' Header Provided")
  }

}
