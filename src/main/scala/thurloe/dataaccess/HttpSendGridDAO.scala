package thurloe.dataaccess

import com.sendgrid._
import com.typesafe.config.ConfigFactory

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

  def sendEmail(email: SendGrid.Email): Future[Boolean] = {
    Future {
      println(s"DEBUG: email sent")
      val sendGrid = new SendGrid(apiKey)

      val response = sendGrid.send(email)
      if (response.getStatus) true else false //probably have a better return value than this
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
  }

  /*
    Adds a set of substitutions to an email template.
    For example, Map("workspaceName"->"TCGA_BRCA") added to the following email template:
    "You have been added to workspace %workspaceName%" will result in this substitution:
    "You have been added to workspace TCGA_BRCA"
   */
  private def addSubstitutions(email: SendGrid.Email, uniqueArguments: Map[String, String]): SendGrid.Email = {
    uniqueArguments.foreach(argument => email.addSubstitution(wrapSubstitution(argument._1), Array(argument._2)))
    email
  }

  private def wrapSubstitution(keyword: String): String = s"$substitutionChar$keyword$substitutionChar"

}
