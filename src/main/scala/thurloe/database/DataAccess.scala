package thurloe.database

import thurloe.database.DatabaseOperation.DatabaseOperation
import thurloe.service.{ThurloeQuery, KeyValuePairWithId, UserKeyValuePairs, UserKeyValuePair}

import scala.concurrent.Future

trait DataAccess {

  def set(keyValuePair: UserKeyValuePair): Future[DatabaseOperation]
  def lookup(userId: String, key: String): Future[UserKeyValuePair]
  def lookup(userId: String): Future[UserKeyValuePairs]
  def lookup(query: ThurloeQuery): Future[Seq[UserKeyValuePair]]
  def delete(userId: String, key: String): Future[Unit]
  def status(): Future[Unit]
}

case class KeyNotFoundException(userId: String, key: String) extends Exception {
  override def getMessage = s"Key '$key' not found for user '$userId'"
}

case class InvalidDatabaseStateException(message: String) extends Exception {
  override def getMessage = message
}

case class DatabaseConnectionException() extends Exception {
  override def getMessage = {
    s"Connection to database was unsuccessful"
  }
}