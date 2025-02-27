package thurloe.dataaccess

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import okhttp3.{Dispatcher, Protocol}
import org.broadinstitute.dsde.workbench.client.sam
import org.broadinstitute.dsde.workbench.client.sam.ApiClient
import org.broadinstitute.dsde.workbench.client.sam.api.AdminApi
import thurloe.dataaccess.auth.CloudServiceAuthTokenProvider

import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._

class HttpSamDAO(config: Config, cloudServiceAuthTokenProvider: CloudServiceAuthTokenProvider)
    extends SamDAO
    with LazyLogging {

  private val samConfig = config.getConfig("sam")

  private val samServiceURL = samConfig.getString("samBaseUrl")
  private val timeout = samConfig.getDuration("timeout").toScala

  private val okHttpClient = {
    val dispatcher = new Dispatcher()
    dispatcher.setMaxRequests(samConfig.getInt("maxConcurrentRequests"))
    dispatcher.setMaxRequestsPerHost(samConfig.getInt("maxConcurrentRequests"))
    new ApiClient().getHttpClient.newBuilder
      .readTimeout(timeout.toJava)
      .protocols(Seq(Protocol.HTTP_1_1).asJava)
      .build()
  }

  private def getApiClient = {
    val samApiClient = new ApiClient(okHttpClient)
    samApiClient.setBasePath(samServiceURL)

    val token: String = cloudServiceAuthTokenProvider.getAccessToken
    samApiClient.setAccessToken(token)
    samApiClient
  }

  protected def adminApi(samApiClient: ApiClient) = new AdminApi(samApiClient)

  override def getUserById(userId: String): List[sam.model.User] =
    try
      adminApi(getApiClient).adminGetUsersByQuery(userId, userId, userId, 5).asScala.toList
    catch {
      case e: Exception =>
        logger.warn(s"Sam user not found: $userId", e)
        List.empty
    }
}
