package thurloe.service

import thurloe.dataaccess.MockSendGridDAO
import org.scalatest.FunSpec
import spray.http.StatusCodes
import spray.testkit.ScalatestRouteTest
import spray.httpx.SprayJsonSupport._

/**
 * Created by mbemis on 6/23/16.
 */
class NotificationServiceSpec extends FunSpec with ScalatestRouteTest {

  import ApiDataModelsJsonProtocol._

  val validNotification = Notification("a_user_id", "valid_notification_id1", Map.empty)
  val invalidNotification = Notification("a_user_id", "invalid_notification_id1", Map.empty)

  def notificationService = new NotificationService {
    val sendGridDAO = new MockSendGridDAO
    def actorRefFactory = system
  }

  describe("The Notification Service") {
    it("should send a valid notification to a user") {
      Post("/notification", validNotification) ~> notificationService.notificationRoutes ~> check {
        assertResult("OK") {
          responseAs[String]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should not send an invalid notification to a user") {
      Post("/notification", invalidNotification) ~> notificationService.notificationRoutes ~> check {
        assertResult(StatusCodes.InternalServerError) {
          status
        }
      }
    }

    it("should send a valid notification to a user with no contactEmail set") {
      Post("/notification", validNotification.copy(userId = "a_user_id2")) ~> notificationService.notificationRoutes ~> check {
        assertResult("OK") {
          responseAs[String]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("throw an exception when sending a valid notification to a user with no contact settings") {
      Post("/notification", validNotification.copy(userId = "a_user_id3")) ~> notificationService.notificationRoutes ~> check {
        assertResult(StatusCodes.InternalServerError) {
          status
        }
      }
    }


  }
}
