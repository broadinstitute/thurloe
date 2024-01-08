package thurloe.dataaccess

import akka.http.scaladsl.model.StatusCodes
import com.sendgrid.SendGrid.Response
import com.sendgrid._
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.model.{WorkbenchEmail, WorkbenchUserId}
import thurloe.database.{KeyNotFoundException, ThurloeDatabaseConnector}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by mbemis on 6/16/16.
 */
class HttpSendGridDAO(samDao: SamDAO) extends SendGridDAO with LazyLogging {
  val dataAccess = ThurloeDatabaseConnector

  override def sendEmail(email: SendGrid.Email): Future[Response] = {
    val sendGrid = new SendGrid(apiKey)

    Future {
      val response = sendGrid.send(email)
      if (response.getStatus) response
      else
        throw new NotificationException(
          StatusCodes.InternalServerError,
          "Unable to send notification, unexpected error occurred: " + response.getMessage,
          email.getTos.toSeq,
          email.getFilters.getJSONObject("templates").getJSONObject("settings").getString("template_id")
        )
    }
  }

  //Looks up a KVP, converting empty values or missing KVPs into None
  private def lookupNonEmptyKeyValuePair(userId: String, key: String) =
    dataAccess
      .lookup(samDao, userId, key)
      .map { kvp =>
        if (kvp.keyValuePair.value.isEmpty) None
        else Some(kvp)
      }
      .recover(_ => None)

  //There are two cases that need to be handled when looking up the preferred contact email:
  // 1) If the contactEmail is not present at all, the DB query will throw an exception. So that needs to be handled.
  // 2) If the contactEmail is present but blank, it also needs to be ignored. Thurloe accepts arbitrary key/value pairs
  //    and makes no guarantees about what the data might look like, so a blank value or invalid email is a valid case.
  //    Orchestration does it's best to enforce valid emails, but blank emails are still used when a user removes their
  //    contact email (instead of deleting the k/v pair entirely). There is a larger discussion to be had about the future
  //    of this service and how to structure the data more reliably, but for now we'll deal with the current realities and
  //    defend against them here.
  //  ^-- note: the same can also all be said about the 'email' k/v pair, but a blank email is a less common case due to
  //      how profiles are populated.
  def lookupPreferredEmail(userId: WorkbenchUserId): Future[WorkbenchEmail] =
    lookupNonEmptyKeyValuePair(userId.value, "contactEmail") flatMap {
      case Some(kvp) => Future.successful(WorkbenchEmail(kvp.keyValuePair.value)) //contactEmail was found and non-empty
      case None =>
        logger.info(s"Failed to get stored contactEmail for ${userId.value}. Defaulting to account email for user.")
        lookupNonEmptyKeyValuePair(userId.value, "email") flatMap {
          case Some(kvp) =>
            Future.successful(WorkbenchEmail(kvp.keyValuePair.value)) //account email was found and non-empty
          case None => Future.failed(new KeyNotFoundException(userId.value, "email"))
        }
    }

  def lookupUserName(userId: WorkbenchUserId): Future[String] =
    for {
      firstName <- dataAccess.lookup(samDao, userId.value, "firstName")
      lastName <- dataAccess.lookup(samDao, userId.value, "lastName")
    } yield s"${firstName.keyValuePair.value} ${lastName.keyValuePair.value}"

  def lookupUserFirstName(userId: WorkbenchUserId): Future[String] =
    for {
      firstName <- dataAccess.lookup(samDao, userId.value, "firstName")
    } yield firstName.keyValuePair.value
}
