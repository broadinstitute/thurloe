package thurloe.dataaccess

import com.sendgrid.SendGrid.Response
import com.sendgrid._
import com.typesafe.config.ConfigFactory
import org.json.JSONObject
import thurloe.database.ThurloeDatabaseConnector
import thurloe.service.Notification

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by mbemis on 6/16/16.
 */
class HttpSendGridDAO {

  val configFile = ConfigFactory.load()
  val sendGridConfig = configFile.getConfig("sendgrid")
  val apiKey = sendGridConfig.getString("apiKey")
  val substitutionChar = sendGridConfig.getString("substitutionChar")
  val fromAddress = sendGridConfig.getString("defaultFromAddress")

  val dataAccess = ThurloeDatabaseConnector

  def sendNotification(notification: Notification): Future[Response] = {
    lookupPreferredEmail(notification.userId) flatMap { preferredEmail =>
      val email = createEmail(preferredEmail, notification.notificationId, notification.substitutions)
      sendEmail(email)
    }
  }

  /*
    Note: email.setSubject and email.setText must be set even if their values
    aren't used. Supposedly this will be fixed in a future version of SendGrid
   */
  def createEmail(toAddress: String, notificationId: String, substitutions: Map[String, String] = Map.empty): SendGrid.Email = {
    val email = new SendGrid.Email()

    email.addTo(toAddress)
    email.setFrom(fromAddress)
    email.setTemplateId(notificationId)
    email.setSubject(" ")
    email.setText(" ")
    addSubstitutions(email, substitutions)
    email
  }

  def sendEmail(email: SendGrid.Email): Future[Response] = {
    val sendGrid = new SendGrid(apiKey)

    Future {
      val response = sendGrid.send(email)
      if(response.getStatus) response
      else throw new NotificationException(email.getTos, email.getFilters.getJSONObject("templates").getJSONObject("settings").get("template_id").toString)
    }
  }

  def lookupPreferredEmail(userId: String): Future[String] = {
    for {
      contactEmail <- dataAccess.lookup(userId, "contactEmail")
      email <- dataAccess.lookup(userId, "email")
    } yield if(contactEmail.keyValuePair.value.isEmpty) email.keyValuePair.value else contactEmail.keyValuePair.value
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

  case class NotificationException(recipients: Array[String], notificationId: String) extends Exception {
    override def getMessage = s"Unable to send notification [$notificationId] to recipients [${recipients.mkString(",")}]"
  }

}
