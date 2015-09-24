package thurloe.database

import slick.driver.JdbcProfile

import scala.reflect.runtime._

class DatabaseDataModels(val driver: JdbcProfile) {

  def this(driverName: String) {
    this(DatabaseDataModels.getObject[JdbcProfile](driverName))
  }

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

object DatabaseDataModels {
  // TODO: move to DSDE common util?
  private def getObject[T](objectName: String): T = {
    // via
    //   http://stackoverflow.com/questions/23466782/scala-object-get-reference-from-string-in-scala-2-10
    //   https://github.com/anvie/slick-test/blob/045f4db610d3b91bf928a53f2bc7b6ae17c35985/slick-util/src/main/scala/scala/slick/codegen/ModelGenerator.scala
    val staticModule = currentMirror.staticModule(objectName)
    val reflectModule = currentMirror.reflectModule(staticModule)
    val instance = reflectModule.instance
    instance.asInstanceOf[T]
  }
}

// Collection class useful to pass all these values around as a group
final case class DatabaseRow(id: Option[Int], userId: String, key: String, value: String, iv: String)
