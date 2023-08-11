package thurloe.database

import thurloe.dataaccess.SamDAO
import thurloe.database.DatabaseOperation.DatabaseOperation
import thurloe.service.{ThurloeQuery, UserKeyValuePair, UserKeyValuePairs}

import scala.concurrent.Future

trait DataAccess {
  def set(keyValuePairs: UserKeyValuePairs)(implicit samDAO: SamDAO): Future[DatabaseOperation]
  def lookup(userId: String, key: String)(implicit samDAO: SamDAO): Future[UserKeyValuePair]
  def lookup(userId: String)(implicit samDAO: SamDAO): Future[UserKeyValuePairs]
  def lookup(query: ThurloeQuery)(implicit samDAO: SamDAO): Future[Seq[UserKeyValuePair]]
  def delete(userId: String, key: String): Future[Unit]
  def status(): Future[Unit]
}

case class KeyNotFoundException(userId: String, key: String)
    extends Exception(s"Key '$key' not found for user '$userId'")

case class InvalidDatabaseStateException(message: String) extends Exception(message)
