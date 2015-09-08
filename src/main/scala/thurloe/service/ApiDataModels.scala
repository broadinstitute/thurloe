package thurloe.service

import spray.json.DefaultJsonProtocol

object ApiDataModelsJsonProtocol extends DefaultJsonProtocol {
  implicit val keyValuePairFormat = jsonFormat2(KeyValuePair)
  implicit val userKeyValuePairFormat = jsonFormat2(UserKeyValuePair)
  implicit val userKeyValuePairsFormat = jsonFormat2(UserKeyValuePairs)
}

case class KeyValuePair(key: String, value: String)
case class UserKeyValuePair(userId: String, keyValuePair: KeyValuePair)
case class UserKeyValuePairs(userId: String, keyValuePairs: Seq[KeyValuePair])