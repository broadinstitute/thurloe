package thurloe.database

import slick.driver.H2Driver.api._
import scala.concurrent.{Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global
import thurloe.service.{UserKeyValuePairs, UserKeyValuePair, KeyValuePair}

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

case object ThurloeDatabaseConnector extends DataAccess {

  val database = Database.forConfig("h2mem1")
  setupInMemoryDatabase(database)

  private def lookupWithConstraint(constraint: DbKeyValuePair => Rep[Boolean]): Seq[KeyValuePair] = {
    val keyValuePairs = TableQuery[DbKeyValuePair]
    val q = keyValuePairs.filter(constraint)

    val futureResults = database.run(q.result).map {_.map {
      case (id, userId, key, value) =>
        println("  " + id + "\t" + userId + "\t" + key + "\t" + value + "\t")
        KeyValuePair(key, value)
    }}

    // TODO: This is horrible. Don't let this get past a PR if I forget to refactor!
    Await.result(futureResults, Duration.fromNanos(500 * 1000 * 1000))
  }

  def keyLookup(userId: String, key: String): Try[KeyValuePair] = {
    val results = lookupWithConstraint(x => (x.key === key && x.userId === userId))

    if(results.size == 0) Failure(new KeyNotFoundException(userId, key))
    else if (results.size == 1) Success (results.head)
    else Failure(InvalidDatabaseStateException(s"Too many results: ${results.size}"))
  }

  def collectAll(userId: String): Try[UserKeyValuePairs] = {
    val results = lookupWithConstraint(x => x.userId === userId)

    Success(UserKeyValuePairs(userId, results))
  }

  def setKeyValuePair(userKeyValuePair: UserKeyValuePair): Try[Unit] = {

    val keyValuePairs = TableQuery[DbKeyValuePair]

    val action = keyValuePairs.insertOrUpdate(
      0, // TODO: This seems to be ignored because AUTO_INC. Can that be made obvious ("None" doesn't compile)?
      userKeyValuePair.userId,
      userKeyValuePair.keyValuePair.key,
      userKeyValuePair.keyValuePair.value)

    val affectedRowsCountFuture: Future[Int] = database.run(action)
    // TODO: This is horrible. Don't let this get past a PR if I forget to refactor!

    val affectedRowCount = Try(Await.result(affectedRowsCountFuture, Duration.fromNanos(500 * 1000 * 1000)))

    affectedRowCount match {
      case Success(x) =>
        if (x==1) Success(())
        else Failure(InvalidDatabaseStateException(s"Modified $x rows in database (expected to modify 1)"))
      case Failure(e) => Failure(e)
    }
  }

  def deleteKeyValuePair(userId: String, key: String): Try[Unit] = {
    val keyValuePairs = TableQuery[DbKeyValuePair]
    val q = keyValuePairs.filter(x => x.key === key && x.userId === userId)
    val action = q.delete
    val affectedRowsCountFuture: Future[Int] = database.run(action)

    // TODO: This is horrible. Don't let this get past a PR if I forget to refactor!
    val affectedRowCount: Int = Await.result(affectedRowsCountFuture, Duration.fromNanos(500 * 1000 * 1000))

    if (affectedRowCount > 0) Success()
    else Failure(KeyNotFoundException(userId, key))
  }


  def setupInMemoryDatabase(database: Database) = {
    val keyValuePairs = TableQuery[DbKeyValuePair]
    val setup = DBIO.seq(
      (keyValuePairs.schema).create
    )
    database.run(setup)
  }
}
