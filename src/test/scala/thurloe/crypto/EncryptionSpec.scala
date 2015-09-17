package thurloe.crypto

import com.typesafe.config.ConfigFactory
import org.scalatest.FunSpec

import scala.util.{Failure, Success}

class EncryptionSpec extends FunSpec{

  describe("The Encryption") {
    it("should encrypt and decrypt stuff") {
      val enc = Aes256Cbc

      val cryptoConfig = ConfigFactory.load().getConfig("crypto")
      val secretKey = SecretKey(cryptoConfig.getString("key"))

      val plaintextString = "0123456789ABCDEF, etc"
      val plaintextBytes = plaintextString.getBytes("UTF-8")

      val encrypted = enc.encrypt(plaintextBytes, secretKey)
      val decrypted = encrypted flatMap { x => enc.decrypt(x, secretKey) map { new String(_, "UTF-8") } }

      decrypted match {
        case Success(decryptedValue) => assert(plaintextString.equals(decryptedValue))
        case Failure(e) => fail(e)
      }
    }
  }
}
