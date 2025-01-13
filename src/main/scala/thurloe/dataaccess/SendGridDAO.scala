package thurloe.dataaccess

import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import com.sendgrid.Response
import com.sendgrid.helpers.mail.Mail
import com.sendgrid.helpers.mail.objects.{Email, Personalization}
import com.typesafe.config.ConfigFactory
import org.broadinstitute.dsde.workbench.model.{WorkbenchEmail, WorkbenchUserId}
import thurloe.service.Notification

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._

import scala.util.Try

trait SendGridDAO {

  val configFile = ConfigFactory.load()
  val sendGridConfig = configFile.getConfig("sendgrid")
  val apiKey = sendGridConfig.getString("apiKey")
  val substitutionChar = sendGridConfig.getString("substitutionChar")
  val defaultFromAddress =
    new Email(sendGridConfig.getString("defaultFromAddress"), sendGridConfig.getString("defaultFromName"))

  def sendMail(mail: Mail): Future[Response]
  def lookupPreferredEmail(userId: WorkbenchUserId): Future[WorkbenchEmail]
  def lookupUserName(userId: WorkbenchUserId): Future[String]
  def lookupUserFirstName(userId: WorkbenchUserId): Future[String]

  def sendNotifications(notifications: List[Notification]): Future[List[Response]] =
    Future.sequence(notifications.map { notification =>
      val toAddressFuture = notification.userEmail
        .map(Future.successful)
        .getOrElse(
          notification.userId
            .map(lookupPreferredEmail)
            .getOrElse(
              Future.failed(
                new NotificationException(StatusCodes.BadRequest,
                                          "No recipient specified",
                                          Seq.empty,
                                          notification.notificationId
                )
              )
            )
        )

      val replyTosFuture = notification.replyTos.map {
        Future.traverse(_)(lookupPreferredEmail).map(replyToEmails => Option(replyToEmails))
      } getOrElse Future.successful(None)

      val emailSubstitutionsFuture = Future.traverse(notification.emailLookupSubstitutions.toList) { case (key, id) =>
        lookupPreferredEmail(id).map(email => key -> email.value)
      }

      val nameSubstitutionsFuture = Future.traverse(notification.nameLookupSubstitution.toList) { case (key, id) =>
        lookupUserName(id).map(name => key -> name)
      }

      val recipientFirstNameSubstitutionFuture = notification.userId match {
        case Some(userId) => lookupUserFirstName(userId).map(firstName => Map("recipientFirstName" -> firstName))
        case None         => Future.successful(Map.empty)
      }

      for {
        toAddress <- toAddressFuture
        replyTos <- replyTosFuture
        emailSubstitutions <- emailSubstitutionsFuture
        nameSubstitution <- nameSubstitutionsFuture
        recipientFirstNameSubstitution <- recipientFirstNameSubstitutionFuture
        response <- sendMail(
          createMail(
            toAddress,
            replyTos,
            notification.notificationId,
            notification.substitutions ++ emailSubstitutions ++ nameSubstitution ++ recipientFirstNameSubstitution
          )
        )
      } yield response
    })

  /*
    Note: email.setSubject and email.setText must be set even if their values
    aren't used. Supposedly this will be fixed in a future version of SendGrid
   */
  def createMail(toAddress: WorkbenchEmail,
                 replyTos: Option[Set[WorkbenchEmail]],
                 notificationId: String,
                 substitutions: Map[String, String] = Map.empty
  ): Mail = {
    val mail = new Mail()

    // set recipient
    val personalization = new Personalization()
    personalization.addTo(new Email(toAddress.value))
    addSubstitutions(personalization, substitutions)

    mail.setFrom(defaultFromAddress)
    mail.setTemplateId(notificationId)
    mail.setSubject(" ")

    mail.addPersonalization(personalization)

    replyTos.foreach { userEmails =>
      val addrs = userEmails.map(_.value)
      mail.addHeader("Reply-To", addrs.mkString(", "))
    }

    mail
  }

  def isSuccessful(response: Response): Boolean =
    Try(StatusCode.int2StatusCode(response.getStatusCode).isSuccess()).getOrElse(false)

  def getTos(mail: Mail): Seq[String] = mail.getPersonalization.asScala.flatMap(_.getTos.asScala.map(_.getEmail)).toSeq

  /*
    Adds a set of substitutions to an email template.
    For example, Map("workspaceName"->"TCGA_BRCA") added to the following email template:
    "You have been added to workspace %workspaceName%" will result in this substitution:
    "You have been added to workspace TCGA_BRCA"
   */
  private def addSubstitutions(personalization: Personalization, substitution: Map[String, String]): Unit =
    substitution.foreach(sub => personalization.addSubstitution(wrapSubstitution(sub._1), sub._2))

  private def wrapSubstitution(keyword: String): String = s"$substitutionChar$keyword$substitutionChar"

}

case class NotificationException(statusCode: StatusCode,
                                 message: String,
                                 recipients: Seq[String],
                                 notificationId: String
) extends Exception {
  override def getMessage =
    s"Error message: [${message}], recipients: [${recipients.mkString(",")}], notificationId: [${notificationId}]"
}
