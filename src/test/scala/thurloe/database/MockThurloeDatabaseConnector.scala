package thurloe.database

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import thurloe.dataaccess.{HttpSamDAO, SamDAO}
import thurloe.service.{ThurloeQuery, UserKeyValuePairs}

import scala.concurrent.Future
import org.mockito.MockitoSugar.mock

case object MockThurloeDatabaseConnector extends DataAccess {
  val samDAO = mock[HttpSamDAO]

  // By default return no users
  when(samDAO.getUserById(any[String])).thenReturn(List.empty)

  override def set(keyValuePairs: UserKeyValuePairs)(implicit samDao: SamDAO) = ???

  override def lookup(userId: String, key: String)(implicit samDao: SamDAO) = ???

  override def lookup(userId: String)(implicit samDao: SamDAO) = ???

  override def lookup(query: ThurloeQuery)(implicit samDao: SamDAO) = ???

  override def delete(userId: String, key: String) = ???

  override def status() = Future.successful(())
}
