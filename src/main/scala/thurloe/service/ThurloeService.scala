package thurloe.service

import akka.actor.Actor
import com.wordnik.swagger.annotations._
import spray.routing._
import spray.http._
import MediaTypes._
import spray.json._
import ApiDataModelsJsonProtocol._

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class ThurloeService extends Actor with MyService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(thurloeRoutes)
}

// this trait defines our service behavior independently from the service actor
@Api(value="/thurloe", description = "Thurloe service", produces = "application/json", position = 1)
trait MyService extends HttpService {

  import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller

  val thurloePrefix = "thurloe"

  val getRoute = path(thurloePrefix / Segment / Segment) { (userId, key) =>
    get {
      // TODO: Get a value from the DB instead of this placeholder
      respondWithMediaType(`application/json`) {
        complete {
          val json = KeyValuePair(key, "Bob Loblaw's Law Blog").toJson
          json.prettyPrint
        }
      }
    }
  }

  val getAllRoute = path(thurloePrefix / Segment) { (userId) =>
    get {
      // TODO: Get all values from the DB instead of this placeholder
      respondWithMediaType(`application/json`) {
        complete {
          val json = Array(
            KeyValuePair("key1", "Bob Loblaw's Law Blog"),
            KeyValuePair("key2", "Blah blah blah blah blah")).toJson
          json.prettyPrint
        }
      }
    }
  }

  val setRoute = path(thurloePrefix / Segment) { (userId) =>
    post {
      entity(as[KeyValuePair]) { keyValuePair =>
        // TODO: Database write here.
        respondWithMediaType(`application/json`) {
          complete {
            val json = KeyValuePair(keyValuePair.key, keyValuePair.value).toJson
            json.prettyPrint
          }
        }
      }
    }
  }

  val deleteRoute = path(thurloePrefix / Segment / Segment) { (userId, key) =>
    delete {
      // TODO: Delete the entry here.
      respondWithMediaType(`text/plain`) {
        complete {
          "Done"
        }
      }
    }
  }

  val thurloeRoutes: Route = getRoute ~ getAllRoute ~ setRoute ~ deleteRoute
}