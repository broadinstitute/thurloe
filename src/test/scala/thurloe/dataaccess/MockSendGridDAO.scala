package thurloe.dataaccess

import com.sendgrid.SendGrid.Response
import com.sendgrid._
import thurloe.database.ThurloeDatabaseConnector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by thibault on 6/23/16.
 */
class MockSendGridDAO extends SendGridDAO {

  val desiredFrom = "good_from_user"
  val ok = new Response(200, "OK")

  val validNotificationIds = Seq("valid_notification_id1", "valid_notification_id2")

  override def sendEmail(email: SendGrid.Email): Future[Response] = Future {
    val usedId = email.getFilters.getJSONObject("templates").getJSONObject("settings").getString("template_id")
    email match {
      case e if validNotificationIds.contains(e.getFilters.getJSONObject("templates").getJSONObject("settings").getString("template_id")) => ok
      case _ => throw new NotificationException(Seq("a_user_id"), usedId)
    }
  }

  val testUserId1 = "a_user_id"
  val testUserEmail1 = "a_user_email"
  val testUserContactEmail = "a_user_contactEmail"

  val testUserId2 = "a_user_id2"
  val testUserEmail2 = "a_user_email2"
  val testUserContactEmail2 = ""

  val testUserId3 = "a_user_id3"
  val testUserEmail3 = ""
  val testUserContactEmail3 = ""

  val preferredEmailMap = Map(testUserId1 -> (testUserEmail1, testUserContactEmail),
    testUserId2 -> (testUserEmail2, testUserContactEmail2))

  override def lookupPreferredEmail(userId: String): Future[String] = Future {
    preferredEmailMap get userId match {
      case Some((email, contactEmail)) => if(contactEmail.isEmpty) email else contactEmail
      case _ => throw new Exception("Not Found")
    }
  }
}