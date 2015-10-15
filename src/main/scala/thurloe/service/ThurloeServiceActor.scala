package thurloe.service

import akka.actor.{Actor, ActorRefFactory, Props}
import com.typesafe.config.Config
import lenthall.spray.ConfigSwaggerUiHttpService
import thurloe.database.ThurloeDatabaseConnector


object SwaggerService {
  /*
    Because of the implicit arg requirement apply() doesn't work here, so falling back to the less
    idiomatic (but not unheard of) from().
   */
  def from(conf: Config)(implicit actorRefFactory: ActorRefFactory): SwaggerService = {
    new SwaggerService(conf.getConfig("swagger"))
  }
}

class SwaggerService(override val swaggerUiConfig: Config)
                    (implicit val actorRefFactory: ActorRefFactory)
  extends ConfigSwaggerUiHttpService {
}

object ThurloeServiceActor {
  def props(swaggerService: SwaggerService) = Props(new ThurloeServiceActor(swaggerService))
}

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class ThurloeServiceActor(swaggerService: SwaggerService) extends Actor with ThurloeService {
  override val dataAccess = ThurloeDatabaseConnector
  override def actorRefFactory = context

  override def receive = runRoute(keyValuePairRoutes ~ yamlRoute ~ swaggerService.swaggerUiRoutes)
}

