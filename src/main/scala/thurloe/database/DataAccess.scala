package thurloe.database

import thurloe.service.{UserKeyValuePairs, UserKeyValuePair, KeyValuePair}

import scala.util.Try

trait DataAccess {
  def setKeyValuePair(keyValuePair: UserKeyValuePair): Try[Unit]
  def keyLookup(userId: String, key: String): Try[KeyValuePair]
  def collectAll(userId: String): Try[UserKeyValuePairs]
  def deleteKeyValuePair(userId: String, key: String): Try[Unit]
}

case class KeyNotFoundException(userId: String, key: String) extends Exception {
  override def getMessage = s"Key '$key' not found for user '$userId'"
}

case class InvalidDatabaseStateException(message: String) extends Exception {
  override def getMessage = message
}