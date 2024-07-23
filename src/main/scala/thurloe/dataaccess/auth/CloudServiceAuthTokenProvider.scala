package thurloe.dataaccess.auth

import com.typesafe.config.Config
import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail

import java.io.File

/***
 * Provides an access token for a cloud service.
 */
trait CloudServiceAuthTokenProvider {
  def getAccessToken: String
}

/***
 * Factory for creating a CloudServiceAuthTokenProvider.
 */
object CloudServiceAuthTokenProvider {
  def createProvider(config: Config): CloudServiceAuthTokenProvider =
    if (isAzureHostingEnabled(config)) {
      new AzureAuthTokenProvider(config.getConfig("azureHosting"))
    } else {
      val gcsConfig = config.getConfig("gcs")
      val pem =
        GoogleCredentialModes.Pem(WorkbenchEmail(gcsConfig.getString("clientEmail")),
                                  new File(gcsConfig.getString("pathToPem")))

      new GcpAuthTokenProvider(pem)
    }

  def isAzureHostingEnabled(config: Config): Boolean =
    if (config.hasPath("azureHosting.enabled")) {
      config.getBoolean("azureHosting.enabled")
    } else {
      false
    }
}
