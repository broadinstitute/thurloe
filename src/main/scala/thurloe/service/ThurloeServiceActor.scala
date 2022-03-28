package thurloe.service

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import thurloe.dataaccess.HttpSendGridDAO
import thurloe.database.ThurloeDatabaseConnector

class ThurloeServiceActor extends FireCloudProtectedServices with StatusService {
  val authConfig = ConfigFactory.load().getConfig("auth")

  override val dataAccess = ThurloeDatabaseConnector
  override val sendGridDAO = new HttpSendGridDAO
  protected val swaggerUiPath = "META-INF/resources/webjars/swagger-ui/4.1.3"

  def route: Route =
    swaggerUiService ~ statusRoute ~ fireCloudProtectedRoutes

  val swaggerUiService = {
    path("") {
      get {
        serveIndex
      }
    } ~
      path("api-docs.yaml") {
        get {
          getFromResource("swagger/thurloe.yaml")
        }
      } ~
      // We have to be explicit about the paths here since we're matching at the root URL and we don't
      // want to catch all paths lest we circumvent Spray's not-found and method-not-allowed error
      // messages.
      (pathPrefixTest("swagger-ui") | pathPrefixTest("oauth2") | pathSuffixTest("js")
        | pathSuffixTest("css") | pathPrefixTest("favicon")) {
        get {
          getFromResourceDirectory(swaggerUiPath)
        }
      }
  }

  private val serveIndex: Route = {
    val swaggerOptions =
      s"""
         |        validatorUrl: null,
         |        apisSorter: "alpha",
         |        operationsSorter: "alpha"
      """.stripMargin

    mapResponseEntity { entityFromJar =>
      entityFromJar.transformDataBytes(Flow.fromFunction[ByteString, ByteString] { original: ByteString =>
        ByteString(
          original.utf8String
            .replace("""url: "https://petstore.swagger.io/v2/swagger.json"""", "url: '/api-docs.yaml'")
            .replace("""layout: "StandaloneLayout"""", s"""layout: "StandaloneLayout", $swaggerOptions""")
            .replace(
              "window.ui = ui",
              s"""ui.initOAuth({
                 |        clientId: "${authConfig.getString("googleClientId")}",
                 |        appName: "Thurloe",
                 |        scopeSeparator: " ",
                 |        additionalQueryStringParams: {}
                 |      })
                 |      window.ui = ui
                 |      """.stripMargin
            )
        )
      })
    } {
      getFromResource(s"$swaggerUiPath/index.html")
    }
  }
}
