package thurloe.dataaccess

import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import okhttp3.Protocol
import org.broadinstitute.dsde.workbench.client.sam
import org.broadinstitute.dsde.workbench.client.sam.ApiClient
import org.broadinstitute.dsde.workbench.client.sam.api.AdminApi
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes

import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._

class HttpSamDAO(config: Config, credentials: GoogleCredentialModes.Pem)
    extends SamDAO
    with LazyLogging {

  val samConfig = config.getConfig("sam")

  private val samServiceURL = samConfig.getString("samBaseUrl")
  private val timeout = samConfig.getDuration("timeout").toScala

  val USERINFO_EMAIL = "https://www.googleapis.com/auth/userinfo.email"
  val USERINFO_PROFILE = "https://www.googleapis.com/auth/userinfo.profile"

  private def getApiClient = {
    val okHttpClient = new ApiClient().getHttpClient

    val okHttpClientBuilder = okHttpClient.newBuilder
      .readTimeout(timeout.toJava)

    val samApiClient = new ApiClient(okHttpClientBuilder.protocols(Seq(Protocol.HTTP_1_1).asJava).build())
    samApiClient.setBasePath(samServiceURL)

    //Set credentials
    val scopes = List(USERINFO_EMAIL, USERINFO_PROFILE)
    val saPemCredentials = credentials.toGoogleCredential(scopes)
    val expiresInSeconds = Option(saPemCredentials.getExpiresInSeconds).map(_.longValue()).getOrElse(0L)
    val token = if (expiresInSeconds < 60 * 5) {
      saPemCredentials.refreshToken()
      saPemCredentials.getAccessToken
    } else {
      saPemCredentials.getAccessToken
    }
    samApiClient.setAccessToken(token)
    samApiClient
  }

  protected def adminApi(samApiClient: ApiClient) = new AdminApi(samApiClient)

  override def getUserById(userId: String): List[sam.model.User] =
    try {
      adminApi(getApiClient).adminGetUsersByQuery(userId, userId, userId, 5).asScala.toList
    } catch {
      case e: Exception =>
        logger.warn(s"Sam user not found: $userId", e)
        List.empty
    }

}
