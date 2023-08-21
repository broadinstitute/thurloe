package thurloe.database

import com.google.common.base.Throwables
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.{ClassLoaderResourceAccessor, ResourceAccessor}
import liquibase.{Contexts, Liquibase}
import org.broadinstitute.dsde.workbench.client.sam
import org.broadinstitute.dsde.workbench.client.sam.ApiException
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import sun.security.provider.certpath.SunCertPathBuilderException
import thurloe.crypto.{Aes256Cbc, EncryptedBytes, SecretKey}
import thurloe.dataaccess.SamDAO
import thurloe.service._

import java.sql.SQLTimeoutException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

case object ThurloeDatabaseConnector extends DataAccess with LazyLogging {

  val configFile = ConfigFactory.load()

  // DB Config:
  val dbConfigName =
    if (configFile.hasPath("databaseSlickDriverConfigSwitch")) "databaseSlickDriverConfigSwitch" else "database"
  val dbConfig = configFile.getConfig(dbConfigName)
  val databaseInstanceConfig = dbConfig.getConfig(dbConfig.getString("config"))
  val slickConfig = DatabaseConfig.forConfig[JdbcProfile]("", databaseInstanceConfig)
  val dataModels = new DatabaseDataModels(slickConfig.profile)

  // Crypto Config:
  val cryptoConfig = configFile.getConfig("crypto")
  val secretKey = SecretKey(cryptoConfig.getString("key"))

  import dataModels._
  import dataModels.driver.api._

  val database = slickConfig.db
  val keyValuePairTable = TableQuery[DbKeyValuePair]

  initWithLiquibase()

  private def databaseValuesToUserKeyValuePair(id: Option[Int],
                                               userId: String,
                                               key: String,
                                               value: String,
                                               iv: String): Try[UserKeyValuePairWithId] =
    Aes256Cbc.decrypt(EncryptedBytes(value, iv), secretKey) map { decryptedBytes =>
      UserKeyValuePairWithId(id, UserKeyValuePair(userId, KeyValuePair(key, new String(decryptedBytes, "UTF-8"))))
    }

  private def interpretDatabaseResponse(resultSequence: Seq[DatabaseRow]): Seq[Future[UserKeyValuePairWithId]] =
    resultSequence map {
      case DatabaseRow(id, userId, key, value, iv) =>
        Future.fromTry(databaseValuesToUserKeyValuePair(id, userId, key, value, iv))
    }

  /**
   * Consumers of thurloe are not consistent in what type of userId they send. Sometimes it is the user's
   * googleSubjectId and sometimes it is the user's azureB2cId. This method will look up the user in sam to get
   * all of their ids in order to properly do the lookup and support azure b2c users.
   *
   * NOTE: Once all consumers of thurloe are using sam id to store and lookup user data this method can be removed.
   *
   * @param userId We dont know what type of user id this is, could be gsid, b2cid, or sam id
   * @param samDao
   * @return
   */
  private def lookupSamUser(userId: String, samDao: SamDAO): Future[sam.model.User] = {
    val results = samDao.getUserById(userId)

    val samUser = if (results.isEmpty) {
      Future.failed(new KeyNotFoundException(userId, "n/a"))
    } else if (results.size == 1) {
      // If we get exactly one result we have found the user we want.
      Future.successful(results.head)
    } else {
      // If we get back multiple results, we assume the record with the b2c id is the most recent and return that one.
      results
      // The user record is a java obj so we need to handle nulls properly
        .find(samUserRecord => Option(samUserRecord.getAzureB2CId).isDefined)
        .map(Future.successful)
        .getOrElse(
          Future.failed(
            InvalidDatabaseStateException(
              s"Too many results returned from sam, none of which have an AzureB2cId: ${results.size}." +
                s"\nResults: ${results
                  .map(samRecord => s"GoogleSubjectId: ${samRecord.getGoogleSubjectId}, AzureB2cId: ${samRecord.getAzureB2CId}, SamId: ${samRecord.getId}")}" +
                s"\nQuery: $userId"
            )
          )
        )
    }
    samUser.recoverWith({
      case e: ApiException =>
        logger.error(s"Api error while looking up user $userId in sam", e)
        val dummySamUser = new sam.model.User()
        dummySamUser.setGoogleSubjectId(userId)
        dummySamUser.setAzureB2CId(userId)
        dummySamUser.setId(userId)

        Future.successful(dummySamUser)
    })
  }

  private def lookupWithConstraint(constraint: DbKeyValuePair => Rep[Boolean]): Future[Seq[UserKeyValuePairWithId]] = {
    val query = keyValuePairTable.filter(constraint)

    for {
      responseSequence <- database.run(query.result.transactionally)
      result <- Future.sequence(interpretDatabaseResponse(responseSequence map {
        case (id, userId, key, value, iv) => DatabaseRow(id, userId, key, value, iv)
      }))
    } yield result
  }

  /*
   * For historical reasons, we don't know which 'type' of userId was used when creating any given record in Thurloe.
   * It could be the Sam UserID, GoogleSubject ID, or the Azure B2C ID, so we need to try find records with all three for now.
   * In the future, we are going to migrate/normalize Thurloe's DB to only use Sam User ID
   */
  def lookupIncludingDatabaseId(userId: String, key: String, samDAO: SamDAO): Future[UserKeyValuePairWithId] =
    for {
      samUser <- lookupSamUser(userId, samDAO)
      results <- lookupWithConstraint(thurloeRecord =>
        thurloeRecord.key === key && (thurloeRecord.userId === samUser.getId || thurloeRecord.userId === samUser.getGoogleSubjectId || thurloeRecord.userId === samUser.getAzureB2CId)
      )
      result <- if (results.isEmpty) {
        Future.failed(KeyNotFoundException(userId, key))
      } else if (results.size == 1) {
        Future.successful(results.head)
      } else {
        Future.failed(
          InvalidDatabaseStateException(
            s"Too many results returned from Thurloe's DB (${results.size}) for userId: $userId and key: $key" +
              s"\nResults: ${results.map(thurloeRecord => s"KeyValuePair: ${thurloeRecord.userKeyValuePair}")}"
          )
        )
      }
    } yield result.copy(userKeyValuePair = result.userKeyValuePair.copy(userId = userId))

  def lookup(samDao: SamDAO, userId: String, key: String): Future[UserKeyValuePair] =
    lookupIncludingDatabaseId(userId, key, samDao) map {
      _.userKeyValuePair
    }

  /*
   * For historical reasons, we don't know which 'type' of userId was used when creating any given record in Thurloe.
   * It could be the Sam UserID, GoogleSubject ID, or the Azure B2C ID, so we need to try find records with all three for now.
   * In the future, we are going to migrate/normalize Thurloe's DB to only use Sam User ID
   */
  def lookup(samDao: SamDAO, userId: String): Future[UserKeyValuePairs] =
    for {
      samUser <- lookupSamUser(userId, samDao)
      results <- lookupWithConstraint(thurloeRecord =>
        thurloeRecord.userId === samUser.getId || thurloeRecord.userId === samUser.getGoogleSubjectId || thurloeRecord.userId === samUser.getAzureB2CId
      )
    } yield UserKeyValuePairs(userId, results map { _.userKeyValuePair.keyValuePair })

  /*
   * For historical reasons, we don't know which 'type' of userId was used when creating any given record in Thurloe.
   * It could be the Sam UserID, GoogleSubject ID, or the Azure B2C ID, so we need to try find records with all three for now.
   * In the future, we are going to migrate/normalize Thurloe's DB to only use Sam User ID
   */
  def lookup(samDao: SamDAO, queryParameters: ThurloeQuery): Future[Seq[UserKeyValuePair]] = {
    def userIdAndKeyConstraint(queryParameters: ThurloeQuery) = (thurloeRecord: DbKeyValuePair) => {
      val include: Rep[Boolean] = true

      val userIdFilter = queryParameters.userId.map { userIds =>
        val userIdFilters = userIds map { userId =>
          val samUser = Await.result(lookupSamUser(userId, samDao), 60 seconds)
          thurloeRecord.userId === samUser.getId ||
          thurloeRecord.userId === samUser.getGoogleSubjectId || thurloeRecord.userId === samUser.getAzureB2CId

        }
        userIdFilters.reduceLeft(_ || _)
      }
      val keyFilter = queryParameters.key.map { keys =>
        val keyFilters = keys map { key => thurloeRecord.key === key }
        keyFilters.reduceLeft(_ || _)
      }

      val optionalFilters = List(userIdFilter, keyFilter)
      val filters = optionalFilters.map(_.getOrElse(include))
      filters.reduceLeftOption(_ && _).getOrElse(include)
    }

    for {
      filteredOnUserAndKey <- lookupWithConstraint(userIdAndKeyConstraint(queryParameters))
      // We have to filter out values outside of the Slick access because the values are encrypted until now.
      valueFilter = (userKeyValuePair: UserKeyValuePairWithId) =>
        queryParameters.value map { values =>
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
  private def databaseWrite(userKeyValuePair: UserKeyValuePair,
                            encryptedValue: EncryptedBytes,
                            samDao: SamDAO): Future[DatabaseOperation] = {
    val lookupExists = lookupIncludingDatabaseId(userKeyValuePair.userId, userKeyValuePair.keyValuePair.key, samDao)
    lookupExists flatMap { existingKvp =>
      update(existingKvp, userKeyValuePair, encryptedValue)
    } recoverWith {
      case _: KeyNotFoundException => insert(userKeyValuePair, encryptedValue)
      case e                       => Future.failed(e)
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

  private def update(oldKeyValuePair: UserKeyValuePairWithId,
                     userKeyValuePair: UserKeyValuePair,
                     newEncryptedValue: EncryptedBytes): Future[DatabaseOperation] =
    // We've just looked up and found an entry, so this ID should never be None. However, belt and braces...
    oldKeyValuePair.id match {
      case None        => Future.failed(new KeyNotFoundException(userKeyValuePair.userId, userKeyValuePair.keyValuePair.key))
      case Some(rowId) =>
        // NB: Using sqlu"..." strings does clever DB magic to turn this into a proper parameterised DB command to avoid insertion attacks.
        def sqlUpdateCommand: DBIO[Int] =
          sqlu"UPDATE KEY_VALUE_PAIR SET VALUE=${newEncryptedValue.base64CipherText}, IV=${newEncryptedValue.base64Iv} WHERE KVP_ID=$rowId"

        for {
          affectedRowsCount <- database.run(sqlUpdateCommand.transactionally)
          x <- handleDatabaseWriteResponse(affectedRowsCount, DatabaseOperation.Update)
        } yield x
    }

  private def handleDatabaseWriteResponse(affectedRowsCount: Int, op: DatabaseOperation): Future[DatabaseOperation] =
    if (affectedRowsCount == 1) {
      Future.successful(op)
    } else {
      Future.failed(
        InvalidDatabaseStateException(s"Modified $affectedRowsCount rows in database (expected to modify 1)")
      )
    }

  def set(samDao: SamDAO, userKeyValuePairs: UserKeyValuePairs): Future[DatabaseOperation] =
    Future
      .sequence(userKeyValuePairs.toKeyValueSeq.map { userKeyValuePair =>
        Aes256Cbc.encrypt(userKeyValuePair.keyValuePair.value.getBytes("UTF-8"), secretKey) match {
          case Success(encryptedValue) => databaseWrite(userKeyValuePair, encryptedValue, samDao)
          case Failure(t)              => Future.failed(t)
        }
      })
      .map { operations =>
        operations.distinct match {
          case Seq(op) => op
          case _       => DatabaseOperation.Upsert
        }
      }

  def delete(userId: String, key: String): Future[Unit] = {
    val action =
      keyValuePairTable.filter(thurloeRecord => thurloeRecord.key === key && thurloeRecord.userId === userId).delete
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

  def status(): Future[Unit] = {
    // Check database connection by selecting version
    val action = sql"select version ()".as[String]
    database.run(action.transactionally) map { _ => () }
  }

  def initWithLiquibase() = {
    val liquibaseConf = configFile.getConfig("liquibase")
    val liquibaseChangeLog = liquibaseConf.getString("changelog")
    val initWithLiquibase = liquibaseConf.getBoolean("initWithLiquibase")

    if (initWithLiquibase) {
      val dbConnection = database.source.createConnection()
      try {
        val liquibaseConnection = new JdbcConnection(dbConnection)
        val resourceAccessor: ResourceAccessor = new ClassLoaderResourceAccessor()
        val liquibase = new Liquibase(liquibaseChangeLog, resourceAccessor, liquibaseConnection)

        liquibase.update(new Contexts())

      } catch {
        case e: SQLTimeoutException =>
          val isCertProblem = Throwables.getRootCause(e).isInstanceOf[SunCertPathBuilderException]
          if (isCertProblem) {
            val k = "javax.net.ssl.keyStore"
            if (System.getProperty(k) == null) {
              logger.warn("************")
              logger.warn(
                s"The system property '${k}' is null. This is likely the cause of the database"
                  + " connection failure."
              )
              logger.warn("************")
            }
          }
          throw e
      } finally {
        dbConnection.close()
      }
    }
  }
}

object DatabaseOperation extends Enumeration {
  type DatabaseOperation = Value
  val Insert, Update, Upsert = Value
}
