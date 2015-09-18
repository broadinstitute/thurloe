package thurloe.crypto

import java.security.SecureRandom
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import javax.crypto.Cipher

import org.apache.commons.codec.binary.Base64

import scala.util.{Failure, Success, Try}

sealed trait Encryption {

  def encryption: String
  def blockSize: Int
  def keySize: Int
  def paddingMode: String
  def cipherMode: String

  val ranGen = new SecureRandom()

  final def init(mode: Int, secretKey: Array[Byte], iv: Array[Byte]) = {
    val key = new SecretKeySpec(secretKey, encryption)
    val ivSpec = new IvParameterSpec(iv)
    val cipher = Cipher.getInstance(s"$encryption/$cipherMode/$paddingMode")
    cipher.init(mode, key, ivSpec)
    cipher
  }

  final def encrypt(plainText: Array[Byte], secretKey: SecretKey): Try[EncryptedBytes] = {
    if(secretKey.key.length != keySize / 8) {
      Failure(new IllegalArgumentException(s"Key size (${secretKey.key.length}) did not match required ${keySize / 8}"))
    }
    else {
      // Generate an IV:
      val iv = new Array[Byte](blockSize / 8)
      ranGen.nextBytes(iv)

      val cipher = init(Cipher.ENCRYPT_MODE, secretKey.key, iv)
      Success(EncryptedBytes(cipher.doFinal(plainText), iv))
    }
  }

  final def decrypt(encryptedBytes: EncryptedBytes, secretKey: SecretKey): Try[Array[Byte]] = {
    if(secretKey.key.length != keySize / 8) {
      Failure(new IllegalArgumentException(s"Key size (${secretKey.key.length}) did not match required ${keySize / 8}"))
    }
    else if(encryptedBytes.iv.length != blockSize / 8) {
      Failure(new IllegalArgumentException(s"IV size (${encryptedBytes.iv.length}) did not match required ${blockSize / 8}"))
    }
    else {
      val cipher = init(Cipher.DECRYPT_MODE, secretKey.key, encryptedBytes.iv)
      Success(cipher.doFinal(encryptedBytes.cipherText))
    }
  }
}

case object Aes256Cbc extends Encryption {
  override def encryption = "AES"
  override def blockSize = 128
  override def keySize = 256
  override def paddingMode = "PKCS5Padding"
  override def cipherMode = "CBC"
}

final case class EncryptedBytes(cipherText: Array[Byte], iv: Array[Byte]) {
  def base64CipherText = Base64.encodeBase64String(cipherText)
  def base64Iv = Base64.encodeBase64String(iv)
}

object EncryptedBytes {
  def apply(base64CipherTextString: String, base64IvString: String): EncryptedBytes =
    EncryptedBytes(Base64.decodeBase64(base64CipherTextString), Base64.decodeBase64(base64IvString))
}

final case class SecretKey(key: Array[Byte])
object SecretKey {
  def apply(base64KeyString: String): SecretKey =
    SecretKey(Base64.decodeBase64(base64KeyString))
}
