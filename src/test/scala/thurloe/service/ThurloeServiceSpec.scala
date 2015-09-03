package thurloe.service

import org.scalatest.{FunSpec}
// import spray.http._

class ThurloeServiceSpec extends FunSpec with MyService {

  // TODO: Test the real API not the one I copied the tests from:

  //def actorRefFactory = system
  def actorRefFactory = ???

  describe("MyService") {
    it("should return a greeting for GET requests to the root path") {
//      Get() ~> myRoute ~> check {
//        responseAs[String] must contain("Say hello")
//      }
    }

    it("should leave GET requests to other paths unhandled") {
//      Get("/kermit") ~> myRoute ~> check {
//        handled must beFalse
//      }
    }

    it("should return a MethodNotAllowed error for PUT requests to the root path") {
//      Put() ~> sealRoute(myRoute) ~> check {
//        status === MethodNotAllowed
//        responseAs[String] === "HTTP method not allowed, supported methods: GET"
//      }
    }
  }
}
