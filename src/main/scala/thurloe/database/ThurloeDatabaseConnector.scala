package thurloe.database

import com.typesafe.config.ConfigFactory
import thurloe.crypto.{EncryptedBytes, SecretKey, Aes256Cbc}
import scala.concurrent.{Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global
import thurloe.service.{UserKeyValuePairs, UserKeyValuePair, KeyValuePair}

import scala.concurrent.duration.Duration
import org.apache.commons.codec.binary.{Base64, Hex}

import scala.util.{Failure, Success, Try}

case object ThurloeDatabaseConnector extends DataAccess {

  val configFile = ConfigFactory.load()

  // DB Config:
  val dbConfig = configFile.getConfig("database")
  val databaseInstanceConfig = dbConfig.getConfig(dbConfig.getString("config"))
  val dataModels = new DatabaseDataModels(databaseInstanceConfig.getString("slick.driver"))

  // Crypto Config:
  val cryptoConfig = configFile.getConfig("crypto")
  val secretKey = SecretKey(cryptoConfig.getString("key"))

  import dataModels._
  import dataModels.driver.api._

  val database = Database.forConfig("", databaseInstanceConfig)

  if (databaseInstanceConfig.hasPath("slick.createSchema") && databaseInstanceConfig.getBoolean("slick.createSchema"))
    setupInMemoryDatabase(database)

  private def databaseValuesToKeyValuePair(key: String, value: String, iv: String): Try[KeyValuePair] = {
    Aes256Cbc.decrypt(EncryptedBytes(value, iv), secretKey) map { decryptedBytes =>
      KeyValuePair(key, new String(decryptedBytes, "UTF-8"))
    }
  }

  private def interpretDatabaseResponse(resultSequence: Seq[(Option[Int], String, String, String, String)]): Seq[Future[KeyValuePair]] = {
    resultSequence map {
      case (id, userId, key, value, iv) => Future.fromTry(databaseValuesToKeyValuePair(key, value, iv))
    }
  }

  private def lookupWithConstraint(constraint: DbKeyValuePair => Rep[Boolean]): Future[Seq[KeyValuePair]] = {
    val keyValuePairs = TableQuery[DbKeyValuePair]
    val query = keyValuePairs.filter(constraint)

    for {
      responseSequence <- database.run(query.result.transactionally)
      result <- Future.sequence(interpretDatabaseResponse(responseSequence))
    } yield result
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

  /**
   * Writes the user key value pair to the database, with the exception of the 'value' which uses
   * the encrypted version instead. The IV also comes from the encryption result.
   */
  private def databaseWrite(userKeyValuePair: UserKeyValuePair, encryptedValue: EncryptedBytes): Future[Unit] = {
    val keyValuePairs = TableQuery[DbKeyValuePair]

    val action = keyValuePairs.insertOrUpdate(
      None,
      userKeyValuePair.userId,
      userKeyValuePair.keyValuePair.key,
      encryptedValue.base64CipherText,
      encryptedValue.base64Iv
    )

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

  def setKeyValuePair(userKeyValuePair: UserKeyValuePair): Future[Unit] = {
    Aes256Cbc.encrypt(userKeyValuePair.keyValuePair.value.getBytes("UTF-8"), secretKey) match {
      case Success(encryptedValue) => databaseWrite(userKeyValuePair, encryptedValue)
      case Failure(x) => Future.fromTry(Failure[Unit](x))
    }
  }

  def deleteKeyValuePair(userId: String, key: String): Future[Unit] = {
    val keyValuePairs = TableQuery[DbKeyValuePair]
    val action = keyValuePairs.filter(x => x.key === key && x.userId === userId).delete
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
    val setup = DBIO.seq(keyValuePairs.schema.create)
    Await.result(database.run(setup), Duration.Inf)
  }
}
