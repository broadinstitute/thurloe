package thurloe.database

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import thurloe.dataaccess.{HttpSamDAO, SamDAO}
import thurloe.service.{ThurloeQuery, UserKeyValuePair, UserKeyValuePairs}

import scala.concurrent.Future
import org.mockito.MockitoSugar.mock
import thurloe.database.DatabaseOperation.DatabaseOperation

case object MockThurloeDatabaseConnector extends DataAccess {
  val samDAO = mock[HttpSamDAO]

  // By default return no users
  when(samDAO.getUserById(any[String])).thenReturn(List.empty)

  override def set(samDao: SamDAO, keyValuePairs: UserKeyValuePairs): Future[DatabaseOperation] = ???

  override def lookup(samDao: SamDAO, userId: String, key: String): Future[UserKeyValuePair] = ???

  override def lookup(samDao: SamDAO, userId: String): Future[UserKeyValuePairs] = ???

  override def lookup(samDao: SamDAO, query: ThurloeQuery): Future[Seq[UserKeyValuePair]] = ???

  override def delete(userId: String, key: String) = ???

  override def status() = Future.successful(())
}
