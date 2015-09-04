package thurloe.service

import com.wordnik.swagger.annotations.{ApiModel, ApiModelProperty}
import spray.json.DefaultJsonProtocol

import scala.annotation.meta.field

object ApiDataModelsJsonProtocol extends DefaultJsonProtocol {
  implicit val format = jsonFormat2(KeyValuePair)
}

@ApiModel(value = "Key Value Pair")
case class KeyValuePair
(
  @(ApiModelProperty@field)(required = true, value = "The key")
  key: String,
  @(ApiModelProperty@field)(required = true, value = "The value")
  value: String
)