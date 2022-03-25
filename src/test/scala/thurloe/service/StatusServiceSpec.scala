package thurloe.service

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AnyFunSpec
import thurloe.database.{MockThurloeDatabaseConnector, MockUnhealthyThurloeDatabaseConnector}

class StatusServicePositiveSpec extends AnyFunSpec with ScalatestRouteTest {

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

class StatusServiceNegativeSpec extends AnyFunSpec with ScalatestRouteTest {

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
