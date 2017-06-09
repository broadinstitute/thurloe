package thurloe.dataaccess

import com.sendgrid.SendGrid.Response
import com.sendgrid._
import org.broadinstitute.dsde.rawls.model.{RawlsUserEmail, RawlsUserSubjectId}
import spray.http.StatusCodes
import thurloe.database.ThurloeDatabaseConnector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by mbemis on 6/16/16.
 */
class HttpSendGridDAO extends SendGridDAO {
  val dataAccess = ThurloeDatabaseConnector

  override def sendEmail(email: SendGrid.Email): Future[Response] = {
    val sendGrid = new SendGrid(apiKey)

    Future {
      val response = sendGrid.send(email)
      if(response.getStatus) response
      else throw new NotificationException(StatusCodes.InternalServerError, "Unable to send notification", email.getTos, email.getFilters.getJSONObject("templates").getJSONObject("settings").getString("template_id"))
    }
  }

  def lookupPreferredEmail(userId: RawlsUserSubjectId): Future[RawlsUserEmail] = {
    for {
      contactEmail <- dataAccess.lookup(userId.value, "contactEmail")
      email <- dataAccess.lookup(userId.value, "email")
    } yield if(contactEmail.keyValuePair.value.isEmpty) RawlsUserEmail(email.keyValuePair.value) else RawlsUserEmail(contactEmail.keyValuePair.value)
  }
}
