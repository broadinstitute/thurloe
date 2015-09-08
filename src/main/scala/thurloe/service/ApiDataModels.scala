package thurloe.service

import spray.json.DefaultJsonProtocol

object ApiDataModelsJsonProtocol extends DefaultJsonProtocol {
  implicit val format = jsonFormat2(KeyValuePair)
}

case class KeyValuePair(key: String, value: String)