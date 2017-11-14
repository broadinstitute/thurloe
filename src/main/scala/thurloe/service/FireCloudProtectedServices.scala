package thurloe.service

import com.typesafe.config.ConfigFactory
import spray.http.StatusCodes
import spray.routing.{HttpService, _}

// These routes are restricted to access from valid FireCloud clients.
// Ensure that all services under this one provide the required Header.
trait FireCloudProtectedServices extends HttpService with ThurloeService with NotificationService {

  val fcHeader = "X-FireCloud-Id"
  val config = ConfigFactory.load()
  val fcId = config.getConfig("fireCloud").getString("id")

  val fireCloudProtectedRoutes: Route = headerValueByName(fcHeader) {
    case x if x.equals(fcId) => pathPrefix("api") { keyValuePairRoutes ~ notificationRoutes}
    case _ => respondWithStatus(StatusCodes.BadRequest) { complete(s"Invalid '$fcHeader' Header Provided") }
  }

}
