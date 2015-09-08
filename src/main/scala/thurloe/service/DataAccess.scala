package thurloe.service

import scala.util.Try

trait DataAccess {
  def setKeyValuePair(keyValuePair: KeyValuePair): Try[Unit]
  def keyLookup(key: String): Try[KeyValuePair]
  def collectAll(): Try[Seq[KeyValuePair]]
  def deleteKeyValuePair(key: String): Try[Unit]
}

class KeyNotFoundException extends Exception