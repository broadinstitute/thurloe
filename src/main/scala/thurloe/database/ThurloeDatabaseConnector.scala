package thurloe.database

import com.typesafe.config.ConfigFactory
import scala.concurrent.{Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global
import thurloe.service.{UserKeyValuePairs, UserKeyValuePair, KeyValuePair}

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

case object ThurloeDatabaseConnector extends DataAccess {

  val config = ConfigFactory.load().getConfig("database")
  val databaseConfig = config.getConfig(config.getString("config"))

  val dataModels = new DatabaseDataModels(databaseConfig.getString("slick.driver"))

  import dataModels._
  import dataModels.driver.api._

  val database = Database.forConfig("", databaseConfig)

  if (databaseConfig.hasPath("slick.createSchema") && databaseConfig.getBoolean("slick.createSchema"))
    setupInMemoryDatabase(database)

  private def lookupWithConstraint(constraint: DbKeyValuePair => Rep[Boolean]): Future[Seq[KeyValuePair]] = {
    val keyValuePairs = TableQuery[DbKeyValuePair]
    val q = keyValuePairs.filter(constraint)

    val futureResults = database.run(q.result.transactionally) map {
      _ map {
      case (id, userId, key, value) =>
        println("  " + id + "\t" + userId + "\t" + key + "\t" + value + "\t")
        KeyValuePair(key, value)
    }}

    futureResults
  }

  def keyLookup(userId: String, key: String): Future[KeyValuePair] = {
    for {
      results <- lookupWithConstraint(x => x.key === key && x.userId === userId)
      result <- if (results.isEmpty) {
        Future.failed(new KeyNotFoundException(userId, key))
      } else if (results.size == 1) {
        Future.successful(results.head)
      } else {
        Future.failed(InvalidDatabaseStateException(s"Too many results: ${results.size}"))
      }
    } yield result
  }

  def collectAll(userId: String): Future[UserKeyValuePairs] = {
    for {
      results <- lookupWithConstraint(x => x.userId === userId)
    } yield UserKeyValuePairs(userId, results)
  }

  def setKeyValuePair(userKeyValuePair: UserKeyValuePair): Future[Unit] = {

    val keyValuePairs = TableQuery[DbKeyValuePair]

    val action = keyValuePairs.insertOrUpdate(
      None,
      userKeyValuePair.userId,
      userKeyValuePair.keyValuePair.key,
      userKeyValuePair.keyValuePair.value)

    for {
      affectedRowsCount <- database.run(action.transactionally)
      _ <- if (affectedRowsCount == 1) {
        Future.successful(())
      } else {
        Future.failed(InvalidDatabaseStateException(
          s"Modified $affectedRowsCount rows in database (expected to modify 1)"))
      }
    } yield ()
  }

  def deleteKeyValuePair(userId: String, key: String): Future[Unit] = {
    val keyValuePairs = TableQuery[DbKeyValuePair]
    val q = keyValuePairs.filter(x => x.key === key && x.userId === userId)
    val action = q.delete
    val affectedRowsCountFuture: Future[Int] = database.run(action.transactionally)

    for {
      affectedRowCount <- affectedRowsCountFuture
      _ <- if (affectedRowCount > 0) {
        Future.successful(())
      } else {
        Future.failed(KeyNotFoundException(userId, key))
      }
    } yield ()
  }

  def setupInMemoryDatabase(database: Database): Unit = {
    val keyValuePairs = TableQuery[DbKeyValuePair]
    val setup = DBIO.seq(
      keyValuePairs.schema.create
    )
    Await.result(database.run(setup), Duration.Inf)
  }
}
