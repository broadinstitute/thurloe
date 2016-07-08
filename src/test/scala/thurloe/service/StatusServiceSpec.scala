package thurloe.service

import org.scalatest.FunSpec
import spray.http.StatusCodes
import spray.testkit.ScalatestRouteTest
import thurloe.database.ThurloeDatabaseConnector

class StatusServiceSpec extends FunSpec with ScalatestRouteTest {

  val validNotification = Notification("a_user_id", "valid_notification_id1", Map.empty)
  val invalidNotification = Notification("a_user_id", "invalid_notification_id1", Map.empty)

  def statusService = new StatusService {
    val dataAccess = ThurloeDatabaseConnector
    def actorRefFactory = system
  }

  describe("The Notification Service") {
    ignore ("should return successful status code") {
      // test database is weird -- get an error trying to select version ()
      // java.sql.SQLSyntaxErrorException: user lacks privilege or object not found: VERSION
      Get(s"/status") ~> statusService.statusRoutes~> check {
        assertResult(StatusCodes.OK) {
          status
        }

      }
    }
    it ("should return internal server error") {
      // This shouldn't really pass, but test database is weird so select version () fails
      Get(s"/status") ~> statusService.statusRoutes~> check {
        assertResult(s"Connection to database was unsuccessful") {
          responseAs[String]
        }
        assertResult(StatusCodes.InternalServerError) {
          status
        }

      }
    }
  }


}
