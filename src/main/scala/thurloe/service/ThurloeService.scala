package thurloe.service

import akka.actor.Actor
import com.wordnik.swagger.annotations._
import spray.routing._
import spray.http._
import MediaTypes._
import spray.json._
import ApiDataModelsJsonProtocol._

import scala.util.{Try, Failure, Success}

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class ThurloeService extends Actor with ThurloeApi {

  val dataAccess = DatabaseConnectedThurloe

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(thurloeRoutes)
}

case object DatabaseConnectedThurloe extends DataAccess {
  
  // TODO: Fill THESE in with database access implementation.
  def keyLookup(key: String) = Success(KeyValuePair("yek", "eulav"))
  def collectAll() = Success(Seq(
    KeyValuePair("key1", "Bob Loblaw's Law Blog"),
    KeyValuePair("key2", "Blah blah blah blah blah")))
  def setKeyValuePair(keyValuePair: KeyValuePair): Try[Unit] = Success(())
  def deleteKeyValuePair(key: String): Try[Unit] = Success()
}

// this trait defines our service behavior independently from the service actor
@Api(value="/thurloe", description = "Thurloe service", produces = "application/json", position = 1)
trait ThurloeApi extends HttpService {

  import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller
  val dataAccess: DataAccess

  val thurloePrefix = "thurloe"

  val getRoute = path(thurloePrefix / Segment / Segment) { (userId, key) =>
    get {
      dataAccess.keyLookup(key) match {
        case Success(keyValuePair) =>
          respondWithMediaType(`application/json`) {
          complete {
            keyValuePair.toJson.prettyPrint
          }
        }
        case Failure(e: KeyNotFoundException) =>
          respondWithStatus(StatusCodes.NotFound) {
            complete {
              s"Key not found: $key"
            }
          }
        case Failure(e) =>
          respondWithStatus(StatusCodes.InternalServerError) {
            complete {
              s"Oops! $e"
            }
          }
      }
    }
  }

  val getAllRoute = path(thurloePrefix / Segment) { (userId) =>
    get {
      dataAccess.collectAll() match {
        case Success(array) =>
          respondWithMediaType(`application/json`) {
            complete {
              array.toJson.prettyPrint
            }
          }
        case Failure(e) =>
          respondWithStatus(StatusCodes.InternalServerError) {
            complete {
              s"Oops! $e"
            }
          }
      }
    }
  }

  val setRoute = path(thurloePrefix / Segment) { (userId) =>
    post {
      entity(as[KeyValuePair]) { keyValuePair =>
        dataAccess.setKeyValuePair(keyValuePair) match {
          case Success(unit) =>
            respondWithMediaType(`application/json`) {
              respondWithStatus(StatusCodes.OK) {
                complete {
                  ""
                }
              }
          }
          case Failure(e) =>
            respondWithStatus(StatusCodes.InternalServerError)
            {
              complete {
                s"Oops! $e"
              }
            }
        }
      }
    }
  }

  val deleteRoute = path(thurloePrefix / Segment / Segment) { (userId, key) =>
    delete {
      dataAccess.deleteKeyValuePair(key) match {
        case Success(_) =>
          respondWithMediaType(`text/plain`) {
            complete {
              "Key deleted."
            }
          }
        case Failure(e: KeyNotFoundException) =>
          respondWithStatus(StatusCodes.NotFound) {
            complete {
              s"Key not found: $key"
            }
          }
        case Failure(e) =>
          respondWithStatus(StatusCodes.InternalServerError) {
            complete {
              s"Oops! $e"
            }
          }
      }
    }
  }

  val thurloeRoutes: Route = getRoute ~ getAllRoute ~ setRoute ~ deleteRoute
}