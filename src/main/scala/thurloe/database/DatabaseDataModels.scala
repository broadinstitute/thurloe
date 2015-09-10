package thurloe.database

// Use H2Driver to connect to an H2 database
import slick.driver.H2Driver.api._

import scala.concurrent.ExecutionContext.Implicits.global


class DbKeyValuePair(tag: Tag) extends Table[(Int, String, String, String)](tag, "KEY_VALUE_PAIR") {
  def id = column[Int]("KVP_ID", O.PrimaryKey, O.AutoInc)
  def userId = column[String]("USER_ID")
  def key = column[String]("KEY")
  def value = column[String]("VALUE")
  def idx = index("idx_a", (userId, key), unique = true)

  def * = (id, userId, key, value)
}
