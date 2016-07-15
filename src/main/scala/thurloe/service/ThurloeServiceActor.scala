package thurloe.service

import akka.actor.{Actor, Props}
import com.typesafe.config.Config
import lenthall.config.ScalaConfig._
import lenthall.spray.SwaggerUiResourceHttpService
import lenthall.spray.WrappedRoute._
import thurloe.dataaccess.HttpSendGridDAO
import thurloe.database.ThurloeDatabaseConnector

trait SwaggerService extends SwaggerUiResourceHttpService {
  override def swaggerServiceName = "thurloe"

  override def swaggerUiVersion = "2.1.1"
}

object ThurloeServiceActor {
  def props(config: Config) = Props(classOf[ThurloeServiceActor], config)
}

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class ThurloeServiceActor(config: Config) extends Actor with ThurloeService with NotificationService with StatusService with SwaggerService {
  override val dataAccess = ThurloeDatabaseConnector
  override def actorRefFactory = context
  override val sendGridDAO = new HttpSendGridDAO

  override def receive = runRoute(
      swaggerUiResourceRoute ~
      pathPrefix("api") { keyValuePairRoutes } ~
      pathPrefix("api") { notificationRoutes } ~
      statusRoute
    )
}
