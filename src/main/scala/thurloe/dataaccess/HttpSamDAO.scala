package thurloe.dataaccess

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import okhttp3.Protocol
import org.broadinstitute.dsde.workbench.client.sam
import org.broadinstitute.dsde.workbench.client.sam.ApiClient
import org.broadinstitute.dsde.workbench.client.sam.api.AdminApi
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes

import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._

class HttpSamDAO(config: Config, credentials: GoogleCredentialModes.Pem) extends SamDAO with LazyLogging {

  val samConfig = config.getConfig("sam")

  private val samServiceURL = samConfig.getString("samBaseUrl")
  private val timeout = samConfig.getDuration("timeout").toScala

  private val okHttpClient = new ApiClient().getHttpClient

  val okHttpClientBuilder = okHttpClient.newBuilder
    .readTimeout(timeout.toJava)

  val samApiClient = new ApiClient(okHttpClientBuilder.protocols(Seq(Protocol.HTTP_1_1).asJava).build())
  samApiClient.setBasePath(samServiceURL)

  //Set credentials
  val scopes = List.empty
  val token = credentials.toGoogleCredential(scopes).getAccessToken
  samApiClient.setAccessToken(credentials.toGoogleCredential(scopes).getAccessToken)

  protected def adminApi() = new AdminApi(samApiClient)

  logger.info(s"Using access token: $token")

  override def getUserById(userId: String): List[sam.model.User] = {
    logger.info(s"Using access token: $token")
    logger.info(samApiClient.getAuthentications.asScala.mkString("\n"))
    adminApi().adminGetUsersByQuery(userId, userId, userId, 5).asScala.toList
  }

}
