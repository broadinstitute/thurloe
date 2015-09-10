package thurloe.service

import spray.http.{MediaTypes, StatusCodes}
import spray.routing.HttpService

import MediaTypes._
import spray.json._
import ApiDataModelsJsonProtocol._
import thurloe.database.{KeyNotFoundException, DataAccess}

import scala.util.{Failure, Success}

trait ThurloeService extends HttpService {

  import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller

  val dataAccess: DataAccess
  val thurloePrefix = "thurloe"

  val getRoute = path(thurloePrefix / Segment / Segment) { (userId, key) =>
    get {
      dataAccess.keyLookup(userId, key) match {
        case Success(keyValuePair) =>
          respondWithMediaType(`application/json`) {
            complete {
              UserKeyValuePair(userId, keyValuePair).toJson.prettyPrint
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
      dataAccess.collectAll(userId) match {
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

  val setRoute = path(thurloePrefix) {
    post {
      entity(as[UserKeyValuePair]) { keyValuePair =>
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
      dataAccess.deleteKeyValuePair(userId, key) match {
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