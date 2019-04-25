package thurloe.service

import com.typesafe.config.ConfigFactory
import org.scalatest.FunSpec
import spray.http.{HttpHeaders, StatusCodes}
import spray.routing.HttpServiceBase
import spray.testkit.ScalatestRouteTest
import thurloe.dataaccess.MockSendGridDAO
import thurloe.database.ThurloeDatabaseConnector

class FireCloudProtectedServiceSpec extends FunSpec with ScalatestRouteTest with HttpServiceBase {

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
      Get(uriPrefix+"?userId=fake") ~> addHeader(HttpHeaders.RawHeader(fcHeader, fcId)) ~> routes ~> check {
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should return a BadRequest response that indicates an incorrect header value") {
      Get(uriPrefix+"?userId=fake") ~> addHeader(HttpHeaders.RawHeader(fcHeader, "invalid")) ~> sealRoute(routes) ~> check {
        assert(responseAs[String].contains("Invalid 'X-FireCloud-Id' Header Provided"))
        assertResult(StatusCodes.BadRequest) {
          status
        }
      }
    }

    it("should return a BadRequest response that indicates a missing header") {
      Get(uriPrefix+"?userId=fake") ~> sealRoute(routes) ~> check {
        assert(responseAs[String].contains("Request is missing required HTTP header 'X-FireCloud-Id'"))
        assertResult(StatusCodes.BadRequest) {
          status
        }
      }
    }
  }

}
