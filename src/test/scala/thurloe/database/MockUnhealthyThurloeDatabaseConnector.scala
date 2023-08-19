package thurloe.database

import org.mockito.MockitoSugar.mock
import thurloe.dataaccess.{HttpSamDAO, SamDAO}
import thurloe.database.DatabaseOperation.DatabaseOperation
import thurloe.service.{ThurloeQuery, UserKeyValuePair, UserKeyValuePairs}

import scala.concurrent.Future

case object MockUnhealthyThurloeDatabaseConnector extends DataAccess {
  val samDAO = mock[HttpSamDAO]
  override def set(samDao: SamDAO, keyValuePairs: UserKeyValuePairs): Future[DatabaseOperation] =
    Future.failed(new Exception("does not work"))

  override def lookup(samDao: SamDAO, userId: String, key: String): Future[UserKeyValuePair] =
    Future.failed(new Exception("does not work"))

  override def lookup(samDao: SamDAO, userId: String): Future[UserKeyValuePairs] =
    Future.failed(new Exception("does not work"))

  override def lookup(samDao: SamDAO, query: ThurloeQuery): Future[Seq[UserKeyValuePair]] =
    Future.failed(new Exception("does not work"))

  override def delete(userId: String, key: String) = Future.failed(new Exception("does not work"))

  override def status() = Future.failed(new Exception("Failure from \"unhealthy\" mock DAO"))
}
