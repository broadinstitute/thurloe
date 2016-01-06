package thurloe.database

import com.typesafe.config.ConfigFactory
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import thurloe.crypto.{EncryptedBytes, SecretKey, Aes256Cbc}
import scala.concurrent.{Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global
import thurloe.service._

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

case object ThurloeDatabaseConnector extends DataAccess {

  val configFile = ConfigFactory.load()

  // DB Config:
  val dbConfigName =
    if (configFile.hasPath("databaseSlickDriverConfigSwitch")) "databaseSlickDriverConfigSwitch" else "database"
  val dbConfig = configFile.getConfig(dbConfigName)
  val databaseInstanceConfig = dbConfig.getConfig(dbConfig.getString("config"))
  val slickConfig = DatabaseConfig.forConfig[JdbcProfile]("", databaseInstanceConfig)
  val dataModels = new DatabaseDataModels(slickConfig.driver)

  // Crypto Config:
  val cryptoConfig = configFile.getConfig("crypto")
  val secretKey = SecretKey(cryptoConfig.getString("key"))

  import dataModels._
  import dataModels.driver.api._

  val database = slickConfig.db
  val keyValuePairTable = TableQuery[DbKeyValuePair]

  if (databaseInstanceConfig.hasPath("slick.createSchema") && databaseInstanceConfig.getBoolean("slick.createSchema"))
    setupInMemoryDatabase(database)

  private def databaseValuesToUserKeyValuePair(id: Option[Int], userId: String, key: String, value: String, iv: String): Try[UserKeyValuePairWithId] = {
    Aes256Cbc.decrypt(EncryptedBytes(value, iv), secretKey) map { decryptedBytes =>
      UserKeyValuePairWithId(id, UserKeyValuePair(userId, KeyValuePair(key, new String(decryptedBytes, "UTF-8"))))
    }
  }

  private def interpretDatabaseResponse(resultSequence: Seq[DatabaseRow]): Seq[Future[UserKeyValuePairWithId]] = {
    resultSequence map {
      case DatabaseRow(id, userId, key, value, iv) => Future.fromTry(databaseValuesToUserKeyValuePair(id, userId, key, value, iv))
    }
  }

  private def lookupWithConstraint(constraint: DbKeyValuePair => Rep[Boolean]): Future[Seq[UserKeyValuePairWithId]] = {
    val query = keyValuePairTable.filter(constraint)

    for {
      responseSequence <- database.run(query.result.transactionally)
      result <- Future.sequence(interpretDatabaseResponse(responseSequence map { case (id, userId, key, value, iv) => DatabaseRow(id,userId,key,value,iv) }))
    } yield result
  }

  def lookupIncludingDatabaseId(userId: String, key: String): Future[UserKeyValuePairWithId] = {
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

  def lookup(userId: String, key: String): Future[UserKeyValuePair] = lookupIncludingDatabaseId(userId, key) map { _.userKeyValuePair }

  def lookup(userId: String): Future[UserKeyValuePairs] = {
    for {
      results <- lookupWithConstraint(x => x.userId === userId)
    } yield UserKeyValuePairs(userId, results map { _.userKeyValuePair.keyValuePair })
  }

  def lookup(queryParameters: ThurloeQuery): Future[Seq[UserKeyValuePair]] = {
    def userIdAndKeyConstraint(queryParameters: ThurloeQuery) = (x: DbKeyValuePair) => {
      val include: Rep[Boolean] = true

      val userIdFilter = queryParameters.userId.map { userIds =>
        val userIdFilters = userIds map { userId => x.userId === userId }
        userIdFilters.reduceLeft(_ || _)
      }
      val keyFilter = queryParameters.key.map { keys =>
        val keyFilters = keys map { key => x.key === key }
        keyFilters.reduceLeft(_ || _)
      }

      val optionalFilters = List(userIdFilter, keyFilter)
      val filters = optionalFilters.map(_.getOrElse(include))
      filters.reduceLeftOption(_ && _).getOrElse(include)
    }

    for {
      filteredOnUserAndKey <- lookupWithConstraint(userIdAndKeyConstraint(queryParameters))
      // We have to filter out values outside of the Slick access because the values are encrypted until now.
      valueFilter = (userKeyValuePair: UserKeyValuePairWithId) => queryParameters.value map { values =>
        val valueFilters = values map { value => value.equals(userKeyValuePair.userKeyValuePair.keyValuePair.value) }
        valueFilters.reduceLeft(_ || _)
      } getOrElse true
      results = filteredOnUserAndKey filter valueFilter

    } yield results map { _.userKeyValuePair }
  }

  import thurloe.database.DatabaseOperation.DatabaseOperation
  /**
   * Writes the user key value pair to the database, with the exception of the 'value' which uses
   * the encrypted version instead. The IV also comes from the encryption result.
   *
   * @return The type of operation which was carried out (as a Future)
   */
  private def databaseWrite(userKeyValuePair: UserKeyValuePair, encryptedValue: EncryptedBytes): Future[DatabaseOperation] = {

    val lookupExists = lookupIncludingDatabaseId(userKeyValuePair.userId, userKeyValuePair.keyValuePair.key)
    lookupExists flatMap { existingKvp => update(existingKvp, userKeyValuePair, encryptedValue) } recoverWith {
      case e: KeyNotFoundException => insert(userKeyValuePair, encryptedValue)
      case e => Future.failed(e)
    }
  }

  private def insert(userKeyValuePair: UserKeyValuePair, encryptedValue: EncryptedBytes): Future[DatabaseOperation] = {
    val action =
      keyValuePairTable += (
        None,
        userKeyValuePair.userId,
        userKeyValuePair.keyValuePair.key,
        encryptedValue.base64CipherText,
        encryptedValue.base64Iv
        )
    for {
      affectedRowsCount <- database.run(action.transactionally)
      x <- handleDatabaseWriteResponse(affectedRowsCount, DatabaseOperation.Insert)
    } yield x
  }

  private def update(oldKeyValuePair: UserKeyValuePairWithId, userKeyValuePair: UserKeyValuePair, newEncryptedValue: EncryptedBytes): Future[DatabaseOperation] = {
    // We've just looked up and found an entry, so this ID should never be None. However, belt and braces...
    oldKeyValuePair.id match {
      case None => Future.failed(new KeyNotFoundException(userKeyValuePair.userId, userKeyValuePair.keyValuePair.key))
      case Some(rowId) =>
        // NB: Using sqlu"..." strings does clever DB magic to turn this into a proper parameterised DB command to avoid insertion attacks.
        def sqlUpdateCommand: DBIO[Int] = sqlu"UPDATE KEY_VALUE_PAIR SET VALUE=${newEncryptedValue.base64CipherText}, IV=${newEncryptedValue.base64Iv} WHERE KVP_ID=$rowId"

        for {
          affectedRowsCount <- database.run(sqlUpdateCommand.transactionally)
          x <- handleDatabaseWriteResponse(affectedRowsCount, DatabaseOperation.Update)
        } yield x
    }
  }

  private def handleDatabaseWriteResponse(affectedRowsCount: Int, op: DatabaseOperation): Future[DatabaseOperation] = {
    if (affectedRowsCount == 1) {
      Future.successful(op)
    } else {
      Future.failed(InvalidDatabaseStateException(
        s"Modified $affectedRowsCount rows in database (expected to modify 1)"))
    }
  }

  def set(userKeyValuePair: UserKeyValuePair): Future[DatabaseOperation] = {
    Aes256Cbc.encrypt(userKeyValuePair.keyValuePair.value.getBytes("UTF-8"), secretKey) match {
      case Success(encryptedValue) => databaseWrite(userKeyValuePair, encryptedValue)
      case Failure(x) => Future.failed(x)
    }
  }

  def delete(userId: String, key: String): Future[Unit] = {
    val action = keyValuePairTable.filter(x => x.key === key && x.userId === userId).delete
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
    val setup = DBIO.seq(keyValuePairTable.schema.create)
    Await.result(database.run(setup), Duration.Inf)
  }
}

object DatabaseOperation extends Enumeration {
  type DatabaseOperation = Value
  val Insert, Update = Value
}
