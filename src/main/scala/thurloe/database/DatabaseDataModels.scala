package thurloe.database

import slick.driver.JdbcProfile

import scala.reflect.runtime._

class DatabaseDataModels(val driver: JdbcProfile) {

  import driver.api._

  class DbKeyValuePair(tag: Tag) extends Table[(Option[Int], String, String, String, String)](tag, "KEY_VALUE_PAIR") {
    def id = column[Int]("KVP_ID", O.PrimaryKey, O.AutoInc)

    def userId = column[String]("USER_ID")

    def key = column[String]("KEY")

    def value = column[String]("VALUE")

    def iv = column[String]("IV")

    def idx = index("idx_a", (userId, key), unique = true)

    def * = (id.?, userId, key, value, iv)
  }
}

// Collection class useful to pass all these values around as a group
final case class DatabaseRow(id: Option[Int], userId: String, key: String, value: String, iv: String)
