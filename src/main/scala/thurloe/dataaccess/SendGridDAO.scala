package thurloe.dataaccess

import com.sendgrid.SendGrid
import com.sendgrid.SendGrid.Response
import com.typesafe.config.ConfigFactory
import spray.http.{StatusCodes, StatusCode}
import thurloe.service.Notification

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait SendGridDAO {

  val configFile = ConfigFactory.load()
  val sendGridConfig = configFile.getConfig("sendgrid")
  val apiKey = sendGridConfig.getString("apiKey")
  val substitutionChar = sendGridConfig.getString("substitutionChar")
  val defaultFromAddress = sendGridConfig.getString("defaultFromAddress")

  def sendEmail(email: SendGrid.Email): Future[Response]
  def lookupPreferredEmail(userId: String): Future[String]

  def sendNotifications(notifications: List[Notification]): Future[List[Response]] = {
    Future.sequence(notifications.map { notification =>
      val toAddressFuture = notification.userEmail.map(Future.successful(_))
        .getOrElse(notification.userId.map(lookupPreferredEmail)
          .getOrElse(Future.failed(new NotificationException(StatusCodes.BadRequest, "No recipient specified", Seq.empty, notification.notificationId))))

      toAddressFuture flatMap { toAddress =>
        val email = createEmail(toAddress, notification.replyTo, notification.notificationId, notification.substitutions)
        sendEmail(email)
      }
    })
  }

  /*
    Note: email.setSubject and email.setText must be set even if their values
    aren't used. Supposedly this will be fixed in a future version of SendGrid
   */
  def createEmail(toAddress: String, replyTo: Option[String], notificationId: String, substitutions: Map[String, String] = Map.empty): SendGrid.Email = {
    val email = new SendGrid.Email()

    email.addTo(toAddress)
    email.setFrom(defaultFromAddress)
    email.setTemplateId(notificationId)
    email.setSubject(" ")
    replyTo.map(email.setReplyTo)
    email.setHtml(" ")
    addSubstitutions(email, substitutions)
    email
  }

  /*
    Adds a set of substitutions to an email template.
    For example, Map("workspaceName"->"TCGA_BRCA") added to the following email template:
    "You have been added to workspace %workspaceName%" will result in this substitution:
    "You have been added to workspace TCGA_BRCA"
   */
  private def addSubstitutions(email: SendGrid.Email, substitution: Map[String, String]): Unit = {
    substitution.foreach(sub => email.addSubstitution(wrapSubstitution(sub._1), Array(sub._2)))
  }

  private def wrapSubstitution(keyword: String): String = s"$substitutionChar$keyword$substitutionChar"

}

case class NotificationException(statusCode: StatusCode, message: String, recipients: Seq[String], notificationId: String) extends Exception {
  override def getMessage = s"Error message: [${message}], recipients: [${recipients.mkString(",")}], notificationId: [${notificationId}]"
}
