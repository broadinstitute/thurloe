package thurloe.service

import org.scalatest.FunSpec
import spray.http.StatusCodes
import spray.testkit.ScalatestRouteTest
import thurloe.database.{MockThurloeDatabaseConnector, MockUnhealthyThurloeDatabaseConnector}

class StatusServicePositiveSpec extends FunSpec with ScalatestRouteTest {

  def statusService = new StatusService {
    val dataAccess = MockThurloeDatabaseConnector
    def actorRefFactory = system
  }

  describe("A healthy Thurloe's status service") {
    it ("should return successful status code, without requiring authentication") {
      Get(s"/status") ~> statusService.statusRoute~> check {
        assertResult(StatusCodes.OK) {
          status
        }
        assertResult(s"""{"status": "up"}""") {
          responseAs[String]
        }

      }
    }
  }
}

class StatusServiceNegativeSpec extends FunSpec with ScalatestRouteTest {

  def statusService = new StatusService {
    val dataAccess = MockUnhealthyThurloeDatabaseConnector
    def actorRefFactory = system
  }

  describe("An unhealthy Thurloe's status service") {
    it("should return an error from the DAO, without requiring authentication") {
      Get(s"/status") ~> statusService.statusRoute ~> check {
        assertResult(s"""{"status": "down", "error": "Failure from \"unhealthy\" mock DAO"}""") {
          responseAs[String]
        }
        assertResult(StatusCodes.InternalServerError) {
          status
        }
      }
    }
  }
}
