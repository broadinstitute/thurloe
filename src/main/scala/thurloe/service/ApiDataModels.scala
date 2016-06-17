package thurloe.service

import spray.json.DefaultJsonProtocol
import scala.language.postfixOps

object ApiDataModelsJsonProtocol extends DefaultJsonProtocol {
  implicit val keyValuePairFormat = jsonFormat2(KeyValuePair)
  implicit val userKeyValuePairFormat = jsonFormat2(UserKeyValuePair)
  implicit val userKeyValuePairsFormat = jsonFormat2(UserKeyValuePairs)
  implicit val notificationFormat = jsonFormat3(Notification)
}

object ThurloeQuery {
  private val UserIdParam = "userId"
  private val KeyParam = "key"
  private val ValueParam = "value"
  private val UnrecognizedParams = "unrecogniZed"
  private val AllowedKeys = Seq(UserIdParam, KeyParam, ValueParam)

  private def getKeys(maybeStrings: Option[Seq[(String, String)]]): Option[Seq[String]] = {
    maybeStrings map { sequence => sequence map { case (key, value) => key } }
  }

  private def getValues(maybeStrings: Option[Seq[(String, String)]]): Option[Seq[String]] = {
    maybeStrings map { sequence => sequence map { case (key, value) => value } }
  }

  private def validKeyOrUnrecognized(maybeKey: String): String = {
    AllowedKeys find { allowedKey => allowedKey.equalsIgnoreCase(maybeKey) } getOrElse UnrecognizedParams
  }

  def apply(params: Seq[(String, String)]): ThurloeQuery = {
    val asMap = params groupBy {case (k,v) => validKeyOrUnrecognized(k) }
    ThurloeQuery(
      getValues(asMap.get(UserIdParam)),
      getValues(asMap.get(KeyParam)),
      getValues(asMap.get(ValueParam)),
      getKeys(asMap.get(UnrecognizedParams)))
  }
}

final case class ThurloeQuery(userId: Option[Seq[String]], key: Option[Seq[String]], value: Option[Seq[String]], unrecognizedFilters: Option[Seq[String]])

case class KeyValuePair(key: String, value: String)
case class KeyValuePairWithId(id: Option[Int], keyValuePair: KeyValuePair)
case class UserKeyValuePairWithId(id: Option[Int], userKeyValuePair: UserKeyValuePair)
case class UserKeyValuePair(userId: String, keyValuePair: KeyValuePair)
case class UserKeyValuePairs(userId: String, keyValuePairs: Seq[KeyValuePair])
case class Notification(contactEmail: String, notificationId: String, uniqueArguments: Map[String, String])