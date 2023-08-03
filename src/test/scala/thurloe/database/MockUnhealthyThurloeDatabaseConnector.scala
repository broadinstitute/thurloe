package thurloe.database

import org.mockito.MockitoSugar.mock
import thurloe.dataaccess.HttpSamDAO
import thurloe.service.{ThurloeQuery, UserKeyValuePairs}

import scala.concurrent.Future

case object MockUnhealthyThurloeDatabaseConnector extends DataAccess {
  val samDAO = mock[HttpSamDAO]
  override def set(keyValuePairs: UserKeyValuePairs) = Future.failed(new Exception("does not work"))

  override def lookup(userId: String, key: String) = Future.failed(new Exception("does not work"))

  override def lookup(userId: String) = Future.failed(new Exception("does not work"))

  override def lookup(query: ThurloeQuery) = Future.failed(new Exception("does not work"))

  override def delete(userId: String, key: String) = Future.failed(new Exception("does not work"))

  override def status() = Future.failed(new Exception("Failure from \"unhealthy\" mock DAO"))
}
