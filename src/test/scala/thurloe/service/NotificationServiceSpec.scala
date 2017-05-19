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

  val validNotification = Notification(Some("a_user_id"), None, None, "valid_notification_id1", Map.empty)
  val validNotification2 = Notification(Some("a_user_id"), None, Option(Set("a_user_id")), "valid_notification_id1", Map.empty)
  val invalidNotification = Notification(Some("a_user_id"), None, None, "invalid_notification_id1", Map.empty)

  def notificationService = new NotificationService {
    val sendGridDAO = new MockSendGridDAO
    def actorRefFactory = system
  }

  describe("The Notification Service") {
    it("should send a valid notification to a user") {
      Post("/notification", List(validNotification)) ~> notificationService.notificationRoutes ~> check {
        assertResult("OK") {
          responseAs[String]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should send a list of valid notifications to a user") {
      Post("/notification", List(validNotification, validNotification2)) ~> notificationService.notificationRoutes ~> check {
        assertResult("OK") {
          responseAs[String]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should not send an invalid notification to a user") {
      Post("/notification", List(invalidNotification)) ~> notificationService.notificationRoutes ~> check {
        assertResult(StatusCodes.BadRequest) {
          status
        }
      }
    }

    it("should not send an invalid notification to a user in a list with a valid notification") {
      Post("/notification", List(invalidNotification, validNotification)) ~> notificationService.notificationRoutes ~> check {
        assertResult(StatusCodes.BadRequest) {
          status
        }
      }
    }

    it("should send a valid notification to a user with no contactEmail set") {
      Post("/notification", List(validNotification.copy(userId = Some("a_user_id2")))) ~> notificationService.notificationRoutes ~> check {
        assertResult("OK") {
          responseAs[String]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("throw an exception when sending a valid notification to a user with no contact settings") {
      Post("/notification", List(validNotification.copy(userId = Some("a_user_id3")))) ~> notificationService.notificationRoutes ~> check {
        assertResult(StatusCodes.InternalServerError) {
          status
        }
      }
    }

    it("send a valid notification to an external user with no contact settings") {
      Post("/notification", List(validNotification.copy(userId = None, userEmail = Some("foo@example.com")))) ~> notificationService.notificationRoutes ~> check {
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("throw an exception when sending a notification with no userId or userEmail set") {
      Post("/notification", List(validNotification.copy(userId = None))) ~> notificationService.notificationRoutes ~> check {
        assertResult(StatusCodes.BadRequest) {
          status
        }
      }
    }

  }
}
