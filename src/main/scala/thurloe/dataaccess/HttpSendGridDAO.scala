package thurloe.dataaccess

import akka.http.scaladsl.model.StatusCodes
import com.sendgrid.SendGrid.Response
import com.sendgrid._
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.rawls.model.{RawlsUserEmail, RawlsUserSubjectId}
import thurloe.database.ThurloeDatabaseConnector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by mbemis on 6/16/16.
 */
class HttpSendGridDAO extends SendGridDAO with LazyLogging {
  val dataAccess = ThurloeDatabaseConnector

  override def sendEmail(email: SendGrid.Email): Future[Response] = {
    val sendGrid = new SendGrid(apiKey)

    Future {
      val response = sendGrid.send(email)
      if (response.getStatus) response
      else
        throw new NotificationException(
          StatusCodes.InternalServerError,
          "Unable to send notification",
          email.getTos.toSeq,
          email.getFilters.getJSONObject("templates").getJSONObject("settings").getString("template_id")
        )
    }
  }

  def lookupPreferredEmail(userId: RawlsUserSubjectId): Future[RawlsUserEmail] =
    for {
      email <- dataAccess
        .lookup(userId.value, "contactEmail")
        .recoverWith { _ =>
          logger.info(s"Failed to get stored contactEmail for ${userId.value}. Defaulting to stored email for user.")
          dataAccess.lookup(userId.value, "email")
        }
    } yield RawlsUserEmail(email.keyValuePair.value)

  def lookupUserName(userId: RawlsUserSubjectId): Future[String] =
    for {
      firstName <- dataAccess.lookup(userId.value, "firstName")
      lastName <- dataAccess.lookup(userId.value, "lastName")
    } yield s"${firstName.keyValuePair.value} ${lastName.keyValuePair.value}"

  def lookupUserFirstName(userId: RawlsUserSubjectId): Future[String] =
    for {
      firstName <- dataAccess.lookup(userId.value, "firstName")
    } yield firstName.keyValuePair.value
}
