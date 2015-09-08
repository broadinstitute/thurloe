package thurloe.database

import thurloe.service.KeyValuePair

import scala.util.{Success, Try}

case object ThurloeDatabaseConnector extends DataAccess {

  // TODO: Fill THESE in with database access implementation.
  def keyLookup(key: String) = Success(KeyValuePair("yek", "eulav"))
  def collectAll() = Success(Seq(
    KeyValuePair("key1", "Bob Loblaw's Law Blog"),
    KeyValuePair("key2", "Blah blah blah blah blah")))
  def setKeyValuePair(keyValuePair: KeyValuePair): Try[Unit] = Success(())
  def deleteKeyValuePair(key: String): Try[Unit] = Success()
}
