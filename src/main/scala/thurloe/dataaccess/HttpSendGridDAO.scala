package thurloe.dataaccess

import com.sendgrid.SendGrid.Response
import com.sendgrid._
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

  override def lookupPreferredEmail(userId: String): Future[String] = {
    for {
      contactEmail <- dataAccess.lookup(userId, "contactEmail")
      email <- dataAccess.lookup(userId, "email")
    } yield if(contactEmail.keyValuePair.value.isEmpty) email.keyValuePair.value else contactEmail.keyValuePair.value
  }
}
