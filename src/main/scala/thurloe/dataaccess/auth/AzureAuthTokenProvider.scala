package thurloe.dataaccess.auth

import com.azure.core.credential.TokenRequestContext
import com.azure.identity.DefaultAzureCredentialBuilder
import com.typesafe.config.Config
import com.azure.core.management.AzureEnvironment

import java.time.Duration

class AzureAuthTokenProvider(azureConfig: Config) extends CloudServiceAuthTokenProvider {

  private val azureEnvironment = azureConfig.getString("azureEnvironment")
  private val tokenScope = azureConfig.getString("tokenScope")
  private val TOKEN_ACQUISITION_TIMEOUT = 30L

  private val credentialBuilder: DefaultAzureCredentialBuilder =
    new DefaultAzureCredentialBuilder()
      .authorityHost(
        AzureEnvironmentConverter
          .fromString(azureEnvironment)
          .getActiveDirectoryEndpoint
      )

  private val tokenRequestContext: TokenRequestContext = {
    val trc = new TokenRequestContext()
    trc.addScopes(tokenScope)
    trc
  }

  override def getAccessToken: String = {
    // The desired client id can be set using the env variable AZURE_CLIENT_ID.
    // If not set, the client ID of the system assigned managed identity will be used.
    val credentials = credentialBuilder
      .build()

    val token = credentials
      .getToken(tokenRequestContext)
      .block(Duration.ofSeconds(TOKEN_ACQUISITION_TIMEOUT))

    token.getToken
  }
}

object AzureEnvironmentConverter {
  val Azure: String = "AZURE"
  val AzureGov: String = "AZURE_GOV"

  def fromString(s: String): AzureEnvironment = s match {
    case AzureGov => AzureEnvironment.AZURE_US_GOVERNMENT
    // a bit redundant, but I want to have a explicit case for Azure for clarity, even though it's the default
    case Azure => AzureEnvironment.AZURE
    case _     => AzureEnvironment.AZURE
  }
}
