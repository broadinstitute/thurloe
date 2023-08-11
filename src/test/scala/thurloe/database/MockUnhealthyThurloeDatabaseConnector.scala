package thurloe.database

import org.mockito.MockitoSugar.mock
import thurloe.dataaccess.{HttpSamDAO, SamDAO}
import thurloe.service.{ThurloeQuery, UserKeyValuePairs}

import scala.concurrent.Future

case object MockUnhealthyThurloeDatabaseConnector extends DataAccess {
  val samDAO = mock[HttpSamDAO]
  override def set(keyValuePairs: UserKeyValuePairs)(implicit samDao: SamDAO) =
    Future.failed(new Exception("does not work"))

  override def lookup(userId: String, key: String)(implicit samDao: SamDAO) =
    Future.failed(new Exception("does not work"))

  override def lookup(userId: String)(implicit samDao: SamDAO) = Future.failed(new Exception("does not work"))

  override def lookup(query: ThurloeQuery)(implicit samDao: SamDAO) = Future.failed(new Exception("does not work"))

  override def delete(userId: String, key: String) = Future.failed(new Exception("does not work"))

  override def status() = Future.failed(new Exception("Failure from \"unhealthy\" mock DAO"))
}
