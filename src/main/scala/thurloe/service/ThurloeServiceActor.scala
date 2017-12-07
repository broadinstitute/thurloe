package thurloe.service

import akka.actor.{Actor, Props}
import com.typesafe.config.{Config, ConfigFactory}
import org.parboiled.common.FileUtils
import spray.http.StatusCodes._
import spray.http._
import spray.routing.Route
import thurloe.dataaccess.HttpSendGridDAO
import thurloe.database.ThurloeDatabaseConnector

import scala.language.postfixOps

object ThurloeServiceActor {
  def props(config: Config) = Props(new ThurloeServiceActor(config))
}

class ThurloeServiceActor(config: Config) extends Actor with FireCloudProtectedServices with StatusService {
  val authConfig = ConfigFactory.load().getConfig("auth")

  override val dataAccess = ThurloeDatabaseConnector
  override def actorRefFactory = context
  override val sendGridDAO = new HttpSendGridDAO

  protected val swaggerUiPath = "META-INF/resources/webjars/swagger-ui/2.2.10-1"

  override def receive = runRoute(
    swaggerUiService ~
      fireCloudProtectedRoutes ~
      statusRoute
  )

  def withResourceFileContents(path: String)(innerRoute: String => Route): Route =
    innerRoute( FileUtils.readAllTextFromResource(path) )

  val swaggerUiService = {
    path("") {
      get {
        parameter("url") {urlparam =>
          requestUri {uri =>
            redirect(uri.withQuery(Map.empty[String,String]), MovedPermanently)
          }
        } ~
          serveIndex()
      }
    } ~
      path("thurloe.yaml") {
        get {
          withResourceFileContents("swagger/thurloe.yaml") { apiDocs =>
            complete(apiDocs)
          }
        }
      } ~
      // We have to be explicit about the paths here since we're matching at the root URL and we don't
      // want to catch all paths lest we circumvent Spray's not-found and method-not-allowed error
      // messages.
      (pathSuffixTest("o2c.html") | pathSuffixTest("swagger-ui.js")
        | pathPrefixTest("css" /) | pathPrefixTest("fonts" /) | pathPrefixTest("images" /)
        | pathPrefixTest("lang" /) | pathPrefixTest("lib" /)) {
        get {
          getFromResourceDirectory(swaggerUiPath)
        }
      }
  }

  private def serveIndex(): Route = {
    withResourceFileContents(swaggerUiPath + "/index.html") { indexHtml =>
      complete {
        val swaggerOptions =
          """
            |        validatorUrl: null,
            |        apisSorter: "alpha",
            |        operationsSorter: "alpha",
          """.stripMargin

        HttpEntity(ContentType(MediaTypes.`text/html`),
          indexHtml
            .replace("your-client-id", authConfig.getString("googleClientId"))
            .replace("scopeSeparator: \",\"", "scopeSeparator: \" \"")
            .replace("jsonEditor: false,", "jsonEditor: false," + swaggerOptions)
            .replace("url = \"http://petstore.swagger.io/v2/swagger.json\";",
              "url = '/thurloe.yaml';"))
      }
    }
  }
}
