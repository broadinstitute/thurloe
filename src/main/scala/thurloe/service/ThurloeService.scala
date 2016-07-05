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
import scala.concurrent.Future
import scala.util.{Failure, Success}

trait ThurloeService extends HttpService {

  import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller

  val dataAccess: DataAccess
  val ThurloePrefix = "thurloe"
  val Interjection = "Harumph!"

  val getRoute = path(ThurloePrefix / Segment / Segment) { (userId, key) =>
    get {
      val query: Future[UserKeyValuePair] = dataAccess.lookup(userId, key)
      onComplete(query) {
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
              s"$Interjection $e"
            }
          }
      }
    }
  }

  val queryRoute = path(ThurloePrefix) {
    parameterSeq { parameters =>
      get {
        val thurloeQuerySpec = ThurloeQuery(parameters)
        thurloeQuerySpec.unrecognizedFilters match {
          case Some(invalidFilters) =>
            respondWithStatus(StatusCodes.BadRequest) {
              complete {
                "Bad query parameter(s): " + invalidFilters.mkString(",")
              }
            }
          case None =>
            val query: Future[Seq[UserKeyValuePair]] = dataAccess.lookup(ThurloeQuery(parameters))
            onComplete(query) {
              case Success(keyValuePairs: Seq[UserKeyValuePair]) =>
                respondWithMediaType(`application/json`) {
                  complete {
                    keyValuePairs.toJson.prettyPrint
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

  val getStatus = path(ThurloePrefix / "status") {
    get {
      onComplete(dataAccess.status()) {
        case Success(_) =>
          respondWithStatus(StatusCodes.OK) {
            complete {
              ""
            }
          }
        case Failure(e) =>
          respondWithStatus(StatusCodes.InternalServerError) {
            complete {
              s"$Interjection $e.getMessage()"
            }
          }
      }
    }
  }
  val keyValuePairRoutes = getStatus ~ getRoute ~ getAllRoute ~ queryRoute ~ setRoute ~ deleteRoute
}