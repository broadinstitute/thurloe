package thurloe.service

import java.net.URLEncoder

import com.typesafe.scalalogging.LazyLogging
import spray.http.MediaTypes._
import spray.http.StatusCodes
import spray.json._
import spray.routing.{HttpService, RequestContext}
import thurloe.database.DatabaseOperation.DatabaseOperation
import thurloe.database.{DataAccess, DatabaseOperation, KeyNotFoundException}
import thurloe.service.ApiDataModelsJsonProtocol._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

trait ThurloeService extends HttpService with LazyLogging {

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
          handleError(e)
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
                handleError(e)
            }
        }
      }
    }
  }

  private def handleError(e: Throwable) = {
    extract(_.request) { request =>
      logger.error(s"error handling request: ${request.method} ${request.uri}", e)
      respondWithStatus(StatusCodes.InternalServerError) {
        complete {
          s"$Interjection $e"
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
          handleError(e)
      }
    }
  }

  private def statusCode(setKeyResponse: DatabaseOperation) = {
    setKeyResponse match {
      case DatabaseOperation.Insert => StatusCodes.Created
      case DatabaseOperation.Upsert => StatusCodes.Created
      case _ => StatusCodes.OK
    }
  }


  val setRoute = path(ThurloePrefix) {
    post {
      entity(as[UserKeyValuePairs]) { keyValuePairs =>
        onComplete(dataAccess.set(keyValuePairs)) {
          case Success(setKeyResponse) =>
            respondWithMediaType(`application/json`) {
              respondWithStatus(statusCode(setKeyResponse)) {
                complete {
                  ""
                }
              }
            }
          case Failure(e) =>
            handleError(e)
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
          handleError(e)
      }
    }
  }

  val keyValuePairRoutes = getRoute ~ getAllRoute ~ queryRoute ~ setRoute ~ deleteRoute
}