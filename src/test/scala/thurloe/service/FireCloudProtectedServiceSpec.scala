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
      Get(uriPrefix) ~> addHeader(HttpHeaders.RawHeader(fcHeader, fcId)) ~> routes ~> check {
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should return a BadRequest response with an incorrect header value") {
      Get(uriPrefix) ~> addHeader(HttpHeaders.RawHeader(fcHeader, "invalid")) ~> sealRoute(routes) ~> check {
        assertResult(StatusCodes.BadRequest) {
          status
        }
      }
    }

    it("should return a BadRequest response with missing header") {
      Get(uriPrefix) ~> sealRoute(routes) ~> check {
        assertResult(StatusCodes.BadRequest) {
          status
        }
      }
    }
  }

}
