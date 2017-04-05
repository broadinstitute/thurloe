package thurloe.database

import thurloe.database.DatabaseOperation.DatabaseOperation
import thurloe.service.{ThurloeQuery, KeyValuePairWithId, UserKeyValuePairs, UserKeyValuePair}

import scala.concurrent.Future

trait DataAccess {

  def set(keyValuePairs: UserKeyValuePairs): Future[DatabaseOperation]
  def lookup(userId: String, key: String): Future[UserKeyValuePair]
  def lookup(userId: String): Future[UserKeyValuePairs]
  def lookup(query: ThurloeQuery): Future[Seq[UserKeyValuePair]]
  def delete(userId: String, key: String): Future[Unit]
  def status(): Future[Unit]
}

case class KeyNotFoundException(userId: String, key: String) extends Exception(s"Key '$key' not found for user '$userId'")

case class InvalidDatabaseStateException(message: String) extends Exception(message)
