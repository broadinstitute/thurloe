package thurloe.dataaccess.auth

import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes

class GcpAuthTokenProvider(credentials: GoogleCredentialModes.Pem) extends CloudServiceAuthTokenProvider {
  val USERINFO_EMAIL = "https://www.googleapis.com/auth/userinfo.email"
  val USERINFO_PROFILE = "https://www.googleapis.com/auth/userinfo.profile"

  override def getAccessToken: String = {

    val scopes = List(USERINFO_EMAIL, USERINFO_PROFILE)
    val saPemCredentials = credentials.toGoogleCredential(scopes)
    val expiresInSeconds = Option(saPemCredentials.getExpiresInSeconds).map(_.longValue()).getOrElse(0L)
    val token = if (expiresInSeconds < 60 * 5) {
      saPemCredentials.refreshToken()
      saPemCredentials.getAccessToken
    } else {
      saPemCredentials.getAccessToken
    }
    token
  }
}
