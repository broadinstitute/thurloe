package thurloe.service

import org.scalatest.FunSpec
import spray.http.StatusCodes
import spray.testkit.ScalatestRouteTest
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import scala.collection.immutable.Map

import scala.util.{Failure, Try, Success}

class ThurloeServiceSpec extends FunSpec with ScalatestRouteTest with ThurloeApi {

  import ApiDataModelsJsonProtocol.format
  val dataAccess = MockedThurloeService

  def actorRefFactory = system

  val uriPrefix = "/thurloe/username"
  val kvp1 = KeyValuePair("key", "value")
  val kvp2 = KeyValuePair("678457834", "7984574398")

  describe("The Thurloe Service") {
    it("should allow key/value pairs to be set and echo the key/value pair back in response") {
      Post(uriPrefix, kvp1) ~> thurloeRoutes ~> check {
        assertResult(StatusCodes.OK) {
          status
        }
      }

      Post(uriPrefix, kvp2) ~> thurloeRoutes ~> check {
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should return stored key value pairs when requested") {
      Get(s"$uriPrefix/${kvp1.key}") ~> thurloeRoutes ~> check {
        assertResult(kvp1) {
          responseAs[KeyValuePair]
        }
      }
      Get(s"$uriPrefix/${kvp2.key}") ~> thurloeRoutes ~> check {
        assertResult(kvp2) {
          responseAs[KeyValuePair]
        }
      }
    }

    it("should return all stored values on GET with no key supplied.") {
      // This one should return both of the key/value pairs we've stored:
      Get(uriPrefix) ~> thurloeRoutes ~> check {
        val resp = responseAs[Seq[KeyValuePair]]
        assert(resp.size == 2)
        assert(resp contains kvp1)
        assert(resp contains kvp2)
      }
    }

    it("should allow key-based deletion") {
      Delete(s"$uriPrefix/${kvp1.key}") ~> thurloeRoutes ~> check {
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should return an appropriate error code for a missing value") {
      Get(s"$uriPrefix/${kvp1.key}") ~> thurloeRoutes ~> check {
        assertResult(StatusCodes.NotFound) {
          status
        }
      }
    }
  }
}

case object MockedThurloeService extends DataAccess {
  var database = Map[String, String]()

  def keyLookup(key: String) = database get key match {
    case Some(x) => Success(KeyValuePair(key, x))
    case None => Failure(new KeyNotFoundException())
  }
  def collectAll() = Success((database map {case (key,value) => KeyValuePair(key, value)}).to[Seq])
  def setKeyValuePair(keyValuePair: KeyValuePair): Try[Unit] = {
    database = database + (keyValuePair.key -> keyValuePair.value)
    Success(())
  }
  def deleteKeyValuePair(key: String): Try[Unit] = {
    if (database contains key) {
      database = database - key
      Success()
    }
    else {
      Failure(new KeyNotFoundException)
    }
  }
}