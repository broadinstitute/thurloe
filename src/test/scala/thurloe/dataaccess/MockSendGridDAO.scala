package thurloe.dataaccess

import akka.http.scaladsl.model.StatusCodes
import com.sendgrid.SendGrid.Response
import com.sendgrid._
import org.broadinstitute.dsde.rawls.model.{RawlsUserEmail, RawlsUserSubjectId}
import thurloe.database.KeyNotFoundException
import thurloe.service.Notification

import java.util
import java.util.Collections
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by thibault on 6/23/16.
 */
class MockSendGridDAO extends SendGridDAO {

  val desiredFrom = "good_from_user"
  val ok = new Response(200, "OK")

  val validNotificationIds = Seq("valid_notification_id1", "valid_notification_id2")

  val emails = Collections.synchronizedList(new util.ArrayList[SendGrid.Email]())

  override def sendEmail(email: SendGrid.Email): Future[Response] = Future {
    val usedId = email.getFilters.getJSONObject("templates").getJSONObject("settings").getString("template_id")
    email match {
      case e if validNotificationIds.contains(e.getFilters.getJSONObject("templates").getJSONObject("settings").getString("template_id")) =>
        emails.add(e)
        ok
      case _ => throw new NotificationException(StatusCodes.BadRequest, "invalid notification id", Seq("a_user_id"), usedId)
    }
  }

  val testUserId1 = "a_user_id"
  val testUserEmail1 = "a_user_email"
  val testUserName1 = ("My", "Name")
  val testUserContactEmail = "a_user_contactEmail"

  val testUserId2 = "a_user_id2"
  val testUserEmail2 = "a_user_email2"
  val testUserName2 = ("Abby", "Testerson")
  val testUserContactEmail2 = ""

  val testUserId3 = "a_user_id3"
  val testUserEmail3 = ""
  val testUserName3 = ("Elvin", "")
  val testUserContactEmail3 = ""

  val notificationMonitorPreferredEmails = (for (i <- 0 until 10 * 4) yield (s"bar$i" -> (s"bar$i", s"bar$i")))

  val preferredEmailMap = Map(testUserId1 -> (testUserEmail1, testUserContactEmail),
    testUserId2 -> (testUserEmail2, testUserContactEmail2)) ++ notificationMonitorPreferredEmails

  val notificationMonitorUserNames = (for (i <- 0 until 10 * 4) yield (s"bar$i" -> (s"First$i", s"Last$i")))

  val nameMap = Map(testUserId1 -> testUserName1, testUserId2 -> testUserName2, testUserId3 -> testUserName3) ++ notificationMonitorUserNames

  def lookupPreferredEmail(userId: RawlsUserSubjectId): Future[RawlsUserEmail] = Future {
    preferredEmailMap get userId.value match {
      case Some((email, contactEmail)) => if(contactEmail.isEmpty) RawlsUserEmail(email) else RawlsUserEmail(contactEmail)
      case _ => throw new Exception("Not Found")
    }
  }

  def lookupUserName(userId: RawlsUserSubjectId): Future[String] = Future {
    nameMap get userId.value match {
      case Some((firstName, lastName)) => s"${firstName} ${lastName}"
      case _ => throw new Exception("Not Found")
    }
  }

  def lookupUserFirstName(userId: RawlsUserSubjectId): Future[String] = Future {
    nameMap get userId.value match {
      case Some((firstName, _)) => firstName
      case _ => throw new Exception("Not Found")
    }
  }
}

class MockSendGridDAOWithException extends MockSendGridDAO {
  override def sendNotifications(notifications: List[Notification]): Future[List[Response]] = {
    Future.failed(KeyNotFoundException("111111", "fakeKey"))
  }
}
