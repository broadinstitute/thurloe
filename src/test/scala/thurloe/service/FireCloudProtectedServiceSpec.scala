package thurloe.service

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.mockito.Mockito.when
import org.mockito.MockitoSugar.mock
import org.scalatest.funspec.AnyFunSpec
import thurloe.dataaccess.{MockSendGridDAO, SamDAO}
import thurloe.database.ThurloeDatabaseConnector
import org.broadinstitute.dsde.workbench.client.sam

class FireCloudProtectedServiceSpec extends AnyFunSpec with ScalatestRouteTest {
  val userId = "fake"

  def protectedServices = new FireCloudProtectedServices {
    val samDao: SamDAO = mock[SamDAO]
    val samUser1 = new sam.model.User()
    samUser1.setId(userId)
    samUser1.setAzureB2CId(userId)
    samUser1.setGoogleSubjectId(userId)

    when(samDao.getUserById(userId)).thenReturn(List(samUser1))
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
      Get(uriPrefix + s"?userId=$userId") ~> addHeader(RawHeader(fcHeader, fcId)) ~> routes ~> check {
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should return a BadRequest response that indicates an incorrect header value") {
      Get(uriPrefix + s"?userId=$userId") ~> addHeader(RawHeader(fcHeader, "invalid")) ~> routes ~> check {
        assert(responseAs[String].contains("Invalid 'X-FireCloud-Id' Header Provided"))
        assertResult(StatusCodes.BadRequest) {
          status
        }
      }
    }

    it("should return a BadRequest response that indicates a missing header") {
      Get(uriPrefix + s"?userId=$userId") ~> routes ~> check {
        assert(responseAs[String].contains("Request is missing required HTTP header 'X-FireCloud-Id'"))
        assertResult(StatusCodes.BadRequest) {
          status
        }
      }
    }
  }

}
