package thurloe.service

import org.scalatest.FunSpec
import spray.http.StatusCodes
import spray.testkit.ScalatestRouteTest
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import thurloe.database.{KeyNotFoundException, DataAccess}
import scala.collection.immutable.Map

import scala.util.{Failure, Try, Success}

class ThurloeServiceSpec extends FunSpec with ScalatestRouteTest {

  import ApiDataModelsJsonProtocol._

  def thurloeService = new ThurloeService {
    val dataAccess = MockedThurloeDatabase
    def actorRefFactory = system
  }

  val uriPrefix = "/thurloe"
  val username = "username"
  val kvp1 = KeyValuePair("key", "value")
  val ukvp1 = UserKeyValuePair(username, kvp1)
  val kvp2 = KeyValuePair("678457834", "7984574398")
  val ukvp2 = UserKeyValuePair(username, kvp2)

  describe("The Thurloe Service") {
    it("should allow key/value pairs to be set and echo the key/value pair back in response") {
      Post(uriPrefix, ukvp1) ~> thurloeService.routes ~> check {
        assertResult(StatusCodes.OK) {
          status
        }
      }

      Post(uriPrefix, ukvp2) ~> thurloeService.routes ~> check {
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should return stored key value pairs when requested") {
      Get(s"$uriPrefix/$username/${kvp1.key}") ~> thurloeService.routes ~> check {
        assertResult(ukvp1) {
          responseAs[UserKeyValuePair]
        }
      }
      Get(s"$uriPrefix/$username/${kvp2.key}") ~> thurloeService.routes ~> check {
        assertResult(ukvp2) {
          responseAs[UserKeyValuePair]
        }
      }
    }

    it("should return all stored values on GET with no key supplied.") {
      // This one should return both of the key/value pairs we've stored:
      Get(s"$uriPrefix/$username") ~> thurloeService.routes ~> check {
        val resp = responseAs[UserKeyValuePairs]
        assert(resp.userId == username)
        assert(resp.keyValuePairs.size == 2)
        assert(resp.keyValuePairs contains kvp1)
        assert(resp.keyValuePairs contains kvp2)
      }
    }

    it("should allow key-based deletion") {
      Delete(s"$uriPrefix/$username/${kvp1.key}") ~> thurloeService.routes ~> check {
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should return an appropriate error code for a missing value") {
      Get(s"$uriPrefix/$username/${kvp1.key}") ~> thurloeService.routes ~> check {
        assertResult(StatusCodes.NotFound) {
          status
        }
      }
    }
  }
}

case object MockedThurloeDatabase extends DataAccess {
  var database = Map[String, String]()

  def keyLookup(userId: String, key: String) = database get key match {
    case Some(x) => Success(KeyValuePair(key, x))
    case None => Failure(KeyNotFoundException(userId, key))
  }
  def collectAll(userId: String) = Success(
    UserKeyValuePairs(userId,
    (database map {case (key,value) => KeyValuePair(key, value)}).to[Seq]))
  def setKeyValuePair(userKeyValuePair: UserKeyValuePair): Try[Unit] = {
    val keyValuePair = userKeyValuePair.keyValuePair
    database = database + (keyValuePair.key -> keyValuePair.value)
    Success(())
  }
  def deleteKeyValuePair(userId: String, key: String): Try[Unit] = {
    if (database contains key) {
      database = database - key
      Success()
    }
    else {
      Failure(KeyNotFoundException(userId, key))
    }
  }
}