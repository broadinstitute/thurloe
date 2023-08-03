package thurloe.database

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import thurloe.dataaccess.HttpSamDAO
import thurloe.service.{ThurloeQuery, UserKeyValuePairs}

import scala.concurrent.Future
import org.mockito.MockitoSugar.mock

case object MockThurloeDatabaseConnector extends DataAccess {
  val samDAO = mock[HttpSamDAO]

  // By default return no users
  when(samDAO.getUserById(any[String])).thenReturn(List.empty)

  override def set(keyValuePairs: UserKeyValuePairs) = ???

  override def lookup(userId: String, key: String) = ???

  override def lookup(userId: String) = ???

  override def lookup(query: ThurloeQuery) = ???

  override def delete(userId: String, key: String) = ???

  override def status() = Future.successful(())
}
