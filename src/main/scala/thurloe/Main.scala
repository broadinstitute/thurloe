package thurloe.service

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import spray.can.Http

import scala.concurrent.duration._

object Main extends App {
  // We need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-spray-can")

  // create and start our service actor
  val service = system.actorOf(Props[ThurloeServiceActor], "demo-service")

  implicit val timeout = Timeout(5.seconds)

  // TODO: the below line of code will need to be updated.  See:
  //   https://groups.google.com/a/broadinstitute.org/forum/#!topic/dsde-engineering/xV96poHj5IA
  //   broadinstitute/agora#1
  //   broadinstitute/vault-common#7

  // start a new HTTP server on port 8000 with our service actor as the handler
  IO(Http) ? Http.Bind(service, interface = "0.0.0.0", port = 8000)
}
