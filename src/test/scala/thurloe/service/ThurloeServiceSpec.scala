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
  val user1 = "username"
  val user2 = "username2"
  val key1 = "key1"
  val key2 = "key2"
  val value1 = "value1"
  val value1a = "value1a"
  val value2 = "value2"
  val value3 = "value3"
  val k1v1 = KeyValuePair(key1, value1)
  val k1v1a = KeyValuePair(key1, value1a)
  val k2v2 = KeyValuePair(key2, value2)
  val k2v1 = KeyValuePair(key2, value1)
  val k1v2 = KeyValuePair(key1, value2)

  val u1k1v1 = UserKeyValuePair(user1, k1v1)
  val u1k1v1a = UserKeyValuePair(user1, k1v1a)
  val u1k2v2 = UserKeyValuePair(user1, k2v2)

  val u2k1v2 = UserKeyValuePair(user2, k1v2)
  val u2k2v1 = UserKeyValuePair(user2, k2v1)

  val u3k3v3 = UserKeyValuePair("NEVER FOUND", KeyValuePair("NEvER", "FOUND"))

  describe("The Thurloe Service") {
    it("should allow key/value pairs to be set") {
      Post(uriPrefix, u1k1v1) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }

      Post(uriPrefix, u1k2v2) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }

      // We post this one but it should never be found in the queries
      Post(uriPrefix, u3k3v3) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }
    }

    it("should return stored key value pairs when requested") {
      Get(s"$uriPrefix/$user1/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k1v1) {
          responseAs[UserKeyValuePair]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
      Get(s"$uriPrefix/$user1/${k2v2.key}") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k2v2) {
          responseAs[UserKeyValuePair]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should let different users use the same key for different values") {
      Post(uriPrefix, u2k2v1) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }
      Post(uriPrefix, u2k1v2) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }

      // The original is unaffected:
      Get(s"$uriPrefix/$user1/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k1v1) {
          responseAs[UserKeyValuePair]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }

      // The new user's data is available:
      Get(s"$uriPrefix/$user2/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u2k1v2) {
          responseAs[UserKeyValuePair]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should allow custom queries based on userId only") {
      Get(s"$uriPrefix?userId=$user1") ~> thurloeService.keyValuePairRoutes ~> check {
        val r: Seq[UserKeyValuePair] = responseAs[Seq[UserKeyValuePair]]
        assert(r.size == 2)
        r foreach { x =>
          assert(x.userId == user1)
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should allow custom queries based on multiple userIds") {
      Get(s"$uriPrefix?userId=$user1&userId=$user2") ~> thurloeService.keyValuePairRoutes ~> check {
        val r: Seq[UserKeyValuePair] = responseAs[Seq[UserKeyValuePair]]
        assert(r.size == 4)
        r foreach { x =>
          assert(x.userId == user1 || x.userId == user2)
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should allow custom queries based on key only") {
      Get(s"$uriPrefix?key=$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        val r: Seq[UserKeyValuePair] = responseAs[Seq[UserKeyValuePair]]
        assert(r.size == 2)
        r foreach { x =>
          assert(x.keyValuePair.key == key1)
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should allow custom queries based on multiple keys") {
      Get(s"$uriPrefix?key=$key1&key=$key2") ~> thurloeService.keyValuePairRoutes ~> check {
        val r: Seq[UserKeyValuePair] = responseAs[Seq[UserKeyValuePair]]
        assert(r.size == 4)
        r foreach { x =>
          assert(x.keyValuePair.key == key1 || x.keyValuePair.key == key2)
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should allow custom queries based on value only") {
      Get(s"$uriPrefix?value=$value1") ~> thurloeService.keyValuePairRoutes ~> check {
        val response: Seq[UserKeyValuePair] = responseAs[Seq[UserKeyValuePair]]
        assert(response.size == 2)
        response foreach { x =>
          assert(x.keyValuePair.value == value1)
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should allow custom queries based on multiple values") {
      Get(s"$uriPrefix?value=$value1&value=$value2") ~> thurloeService.keyValuePairRoutes ~> check {
        val response: Seq[UserKeyValuePair] = responseAs[Seq[UserKeyValuePair]]
        assert(response.size == 4)
        response foreach { x =>
          assert(x.keyValuePair.value == value1 || x.keyValuePair.value == value2)
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should allow custom queries based on userId and key") {
      Get(s"$uriPrefix?userId=$user1&key=$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        val response: Seq[UserKeyValuePair] = responseAs[Seq[UserKeyValuePair]]
        assert(response.size == 1)
        response foreach { x =>
          assert(x.userId == user1 && x.keyValuePair.key == key1)
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should return all stored values on GET with no key supplied.") {
      // This one should return both of the key/value pairs we've stored:
      Get(s"$uriPrefix/$user1") ~> thurloeService.keyValuePairRoutes ~> check {
        val resp = responseAs[UserKeyValuePairs]
        assert(resp.userId == user1)
        assert(resp.keyValuePairs.size == 2)
        assert(resp.keyValuePairs contains k1v1)
        assert(resp.keyValuePairs contains k2v2)
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should allow a key/value pair to be updated") {
      Post(uriPrefix, u1k1v1a) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
      Get(s"$uriPrefix/$user1/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k1v1a) {
          responseAs[UserKeyValuePair]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should allow key-based deletion") {
      Delete(s"$uriPrefix/$user1/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }

      Delete(s"$uriPrefix/$user1/$key2") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }

      Delete(s"$uriPrefix/$user2/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }

      Delete(s"$uriPrefix/$user2/$key2") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should return an appropriate error code and message for a missing value during GET") {
      Get(s"$uriPrefix/$user1/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(s"Key not found: $key1") {
          responseAs[String]
        }
        assertResult(StatusCodes.NotFound) {
          status
        }
      }
    }

    it("should return an appropriate error code and message for a missing value during DELETE") {
      Delete(s"$uriPrefix/$user1/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(s"Key not found: $key1") {
          responseAs[String]
        }
        assertResult(StatusCodes.NotFound) {
          status
        }
      }
    }

    ignore("should return successful status code") {
      // test database is weird -- get an error trying to select version ()
      // java.sql.SQLSyntaxErrorException: user lacks privilege or object not found: VERSION
      Get(s"$uriPrefix/status") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(StatusCodes.OK) {
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
