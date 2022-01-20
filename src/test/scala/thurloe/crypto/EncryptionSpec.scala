package thurloe.crypto

import com.typesafe.config.ConfigFactory
import org.scalatest.FunSpec

import scala.util.{Failure, Success}

class EncryptionSpec extends FunSpec{

  val cryptoConfig = ConfigFactory.load().getConfig("crypto")
  val secretKey = SecretKey(cryptoConfig.getString("key"))

  describe("The Encryption") {
    it("should decrypt encrypted messages") {
      val plaintextString = "0123456789ABCDEF, etc"
      val plaintextBytes = plaintextString.getBytes("UTF-8")

      val encrypted = Aes256Cbc.encrypt(plaintextBytes, secretKey)
      val decrypted = encrypted flatMap { x => Aes256Cbc.decrypt(x, secretKey) map { new String(_, "UTF-8") } }

      decrypted match {
        case Success(decryptedValue) =>
          assert(plaintextString.equals(decryptedValue))
          def encryptedValue = new String((encrypted map { _.cipherText }).get, "UTF-8")
          assert(!decryptedValue.equals(encryptedValue))
        case Failure(e) => fail(e)
      }
    }

    it("should not encrypt with an invalid key size") {
      val badSecretKey = SecretKey("A" + secretKey.key)

      val plaintextBytes = "Doesn't even matter what this string is".getBytes("UTF-8")

      Aes256Cbc.encrypt(plaintextBytes, badSecretKey) match {
        case Failure(e: IllegalArgumentException) => // Expected result
        case x => fail(s"Expected IllegalArgumentException but got $x")
      }
    }

    it("should not decrypt with an invalid IV size") {
      val cipherTextBytes = "Doesn't matter what these bytes are".getBytes("UTF-8")
      val invalidIv = "Bad length".getBytes("UTF-8")

      Aes256Cbc.decrypt(EncryptedBytes(cipherTextBytes, invalidIv), secretKey) match {
        case Failure(e: IllegalArgumentException) => // Expected result
        case x => fail(s"Expected IllegalArgumentException but got $x")
      }
    }
  }
}
