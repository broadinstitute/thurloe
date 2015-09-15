package thurloe.database

import thurloe.service.{UserKeyValuePairs, UserKeyValuePair, KeyValuePair}

import scala.concurrent.Future
import scala.util.Try

trait DataAccess {
  def setKeyValuePair(keyValuePair: UserKeyValuePair): Future[Unit]
  def keyLookup(userId: String, key: String): Future[KeyValuePair]
  def collectAll(userId: String): Future[UserKeyValuePairs]
  def deleteKeyValuePair(userId: String, key: String): Future[Unit]
}

case class KeyNotFoundException(userId: String, key: String) extends Exception {
  override def getMessage = s"Key '$key' not found for user '$userId'"
}

case class InvalidDatabaseStateException(message: String) extends Exception {
  override def getMessage = message
}