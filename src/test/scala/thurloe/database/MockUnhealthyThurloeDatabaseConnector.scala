package thurloe.database
import thurloe.service.{ThurloeQuery, UserKeyValuePairs}

import scala.concurrent.Future

case object MockUnhealthyThurloeDatabaseConnector extends DataAccess {
  override def set(keyValuePairs: UserKeyValuePairs) = ???

  override def lookup(userId: String, key: String) = ???

  override def lookup(userId: String) = ???

  override def lookup(query: ThurloeQuery) = ???

  override def delete(userId: String, key: String) = ???

  override def status() = Future.failed(new Exception("Failure from \"unhealthy\" mock DAO"))
}
