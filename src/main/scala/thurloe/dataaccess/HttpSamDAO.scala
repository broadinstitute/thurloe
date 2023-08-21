package thurloe.dataaccess

import akka.actor.ActorSystem
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import okhttp3.Protocol
import org.broadinstitute.dsde.workbench.client.sam
import org.broadinstitute.dsde.workbench.client.sam.ApiClient
import org.broadinstitute.dsde.workbench.client.sam.api.AdminApi
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes

import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._

class HttpSamDAO(config: Config, credentials: GoogleCredentialModes.Pem)(implicit val system: ActorSystem)
    extends SamDAO
    with LazyLogging {

  val samConfig = config.getConfig("sam")

  private val samServiceURL = samConfig.getString("samBaseUrl")
  private val timeout = samConfig.getDuration("timeout").toScala

  private def getApiClient = {
    val okHttpClient = new ApiClient().getHttpClient

    val okHttpClientBuilder = okHttpClient.newBuilder
      .readTimeout(timeout.toJava)

    val samApiClient = new ApiClient(okHttpClientBuilder.protocols(Seq(Protocol.HTTP_1_1).asJava).build())
    samApiClient.setBasePath(samServiceURL)

    //Set credentials
    val scopes = List.empty
    val saPemCredentials: GoogleCredential = credentials.toGoogleCredential(scopes)
    val token = getServiceAccountAccessToken(saPemCredentials)
    system.log.info(s"Using credentials for sam: $token")
    samApiClient.setAccessToken(getServiceAccountAccessToken(saPemCredentials))
    samApiClient
  }

  private def getServiceAccountAccessToken(saPemCredentials: GoogleCredential): String = {
    val expiresInSeconds = Option(saPemCredentials.getExpiresInSeconds).map(_.longValue()).getOrElse(0L)
    if (expiresInSeconds < 60 * 5) {
      saPemCredentials.refreshToken()
    }
    saPemCredentials.getAccessToken
  }

  protected def adminApi(samApiClient: ApiClient) = new AdminApi(samApiClient)

  override def getUserById(userId: String): List[sam.model.User] =
    adminApi(getApiClient).adminGetUsersByQuery(userId, userId, userId, 5).asScala.toList

}
