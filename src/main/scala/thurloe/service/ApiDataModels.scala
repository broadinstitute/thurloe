package thurloe.service

import org.broadinstitute.dsde.workbench.model.{WorkbenchEmail, WorkbenchUserId}
import org.broadinstitute.dsde.workbench.model.WorkbenchIdentityJsonSupport.{
  WorkbenchEmailFormat,
  WorkbenchUserIdFormat
}
import spray.json.DefaultJsonProtocol

object ApiDataModelsJsonProtocol extends DefaultJsonProtocol {
  implicit val keyValuePairFormat = jsonFormat2(KeyValuePair)
  implicit val userKeyValuePairFormat = jsonFormat2(UserKeyValuePair)
  implicit val userKeyValuePairsFormat = jsonFormat2(UserKeyValuePairs)
  implicit val notificationFormat = jsonFormat7(Notification)
}

object ThurloeQuery {
  private val UserIdParam = "userId"
  private val KeyParam = "key"
  private val ValueParam = "value"
  private val UnrecognizedParams = "unrecogniZed"
  private val AllowedKeys = Seq(UserIdParam, KeyParam, ValueParam)

  private def getKeys(maybeStrings: Option[Seq[(String, String)]]): Option[Seq[String]] =
    maybeStrings map { sequence => sequence map { case (key, _) => key } }

  private def getValues(maybeStrings: Option[Seq[(String, String)]]): Option[Seq[String]] =
    maybeStrings map { sequence => sequence map { case (_, value) => value } }

  private def validKeyOrUnrecognized(maybeKey: String): String =
    AllowedKeys find { allowedKey =>
      allowedKey.equalsIgnoreCase(maybeKey)
    } getOrElse UnrecognizedParams

  def apply(params: Seq[(String, String)]): ThurloeQuery = {
    val asMap = params groupBy { case (k, _) => validKeyOrUnrecognized(k) }
    ThurloeQuery(getValues(asMap.get(UserIdParam)),
                 getValues(asMap.get(KeyParam)),
                 getValues(asMap.get(ValueParam)),
                 getKeys(asMap.get(UnrecognizedParams)))
  }
}

final case class ThurloeQuery(userId: Option[Seq[String]],
                              key: Option[Seq[String]],
                              value: Option[Seq[String]],
                              unrecognizedFilters: Option[Seq[String]]) {
  def isEmpty: Boolean =
    userId.isEmpty && key.isEmpty && value.isEmpty
}

case class KeyValuePair(key: String, value: String)
case class KeyValuePairWithId(id: Option[Int], keyValuePair: KeyValuePair)
case class UserKeyValuePairWithId(id: Option[Int], userKeyValuePair: UserKeyValuePair)
case class UserKeyValuePair(userId: String, keyValuePair: KeyValuePair)
case class UserKeyValuePairs(userId: String, keyValuePairs: Seq[KeyValuePair]) {
  def toKeyValueSeq: Seq[UserKeyValuePair] = keyValuePairs.map(UserKeyValuePair(userId, _))
}
case class Notification(userId: Option[WorkbenchUserId],
                        userEmail: Option[WorkbenchEmail],
                        replyTos: Option[Set[WorkbenchUserId]],
                        notificationId: String,
                        substitutions: Map[String, String],
                        emailLookupSubstitutions: Map[String, WorkbenchUserId],
                        nameLookupSubstitution: Map[String, WorkbenchUserId])
