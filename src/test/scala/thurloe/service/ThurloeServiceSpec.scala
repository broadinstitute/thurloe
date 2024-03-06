package thurloe.service

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.broadinstitute.dsde.workbench.client.sam
import org.mockito.Mockito.when
import org.mockito.MockitoSugar.mock
import org.scalatest.funspec.AnyFunSpec
import thurloe.dataaccess.{HttpSamDAO, SamDAO}
import thurloe.database.{MockUnhealthyThurloeDatabaseConnector, ThurloeDatabaseConnector}

import java.util.UUID

class ThurloeServiceSpec extends AnyFunSpec with ScalatestRouteTest {

  import ApiDataModelsJsonProtocol._

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

  val u1k1v1 = UserKeyValuePairs(user1, Seq(k1v1))
  val u1k1v1a = UserKeyValuePairs(user1, Seq(k1v1a))
  val u1k2v2 = UserKeyValuePairs(user1, Seq(k2v2))
  val u1batch = UserKeyValuePairs(user1, Seq(k1v1, k2v2))

  val u2k1v2 = UserKeyValuePairs(user2, Seq(k1v2))
  val u2k2v1 = UserKeyValuePairs(user2, Seq(k2v1))

  val u3k3v3 = UserKeyValuePairs("NEVER FOUND", Seq(KeyValuePair("NEvER", "FOUND")))

  def thurloeService = new ThurloeService {
    val samDao: SamDAO = mock[SamDAO]

    val samUser1 = new sam.model.User()
    val samUser2 = new sam.model.User()

    samUser1.setId(user1)
    samUser1.setAzureB2CId(user1)
    samUser1.setGoogleSubjectId(user1)

    samUser2.setId(user2)
    samUser2.setAzureB2CId(user2)
    samUser2.setGoogleSubjectId(user2)

    when(samDao.getUserById(user1)).thenReturn(List(samUser1))
    when(samDao.getUserById(user2)).thenReturn(List(samUser2))
    when(samDao.getUserById("NEVER FOUND")).thenReturn(List.empty)

    val dataAccess = ThurloeDatabaseConnector
    def actorRefFactory = system
  }

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

    it("should allow multiple key/value pairs to be set") {
      Post(uriPrefix, u1batch) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }

      // We post this one but it should never be found in the queries
      Get(s"$uriPrefix/$user1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1batch) {
          responseAs[UserKeyValuePairs]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should return stored key value pairs when requested") {
      Get(s"$uriPrefix/$user1/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k1v1.toKeyValueSeq.head) {
          responseAs[UserKeyValuePair]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
      Get(s"$uriPrefix/$user1/${k2v2.key}") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k2v2.toKeyValueSeq.head) {
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
        assertResult(u1k1v1.toKeyValueSeq.head) {
          responseAs[UserKeyValuePair]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }

      // The new user's data is available:
      Get(s"$uriPrefix/$user2/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u2k1v2.toKeyValueSeq.head) {
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
        r foreach { x => assert(x.userId == user1) }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should not allow custom queries with no parameters") {
      Get(s"$uriPrefix") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(StatusCodes.BadRequest) {
          status
        }
      }
    }

    it("should allow custom queries based on multiple userIds") {
      Get(s"$uriPrefix?userId=$user1&userId=$user2") ~> thurloeService.keyValuePairRoutes ~> check {
        val r: Seq[UserKeyValuePair] = responseAs[Seq[UserKeyValuePair]]
        assert(r.size == 4)
        r foreach { x => assert(x.userId == user1 || x.userId == user2) }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should allow custom queries based on key only") {
      Get(s"$uriPrefix?key=$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        val r: Seq[UserKeyValuePair] = responseAs[Seq[UserKeyValuePair]]
        assert(r.size == 2)
        r foreach { x => assert(x.keyValuePair.key == key1) }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should allow custom queries based on multiple keys") {
      Get(s"$uriPrefix?key=$key1&key=$key2") ~> thurloeService.keyValuePairRoutes ~> check {
        val r: Seq[UserKeyValuePair] = responseAs[Seq[UserKeyValuePair]]
        assert(r.size == 4)
        r foreach { x => assert(x.keyValuePair.key == key1 || x.keyValuePair.key == key2) }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should allow custom queries based on value only") {
      Get(s"$uriPrefix?value=$value1") ~> thurloeService.keyValuePairRoutes ~> check {
        val response: Seq[UserKeyValuePair] = responseAs[Seq[UserKeyValuePair]]
        assert(response.size == 2)
        response foreach { x => assert(x.keyValuePair.value == value1) }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should allow custom queries based on multiple values") {
      Get(s"$uriPrefix?value=$value1&value=$value2") ~> thurloeService.keyValuePairRoutes ~> check {
        val response: Seq[UserKeyValuePair] = responseAs[Seq[UserKeyValuePair]]
        assert(response.size == 4)
        response foreach { x => assert(x.keyValuePair.value == value1 || x.keyValuePair.value == value2) }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should allow custom queries based on userId and key") {
      Get(s"$uriPrefix?userId=$user1&key=$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        val response: Seq[UserKeyValuePair] = responseAs[Seq[UserKeyValuePair]]
        assert(response.size == 1)
        response foreach { x => assert(x.userId == user1 && x.keyValuePair.key == key1) }
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
        assertResult(u1k1v1a.toKeyValueSeq.head) {
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

    it("should fail with internal server error") {
      val errorThurloe = new ThurloeService {
        val samDao: SamDAO = mock[HttpSamDAO]
        when(samDao.getUserById(user1)).thenReturn(List.empty)
        val dataAccess = MockUnhealthyThurloeDatabaseConnector
        def actorRefFactory = system
      }
      Get(s"$uriPrefix?userId=$user1") ~> errorThurloe.keyValuePairRoutes ~> check {
        assertResult(StatusCodes.InternalServerError) {
          status
        }
      }
    }

    it("should handle sam users with differing ids") {
      // prepare values and mocks
      val userSamId = UUID.randomUUID().toString
      val userSubjectId = UUID.randomUUID().toString
      val userB2cId = UUID.randomUUID().toString
      val user1 = new sam.model.User()

      user1.setId(userSamId)
      user1.setGoogleSubjectId(userSubjectId)
      user1.setAzureB2CId(userB2cId)

      val key1 = "key1"
      val value1 = "value1"
      val k1v1 = KeyValuePair(key1, value1)
      val key2 = "key2"
      val value2 = "value2"
      val k2v2 = KeyValuePair(key2, value2)
      val thurloeService = new ThurloeService {
        val samDao: SamDAO = mock[HttpSamDAO]
        when(samDao.getUserById(userB2cId)).thenReturn(List(user1))
        when(samDao.getUserById(userSubjectId)).thenReturn(List(user1))
        when(samDao.getUserById(userSamId)).thenReturn(List(user1))
        val dataAccess = ThurloeDatabaseConnector
        def actorRefFactory = system
      }

      val u1k1v1B2cId = UserKeyValuePairs(userB2cId, Seq(k1v1))
      val u1k1v1SubjectId = UserKeyValuePairs(userSubjectId, Seq(k1v1))
      val u1k1v1SamId = UserKeyValuePairs(userSamId, Seq(k1v1))

      val u1k2v2B2cId = UserKeyValuePairs(userB2cId, Seq(k2v2))
      val u1k2v2subjectId = UserKeyValuePairs(userSubjectId, Seq(k2v2))
      val u1k2v2SamId = UserKeyValuePairs(userSamId, Seq(k2v2))

      // Create key values for same user with different ids
      Post(uriPrefix, u1k1v1B2cId) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }

      Post(uriPrefix, u1k2v2subjectId) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }

      // Assert that all ids can be used to get the same key value
      Get(s"$uriPrefix/$userSubjectId/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k1v1SubjectId.toKeyValueSeq.head) {
          responseAs[UserKeyValuePair]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
      Get(s"$uriPrefix/$userB2cId/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k1v1B2cId.toKeyValueSeq.head) {
          responseAs[UserKeyValuePair]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
      Get(s"$uriPrefix/$userSamId/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k1v1SamId.toKeyValueSeq.head) {
          responseAs[UserKeyValuePair]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }

      Get(s"$uriPrefix/$userSamId/$key2") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k2v2SamId.toKeyValueSeq.head) {
          responseAs[UserKeyValuePair]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
      Get(s"$uriPrefix/$userSubjectId/$key2") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k2v2subjectId.toKeyValueSeq.head) {
          responseAs[UserKeyValuePair]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
      Get(s"$uriPrefix/$userB2cId/$key2") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k2v2B2cId.toKeyValueSeq.head) {
          responseAs[UserKeyValuePair]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it("should gracefully handle sam record collision when one of the sam records has a b2c id") {
      // prepare values and mocks
      val userSamId = UUID.randomUUID().toString
      val userSubjectId = UUID.randomUUID().toString
      val userB2cId = UUID.randomUUID().toString
      val user1 = new sam.model.User()
      user1.setId(userSamId)
      user1.setGoogleSubjectId(userSubjectId)

      val user2 = new sam.model.User()
      user2.setId(userSamId)
      user2.setGoogleSubjectId(userSubjectId)
      user2.setAzureB2CId(userB2cId)

      val key1 = "k1"
      val value1 = "v1"
      val k1v1 = KeyValuePair(key1, value1)

      val thurloeService = new ThurloeService {
        val samDao: SamDAO = mock[HttpSamDAO]
        when(samDao.getUserById(userB2cId)).thenReturn(List(user1, user2))
        when(samDao.getUserById(userSubjectId)).thenReturn(List(user1, user2))
        when(samDao.getUserById(userSamId)).thenReturn(List(user1, user2))
        val dataAccess = ThurloeDatabaseConnector

        def actorRefFactory = system
      }

      val u1k1v1B2cId = UserKeyValuePairs(userB2cId, Seq(k1v1))
      val u1k1v1SubjectId = UserKeyValuePairs(userSubjectId, Seq(k1v1))

      // Create key values for the user
      Post(uriPrefix, u1k1v1B2cId) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }

      // Assert that a bad response is returned on sam id collision
      Get(s"$uriPrefix/$userSubjectId/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k1v1SubjectId.toKeyValueSeq.head) {
          responseAs[UserKeyValuePair]
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it(
      "should fallback on the userId in the request when multiple records are returned from sam and none have an azure b2c id"
    ) {
      // prepare values and mocks
      val userSamId = UUID.randomUUID().toString
      val userSubjectId = UUID.randomUUID().toString
      val user1 = new sam.model.User()
      user1.setId(userSamId)
      user1.setGoogleSubjectId(userSubjectId)

      val user2 = new sam.model.User()
      user2.setId(userSamId)
      user2.setGoogleSubjectId(userSubjectId)

      val key1 = "key1"
      val value1 = "value1"
      val k1v1 = KeyValuePair(key1, value1)

      val thurloeService = new ThurloeService {
        val samDao: SamDAO = mock[HttpSamDAO]
        when(samDao.getUserById(userSubjectId)).thenReturn(List(user1, user2))
        when(samDao.getUserById(userSamId)).thenReturn(List(user1, user2))
        val dataAccess = ThurloeDatabaseConnector

        def actorRefFactory = system
      }

      val u1k1v1SubjectId = UserKeyValuePairs(userSubjectId, Seq(k1v1))

      // Assert that a bad response is returned on sam id collision
      Post(uriPrefix, u1k1v1SubjectId) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(
          ""
        ) {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }
    }

    it(
      "should resolve collisions between isRegistrationComplete records when it is stored under multiple types of ids for the same user"
    ) {
      // prepare values and mocks
      val userSamId = UUID.randomUUID().toString
      val userSubjectId = UUID.randomUUID().toString
      val userB2cId = UUID.randomUUID().toString
      val user1 = new sam.model.User()
      user1.setId(userSamId)
      user1.setGoogleSubjectId(userSubjectId)
      user1.setAzureB2CId(userB2cId)

      val key1 = "isRegistrationComplete"
      val value1 = "1"
      val k1v1 = KeyValuePair(key1, value1)

      val value2 = "2"
      val k1v2 = KeyValuePair(key1, value2)

      val value3 = "0"
      val k1v3 = KeyValuePair(key1, value3)

      val thurloeService = new ThurloeService {
        val samDao: SamDAO = mock[HttpSamDAO]
        when(samDao.getUserById(userB2cId)).thenReturn(List(user1))
        when(samDao.getUserById(userSubjectId)).thenReturn(List(user1))
        when(samDao.getUserById(userSamId)).thenReturn(List(user1))
        val dataAccess = ThurloeDatabaseConnector

        def actorRefFactory = system
      }

      val u1k1v1B2cId = UserKeyValuePairs(userB2cId, Seq(k1v1))
      val u1k1v2SubjectId = UserKeyValuePairs(userSubjectId, Seq(k1v2))
      val u1k1v3SamId = UserKeyValuePairs(userSamId, Seq(k1v3))

      // Create key values for the user
      Post(uriPrefix, u1k1v1B2cId) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }

      Post(uriPrefix, u1k1v2SubjectId) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }

      Post(uriPrefix, u1k1v3SamId) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }

      // Assert that the greater value is returned
      Get(s"$uriPrefix/$userSubjectId/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k1v2SubjectId.toKeyValueSeq.head.userId) {
          responseAs[UserKeyValuePair].userId
        }
        assertResult(value2) {
          responseAs[UserKeyValuePair].keyValuePair.value
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }

      Get(s"$uriPrefix/$userB2cId/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k1v1B2cId.toKeyValueSeq.head.userId) {
          responseAs[UserKeyValuePair].userId
        }
        assertResult(value2) {
          responseAs[UserKeyValuePair].keyValuePair.value
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }

      Get(s"$uriPrefix/$userSamId/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k1v3SamId.toKeyValueSeq.head.userId) {
          responseAs[UserKeyValuePair].userId
        }
        assertResult(value2) {
          responseAs[UserKeyValuePair].keyValuePair.value
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it(
      "should resolve collisions between identical records stored under different user id types for the same user"
    ) {
      // prepare values and mocks
      val userSamId = UUID.randomUUID().toString
      val userSubjectId = UUID.randomUUID().toString
      val userB2cId = UUID.randomUUID().toString
      val user1 = new sam.model.User()
      user1.setId(userSamId)
      user1.setGoogleSubjectId(userSubjectId)
      user1.setAzureB2CId(userB2cId)

      val key1 = "key1"
      val value1 = "value1"
      val k1v1 = KeyValuePair(key1, value1)

      val thurloeService = new ThurloeService {
        val samDao: SamDAO = mock[HttpSamDAO]
        when(samDao.getUserById(userB2cId)).thenReturn(List(user1))
        when(samDao.getUserById(userSubjectId)).thenReturn(List(user1))
        when(samDao.getUserById(userSamId)).thenReturn(List(user1))
        val dataAccess = ThurloeDatabaseConnector

        def actorRefFactory = system
      }

      val u1k1v1B2cId = UserKeyValuePairs(userB2cId, Seq(k1v1))
      val u1k1v1SubjectId = UserKeyValuePairs(userSubjectId, Seq(k1v1))
      val u1k1v1SamId = UserKeyValuePairs(userSamId, Seq(k1v1))

      // Create key values for the user
      Post(uriPrefix, u1k1v1B2cId) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }

      Post(uriPrefix, u1k1v1SubjectId) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }

      Post(uriPrefix, u1k1v1SamId) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }

      // Assert that the greater value is returned
      Get(s"$uriPrefix/$userSubjectId/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k1v1SubjectId.toKeyValueSeq.head.userId) {
          responseAs[UserKeyValuePair].userId
        }
        assertResult(value1) {
          responseAs[UserKeyValuePair].keyValuePair.value
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }

      Get(s"$uriPrefix/$userB2cId/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k1v1B2cId.toKeyValueSeq.head.userId) {
          responseAs[UserKeyValuePair].userId
        }
        assertResult(value1) {
          responseAs[UserKeyValuePair].keyValuePair.value
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }

      Get(s"$uriPrefix/$userSamId/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k1v1SamId.toKeyValueSeq.head.userId) {
          responseAs[UserKeyValuePair].userId
        }
        assertResult(value1) {
          responseAs[UserKeyValuePair].keyValuePair.value
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it(
      "should resolve collisions between records stored under different user id types by choosing the latest record that is not N/A"
    ) {
      // prepare values and mocks
      val userSamId = UUID.randomUUID().toString
      val userSubjectId = UUID.randomUUID().toString
      val userB2cId = UUID.randomUUID().toString
      val user1 = new sam.model.User()
      user1.setId(userSamId)
      user1.setGoogleSubjectId(userSubjectId)
      user1.setAzureB2CId(userB2cId)

      val key1 = "key1"
      val value1 = "value1"
      val k1v1 = KeyValuePair(key1, value1)

      val value2 = "value2"
      val k1v2 = KeyValuePair(key1, value2)

      val value3 = "N/A"
      val k1v3 = KeyValuePair(key1, value3)

      val thurloeService = new ThurloeService {
        val samDao: SamDAO = mock[HttpSamDAO]
        when(samDao.getUserById(userB2cId)).thenReturn(List(user1))
        when(samDao.getUserById(userSubjectId)).thenReturn(List(user1))
        when(samDao.getUserById(userSamId)).thenReturn(List(user1))
        val dataAccess = ThurloeDatabaseConnector

        def actorRefFactory = system
      }

      val u1k1v1B2cId = UserKeyValuePairs(userB2cId, Seq(k1v1))
      val u1k1v2SubjectId = UserKeyValuePairs(userSubjectId, Seq(k1v2))
      val u1k1v3SamId = UserKeyValuePairs(userSamId, Seq(k1v3))

      // Create key values for the user
      Post(uriPrefix, u1k1v1B2cId) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }

      Post(uriPrefix, u1k1v2SubjectId) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }

      Post(uriPrefix, u1k1v3SamId) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }

      // Assert that the greater value is returned
      Get(s"$uriPrefix/$userB2cId/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k1v1B2cId.toKeyValueSeq.head.userId) {
          responseAs[UserKeyValuePair].userId
        }
        assertResult(value2) {
          responseAs[UserKeyValuePair].keyValuePair.value
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }

      Get(s"$uriPrefix/$userSubjectId/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k1v2SubjectId.toKeyValueSeq.head.userId) {
          responseAs[UserKeyValuePair].userId
        }
        assertResult(value2) {
          responseAs[UserKeyValuePair].keyValuePair.value
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }

      Get(s"$uriPrefix/$userSamId/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k1v3SamId.toKeyValueSeq.head.userId) {
          responseAs[UserKeyValuePair].userId
        }
        assertResult(value2) {
          responseAs[UserKeyValuePair].keyValuePair.value
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }

    it(
      "should resolve collisions between records stored under different user id types by falling back on choosing the latest record"
    ) {
      // prepare values and mocks
      val userSamId = UUID.randomUUID().toString
      val userSubjectId = UUID.randomUUID().toString
      val userB2cId = UUID.randomUUID().toString
      val user1 = new sam.model.User()
      user1.setId(userSamId)
      user1.setGoogleSubjectId(userSubjectId)
      user1.setAzureB2CId(userB2cId)

      val key1 = "key1"
      val value1 = "value1"
      val k1v1 = KeyValuePair(key1, value1)

      val value2 = "value2"
      val k1v2 = KeyValuePair(key1, value2)

      val value3 = "value3"
      val k1v3 = KeyValuePair(key1, value3)

      val thurloeService = new ThurloeService {
        val samDao: SamDAO = mock[HttpSamDAO]
        when(samDao.getUserById(userB2cId)).thenReturn(List(user1))
        when(samDao.getUserById(userSubjectId)).thenReturn(List(user1))
        when(samDao.getUserById(userSamId)).thenReturn(List(user1))
        val dataAccess = ThurloeDatabaseConnector

        def actorRefFactory = system
      }

      val u1k1v1B2cId = UserKeyValuePairs(userB2cId, Seq(k1v1))
      val u1k1v2SubjectId = UserKeyValuePairs(userSubjectId, Seq(k1v2))
      val u1k1v3SamId = UserKeyValuePairs(userSamId, Seq(k1v3))

      // Create key values for the user
      Post(uriPrefix, u1k1v1B2cId) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }

      Post(uriPrefix, u1k1v2SubjectId) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }

      Post(uriPrefix, u1k1v3SamId) ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult("") {
          responseAs[String]
        }
        assertResult(StatusCodes.Created) {
          status
        }
      }

      // Assert that the greater value is returned
      Get(s"$uriPrefix/$userB2cId/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k1v1B2cId.toKeyValueSeq.head.userId) {
          responseAs[UserKeyValuePair].userId
        }
        assertResult(value3) {
          responseAs[UserKeyValuePair].keyValuePair.value
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }

      Get(s"$uriPrefix/$userSubjectId/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k1v2SubjectId.toKeyValueSeq.head.userId) {
          responseAs[UserKeyValuePair].userId
        }
        assertResult(value3) {
          responseAs[UserKeyValuePair].keyValuePair.value
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }

      Get(s"$uriPrefix/$userSamId/$key1") ~> thurloeService.keyValuePairRoutes ~> check {
        assertResult(u1k1v3SamId.toKeyValueSeq.head.userId) {
          responseAs[UserKeyValuePair].userId
        }
        assertResult(value3) {
          responseAs[UserKeyValuePair].keyValuePair.value
        }
        assertResult(StatusCodes.OK) {
          status
        }
      }
    }
  }
}
