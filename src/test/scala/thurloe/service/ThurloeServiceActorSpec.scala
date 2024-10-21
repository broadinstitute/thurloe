package thurloe.service

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.mockito.MockitoSugar.mock
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import thurloe.dataaccess.HttpSamDAO

class ThurloeServiceActorSpec extends AnyFunSpec with Matchers with ScalatestRouteTest {

  val service = new ThurloeServiceActor(mock[HttpSamDAO])

  describe("ThurloeServiceActor") {
    it("include Content-Security-Policy header in main route") {
      Get("/") ~> service.route ~> check {
        val cspHeader = header("Content-Security-Policy")
        cspHeader shouldBe defined
        cspHeader.get.value should include("default-src 'self'")
        cspHeader.get.value should include("script-src 'self' 'unsafe-inline'")

        assertResult(StatusCodes.OK) {
          status
        }
      }
    }
  }
}
