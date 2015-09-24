package thurloe.database

import thurloe.database.DatabaseOperation.DatabaseOperation
import thurloe.service.{KeyValuePairWithId, UserKeyValuePairs, UserKeyValuePair}

import scala.concurrent.Future

trait DataAccess {

  def set(keyValuePair: UserKeyValuePair): Future[DatabaseOperation]
  def lookup(userId: String, key: String): Future[KeyValuePairWithId]
  def lookup(userId: String): Future[UserKeyValuePairs]
  def delete(userId: String, key: String): Future[Unit]
}

case class KeyNotFoundException(userId: String, key: String) extends Exception {
  override def getMessage = s"Key '$key' not found for user '$userId'"
}

case class InvalidDatabaseStateException(message: String) extends Exception {
  override def getMessage = message
}