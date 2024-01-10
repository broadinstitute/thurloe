package thurloe.service

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import io.sentry.Sentry
import spray.json._
import thurloe.dataaccess.SamDAO
import thurloe.database.DatabaseOperation.DatabaseOperation
import thurloe.database.{DataAccess, DatabaseOperation, KeyNotFoundException}
import thurloe.service.ApiDataModelsJsonProtocol._

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait ThurloeService extends LazyLogging {

  val samDao: SamDAO
  val dataAccess: DataAccess
  val ThurloePrefix = "thurloe"
  val Interjection = "Harumph!"

  val getRoute: Route =
    path(ThurloePrefix / Segment / Segment) { (userId, key) =>
      get {
        val query: Future[UserKeyValuePair] = dataAccess.lookup(samDao, userId, key)
        onComplete(query) {
          case Success(keyValuePair) =>
            complete(HttpEntity(ContentTypes.`application/json`, keyValuePair.toJson.prettyPrint))
          case Failure(_: KeyNotFoundException) =>
            complete(StatusCodes.NotFound, s"Key not found: $key")
          case Failure(e) =>
            handleError(e)
        }
      }
    }

  val queryRoute: Route =
    path(ThurloePrefix) {
      get {
        parameterSeq { parameters =>
          val thurloeQuerySpec = ThurloeQuery(parameters)

          if (thurloeQuerySpec.isEmpty) {
            complete(StatusCodes.BadRequest, "No query parameters specified")
          } else {
            thurloeQuerySpec.unrecognizedFilters match {
              case Some(invalidFilters) =>
                complete(StatusCodes.BadRequest, "Bad query parameter(s): " + invalidFilters.mkString(","))
              case None =>
                val query: Future[Seq[UserKeyValuePair]] = dataAccess.lookup(samDao, ThurloeQuery(parameters))
                onComplete(query) {
                  case Success(keyValuePairs: Seq[UserKeyValuePair]) =>
                    complete(HttpEntity(ContentTypes.`application/json`, keyValuePairs.toJson.prettyPrint))
                  case Failure(e) =>
                    handleError(e)
                }
            }
          }
        }
      }
    }

  private def handleError(e: Throwable) =
    extract(_.request) { request =>
      Sentry.captureException(e)
      logger.error(s"error handling request: ${request.method} ${request.uri}", e)
      complete(StatusCodes.InternalServerError, s"$Interjection")
    }

  val getAllRoute: Route =
    path(ThurloePrefix / Segment) { (userId) =>
      get {
        onComplete(dataAccess.lookup(samDao, userId)) {
          case Success(userKeyValuePairs) =>
            complete(HttpEntity(ContentTypes.`application/json`, userKeyValuePairs.toJson.prettyPrint))
          case Failure(e) =>
            handleError(e)
        }
      }
    }

  private def statusCode(setKeyResponse: DatabaseOperation) =
    setKeyResponse match {
      case DatabaseOperation.Insert => StatusCodes.Created
      case DatabaseOperation.Upsert => StatusCodes.Created
      case _                        => StatusCodes.OK
    }

  val setRoute: Route =
    path(ThurloePrefix) {
      post {
        entity(as[UserKeyValuePairs]) { keyValuePairs =>
          onComplete(dataAccess.set(samDao, keyValuePairs)) {
            case Success(setKeyResponse) =>
              complete(statusCode(setKeyResponse), HttpEntity(ContentTypes.`application/json`, ""))
            case Failure(e) =>
              handleError(e)
          }
        }
      }
    }

  val deleteRoute: Route =
    path(ThurloePrefix / Segment / Segment) { (userId, key) =>
      delete {
        onComplete(dataAccess.delete(userId, key)) {
          case Success(_) =>
            complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, ""))
          case Failure(_: KeyNotFoundException) =>
            complete(StatusCodes.NotFound, s"Key not found: $key")
          case Failure(e) =>
            handleError(e)
        }
      }
    }

  val keyValuePairRoutes = getRoute ~ getAllRoute ~ queryRoute ~ setRoute ~ deleteRoute
}
