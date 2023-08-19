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

  val okHttpClientWithTracingBuilder = okHttpClient.newBuilder
    .readTimeout(timeout.toJava)

  val samApiClient = new ApiClient(okHttpClientWithTracingBuilder.protocols(Seq(Protocol.HTTP_1_1).asJava).build())
  samApiClient.setBasePath(samServiceURL)
  samApiClient.setAccessToken(credentials.toGoogleCredential(List.empty).getAccessToken)

  protected def adminApi() = new AdminApi(samApiClient)

  override def getUserById(userId: String): List[sam.model.User] =
    adminApi().adminGetUsersByQuery(userId, userId, userId, 5).asScala.toList

}
