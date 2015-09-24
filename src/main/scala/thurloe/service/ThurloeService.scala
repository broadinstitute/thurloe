package thurloe.service

import spray.http.HttpHeaders.RawHeader
import spray.http.MediaTypes._
import spray.http.{HttpHeader, StatusCodes}
import spray.json._
import spray.routing.HttpService
import thurloe.database.DatabaseOperation.DatabaseOperation
import thurloe.database.{DatabaseOperation, DataAccess, KeyNotFoundException}
import thurloe.service.ApiDataModelsJsonProtocol._
import java.net.URLEncoder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

trait ThurloeService extends HttpService {

  import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller

  val dataAccess: DataAccess
  val thurloePrefix = "thurloe"

  val getRoute = path(thurloePrefix / Segment / Segment) { (userId, key) =>
    get {
      onComplete(dataAccess.lookup(userId, key)) {
        case Success(keyValuePairWithId) =>
          respondWithMediaType(`application/json`) {
            complete {
              UserKeyValuePair(userId, keyValuePairWithId.keyValuePair).toJson.prettyPrint
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
      onComplete(dataAccess.lookup(userId)) {
        case Success(userKeyValuePairs) =>
          respondWithMediaType(`application/json`) {
            complete {
              userKeyValuePairs.toJson.prettyPrint
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

  private def statusCode(setKeyResponse: DatabaseOperation) = {
    setKeyResponse match {
      case DatabaseOperation.Insert => StatusCodes.Created
      case _ => StatusCodes.OK
    }
  }


  val setRoute = path(thurloePrefix) {
    post {
      entity(as[UserKeyValuePair]) { keyValuePair =>
        respondWithHeader(RawHeader("Location", s"/$thurloePrefix/${URLEncoder.encode(keyValuePair.userId, "UTF-8")}/${URLEncoder.encode(keyValuePair.keyValuePair.key, "UTF-8")}")) {
          onComplete(dataAccess.set(keyValuePair)) {
            case Success(setKeyResponse) =>
              respondWithMediaType(`application/json`) {
                respondWithStatus(statusCode(setKeyResponse)) {
                  complete {
                    ""
                  }
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
    }
  }

  val deleteRoute = path(thurloePrefix / Segment / Segment) { (userId, key) =>
    delete {
      onComplete(dataAccess.delete(userId, key)) {
        case Success(_) =>
          respondWithMediaType(`text/plain`) {
            complete {
              ""
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

  val routes = getRoute ~ getAllRoute ~ setRoute ~ deleteRoute
}