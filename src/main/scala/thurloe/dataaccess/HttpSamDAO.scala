package thurloe.dataaccess

import com.typesafe.scalalogging.LazyLogging
import okhttp3.Protocol
import org.broadinstitute.dsde.workbench.client.sam
import org.broadinstitute.dsde.workbench.client.sam.ApiClient
import org.broadinstitute.dsde.workbench.client.sam.api.AdminApi
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes.GoogleCredentialMode

import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._

class HttpSamDAO(baseSamServiceURL: String, credentials: GoogleCredentialMode, timeout: FiniteDuration)
    extends SamDAO
    with LazyLogging {

  private val samServiceURL = baseSamServiceURL

  private val okHttpClient = new ApiClient().getHttpClient

  val scopes =
    List("https://www.googleapis.com/auth/userinfo.email", "https://www.googleapis.com/auth/userinfo.profile")

  protected def getApiClient(): ApiClient = {

    val okHttpClientWithTracingBuilder = okHttpClient.newBuilder
      .readTimeout(timeout.toJava)

    val samApiClient = new ApiClient(okHttpClientWithTracingBuilder.protocols(Seq(Protocol.HTTP_1_1).asJava).build())
    samApiClient.setBasePath(samServiceURL)
    samApiClient.setAccessToken(credentials.toGoogleCredential(scopes).getAccessToken)

    samApiClient
  }

  protected def adminApi() = new AdminApi(getApiClient())

  override def getUserById(userId: String): List[sam.model.User] =
    adminApi().adminGetUserByQuery(userId, userId, userId).asScala.toList

}
