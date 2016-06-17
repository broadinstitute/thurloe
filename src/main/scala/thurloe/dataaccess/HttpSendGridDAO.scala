package thurloe.dataaccess

import com.sendgrid.SendGrid.Response
import com.sendgrid._
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future

/**
 * Created by mbemis on 6/16/16.
 */
class HttpSendGridDAO {

  val configFile = ConfigFactory.load()
  val sendGridConfig = configFile.getConfig("sendgrid")
  val apiKey = sendGridConfig.getString("apiKey")

  val templateIdReal = "7c99f04a-ac10-4e17-a46c-90465f30722f"
  val fromAddress = "accounts@dev.test.firecloud.org" //configurate the shit out of all of this

  def sendEmail(toAddress: String, templateId: String, uniqueArguments: Map[String, String] = Map.empty): Future[Response] = {
    println(s"DEBUG: [${templateId}] email sent")


    val sendGrid = new SendGrid(apiKey)
    val email = createEmail(toAddress, templateIdReal, uniqueArguments)

    val x = sendGrid.send(email) //handle failures
    Future.successful(x)
  }

  def createEmail(toAddress: String, templateId: String, uniqueArguments: Map[String, String]): SendGrid.Email = {
    val email = new SendGrid.Email()

    email.addTo(toAddress)
    email.setFrom(fromAddress)
    email.setTemplateId(templateIdReal)
    email.setSubject(" ")
    email.setText("")
    addUniqueArguments(email, uniqueArguments)
  }

  /*
    Adds a set of unique arguments to an email template.
    For example, Map("workspaceName"->"TCGA_BRCA") added to the following email template:
    "You have been added to workspace <%workspaceName%>" will result in this substitution:
    "You have been added to workspace TCGA_BRCA"
   */
  private def addUniqueArguments(email: SendGrid.Email, uniqueArguments: Map[String, String]): SendGrid.Email = {
    uniqueArguments.foreach(argument => email.addUniqueArg(argument._1, argument._2))
    email
  }

}
