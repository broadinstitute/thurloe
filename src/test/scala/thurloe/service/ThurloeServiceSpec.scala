package thurloe.service

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Matchers, FlatSpec, FunSpec}
import org.yaml.snakeyaml.Yaml
import spray.http.StatusCodes
import spray.testkit.ScalatestRouteTest
import spray.httpx.SprayJsonSupport._
import thurloe.database.ThurloeDatabaseConnector

class ThurloeServiceSpec extends FunSpec with ScalatestRouteTest {

  import ApiDataModelsJsonProtocol._

  def thurloeService = new ThurloeService {
    val dataAccess = ThurloeDatabaseConnector
    def actorRefFactory = system
  }

  val uriPrefix = "/thurloe"
  val username = "username"
  val username2 = "username2"
  val kvp1 = KeyValuePair("key", "value")
  val kvp1a = KeyValuePair("key", "modified")
  val ukvp1 = UserKeyValuePair(username, kvp1)
  val ukvp1Updated = UserKeyValuePair(username, kvp1a)
  val kvp2 = KeyValuePair("678457834", "7984574398")
  val ukvp2 = UserKeyValuePair(username, kvp2)

  describe("The Thurloe Service") {
    it("should allow key/value pairs to be set") {
      Post(uriPrefix, ukvp1) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }

      Post(uriPrefix, ukvp2) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }
    }

    it("should return stored key value pairs when requested") {
      Get(s"$uriPrefix/$username/${kvp1.key}") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(ukvp1) {
          responseAs[UserKeyValuePair]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
      Get(s"$uriPrefix/$username/${kvp2.key}") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(ukvp2) {
          responseAs[UserKeyValuePair]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should let different users use the same key for different values") {
      val differentUserKeyValuePair = UserKeyValuePair(username2, KeyValuePair(ukvp1.keyValuePair.key, "something completely different"))

      Post(uriPrefix, differentUserKeyValuePair) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }

      // The original is unaffected:
      Get(s"$uriPrefix/$username/${kvp1.key}") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(ukvp1) {
          responseAs[UserKeyValuePair]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }

      // The new user's data is available:
      Get(s"$uriPrefix/$username2/${kvp1.key}") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(differentUserKeyValuePair) {
          responseAs[UserKeyValuePair]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should return all stored values on GET with no key supplied.") {
      // This one should return both of the key/value pairs we've stored:
      Get(s"$uriPrefix/$username") ~> thurloeService.keyValuePairRoutes ~> check {
        val resp = responseAs[UserKeyValuePairs]
        assert(resp.userId == username)
        assert(resp.keyValuePairs.size == 2)
        assert(resp.keyValuePairs contains kvp1)
        assert(resp.keyValuePairs contains kvp2)
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should allow a key/value pair to be updated") {
      Post(uriPrefix, ukvp1Updated) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
      Get(s"$uriPrefix/$username/${kvp1a.key}") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(ukvp1Updated) {
          responseAs[UserKeyValuePair]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should allow key-based deletion") {
      Delete(s"$uriPrefix/$username/${kvp1.key}") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }

      Delete(s"$uriPrefix/$username/${kvp2.key}") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }

      Delete(s"$uriPrefix/$username2/${kvp1.key}") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should return an appropriate error code and message for a missing value during GET") {
      Get(s"$uriPrefix/$username/${kvp1.key}") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(s"Key not found: ${kvp1.key}") {
          responseAs[String]
        }
        assertResult(StatusCodes.NotFound) {
          status
        }
      }
    }

    it("should return an appropriate error code and message for a missing value during DELETE") {
      Delete(s"$uriPrefix/$username/${kvp1.key}") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(s"Key not found: ${kvp1.key}") {
          responseAs[String]
        }
        assertResult(StatusCodes.NotFound) {
          status
        }
      }
    }
  }
}

class SwaggerServiceSpec extends FlatSpec with SwaggerService with ScalatestRouteTest with Matchers with
TableDrivenPropertyChecks {
  def actorRefFactory = system

  "Thurloe swagger docs" should "return 200" in {
    Get("/swagger/thurloe.yaml") ~>
      swaggerUiResourceRoute ~>
      check {
        assertResult(StatusCodes.OK) {
          status
        }
        assertResult("2.0") {
          new Yaml()
            .loadAs(responseAs[String], classOf[java.util.Map[String, AnyRef]])
            .get("swagger")
        }
      }

    Get("/swagger/index.html") ~>
      swaggerUiResourceRoute ~>
      check {
        assertResult(StatusCodes.OK) {
          status
        }
        assertResult("<!DOCTYPE html>") {
          responseAs[String].take(15)
        }
      }
  }

  "Thurloe swagger route" should "return 200 for all options" in {
    val pathExamples = Table("path", "/", "/swagger", "/swagger/thurloe.yaml", "/swagger/index.html", "/api",
      "/api/thurloe/", "/thurloe")

    forAll(pathExamples) { path =>
      Options(path) ~>
        swaggerUiResourceRoute ~>
        check {
          assertResult(StatusCodes.OK) {
            status
          }
          assertResult("OK") {
            responseAs[String]
          }
        }
    }
  }
}
