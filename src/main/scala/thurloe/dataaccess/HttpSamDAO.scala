package thurloe.dataaccess

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import okhttp3.Protocol
import org.broadinstitute.dsde.workbench.client.sam
import org.broadinstitute.dsde.workbench.client.sam.ApiClient
import org.broadinstitute.dsde.workbench.client.sam.api.AdminApi
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail

import java.io.File
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._

class HttpSamDAO(config: Config) extends SamDAO with LazyLogging {

  val gcsConfig = config.getConfig("gcs")
  val samConfig = config.getConfig("sam")

  // GCS config
  val credentials =
    GoogleCredentialModes.Pem(WorkbenchEmail(gcsConfig.getString("clientEmail")),
                              new File(gcsConfig.getString("pathToPem")))

  private val samServiceURL = samConfig.getString("samBaseUrl")
  private val timeout = samConfig.getDuration("timeout").toScala

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
