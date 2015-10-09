package thurloe.service

import java.net.URLEncoder

import spray.http.HttpHeaders.RawHeader
import spray.http.MediaTypes._
import spray.http.StatusCodes
import spray.json._
import spray.routing.HttpService
import thurloe.database.DatabaseOperation.DatabaseOperation
import thurloe.database.{DataAccess, DatabaseOperation, KeyNotFoundException}
import thurloe.service.ApiDataModelsJsonProtocol._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

trait ThurloeService extends HttpService {

  import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller

  val dataAccess: DataAccess
  val ThurloePrefix = "thurloe"
  val Yaml = "thurloe.yaml"
  val Interjection = "Harumph!"
  val Swagger = "swagger"

  val getRoute = path(ThurloePrefix / Segment / Segment) { (userId, key) =>
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
              s"$Interjection $e"
            }
          }
      }
    }
  }

  val getAllRoute = path(ThurloePrefix / Segment) { (userId) =>
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
              s"$Interjection $e"
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


  val setRoute = path(ThurloePrefix) {
    post {
      entity(as[UserKeyValuePair]) { keyValuePair =>
        respondWithHeader(RawHeader("Location", s"/$ThurloePrefix/${URLEncoder.encode(keyValuePair.userId, "UTF-8")}/${URLEncoder.encode(keyValuePair.keyValuePair.key, "UTF-8")}")) {
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
                  s"$Interjection $e"
                }
              }
          }
        }
      }
    }
  }

  val deleteRoute = path(ThurloePrefix / Segment / Segment) { (userId, key) =>
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
              s"$Interjection $e"
            }
          }
      }
    }
  }

  def yamlRoute = path(Swagger / Yaml) {
    getFromResource(s"$Swagger/$Yaml")
  }

  val keyValuePairRoutes = getRoute ~ getAllRoute ~ setRoute ~ deleteRoute
}