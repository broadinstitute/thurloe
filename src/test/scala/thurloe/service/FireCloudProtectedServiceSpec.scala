package thurloe.service

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AnyFunSpec
import thurloe.dataaccess.MockSendGridDAO
import thurloe.database.ThurloeDatabaseConnector

class FireCloudProtectedServiceSpec extends AnyFunSpec with ScalatestRouteTest {

  def protectedServices = new FireCloudProtectedServices {
    val dataAccess = ThurloeDatabaseConnector
    val sendGridDAO = new MockSendGridDAO
    def actorRefFactory = system
  }

  val uriPrefix = "/api/thurloe"
  val fcHeader = protectedServices.fcHeader
  val fcId = protectedServices.fcId
  val routes = protectedServices.fireCloudProtectedRoutes

  describe("The Thurloe Service") {

    it("should return a valid response with a correct header") {
      Get(uriPrefix + "?userId=fake") ~> addHeader(RawHeader(fcHeader, fcId)) ~> routes ~> check {
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should return a BadRequest response that indicates an incorrect header value") {
      Get(uriPrefix + "?userId=fake") ~> addHeader(RawHeader(fcHeader, "invalid")) ~> routes ~> check {
        assert(responseAs[String].contains("Invalid 'X-FireCloud-Id' Header Provided"))
        assertResult(StatusCodes.BadRequest) {
          status
        }
      }
    }

    it("should return a BadRequest response that indicates a missing header") {
      Get(uriPrefix + "?userId=fake") ~> routes ~> check {
        assert(responseAs[String].contains("Request is missing required HTTP header 'X-FireCloud-Id'"))
        assertResult(StatusCodes.BadRequest) {
          status
        }
      }
    }
  }

}
