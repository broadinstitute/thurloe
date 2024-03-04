package thurloe.database

import thurloe.dataaccess.SamDAO
import thurloe.database.DatabaseOperation.DatabaseOperation
import thurloe.service.{ThurloeQuery, UserKeyValuePair, UserKeyValuePairs}

import scala.concurrent.Future

trait DataAccess {
  def set(keyValuePairs: UserKeyValuePairs): Future[DatabaseOperation]
  def lookup(samDao: SamDAO, userId: String, key: String): Future[UserKeyValuePair]
  def lookup(samDao: SamDAO, userId: String): Future[UserKeyValuePairs]
  def lookup(samDao: SamDAO, query: ThurloeQuery): Future[Seq[UserKeyValuePair]]
  def delete(userId: String, key: String): Future[Unit]
  def status(): Future[Unit]
}

case class KeyNotFoundException(userId: String, key: String)
    extends Exception(s"Key '$key' not found for user '$userId'")

case class InvalidDatabaseStateException(message: String) extends Exception(message)
