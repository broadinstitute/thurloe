package thurloe.service

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import thurloe.dataaccess.{HttpSendGridDAO, SamDAO}
import thurloe.database.ThurloeDatabaseConnector
import thurloe.security.CSPDirective.addCSP

class ThurloeServiceActor(httpSamDao: SamDAO) extends FireCloudProtectedServices with StatusService {
  val authConfig = ConfigFactory.load().getConfig("auth")

  val samDao = httpSamDao
  override val dataAccess = ThurloeDatabaseConnector
  override val sendGridDAO = new HttpSendGridDAO(samDao)
  protected val swaggerUiPath = "META-INF/resources/webjars/swagger-ui/5.17.14"

  def route: Route = addCSP {
    swaggerUiService ~ statusRoute ~ fireCloudProtectedRoutes
  }

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
