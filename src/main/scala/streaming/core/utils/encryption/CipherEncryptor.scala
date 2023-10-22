package org.sripas.seaman
package streaming.core.utils.encryption

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import org.slf4j.LoggerFactory

import java.util.Base64
import scala.util.{Failure, Success, Try}

class CipherEncryptor(secret: String) extends Encryptor {

  private val logger = LoggerFactory.getLogger(this.getClass)

  // TODO: Cert-based encrypt/decrypt
  override def encryptString(value: String): String = {
    SymmetricCipher.encryptTextToBase64WithKey16(secret, value) match {
      case Failure(exception) =>
        logger.error("Encryption failed", exception)
        throw exception
      case Success(value) => value
    }
  }

  override def decryptString(value: String): String = {
    SymmetricCipher.decryptFromBase64ToTextWithKey16(secret, value) match {
      case Failure(exception) =>
        logger.error("Decryption failed", exception)
        throw exception
      case Success(value) => value
    }
  }

  override def encryptAuth(username: Option[String], password: Option[String]): (Option[String], Option[String]) =
    (username.map(encryptString), password.map(encryptString))

  override def decryptAuth(username: Option[String], password: Option[String]): (Option[String], Option[String]) =
    (username.map(decryptString), password.map(decryptString))

  case class CipherFailedException(msg: String, cause: Throwable)
    extends Exception(msg, cause)

  object SymmetricCipher {
    // Probably doesn't need padding, but let's keep it, in case we use the cipher for something more
    val cipher: Cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")

    private def key16(s: String): String =
      "%16s".format(s)

    def encrypt(key: String, input: Array[Byte]): Try[Array[Byte]] =
      encrypt(key.getBytes, input)

    def encrypt(key: Array[Byte], input: Array[Byte]): Try[Array[Byte]] =
      Try {
        val secretBytes = new SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretBytes, new SecureRandom())
        cipher.getIV ++ cipher.doFinal(input)
      } recoverWith {
        case e: Throwable =>
          e.printStackTrace()
          Failure(CipherFailedException("Encryption failed", e))
      }

    def decrypt(key: String, input: Array[Byte]): Try[Array[Byte]] =
      decrypt(key.getBytes, input)

    def decrypt(key: Array[Byte], input: Array[Byte]): Try[Array[Byte]] =
      Try {
        val secretBytes = new SecretKeySpec(key, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretBytes, new IvParameterSpec(input.slice(0, 16)))
        cipher.doFinal(input.slice(16, input.length))
      } recoverWith {
        case e: Throwable =>
          Failure(CipherFailedException("Decryption failed", e))
      }

    def encryptTextToBase64WithKey16(key: String, text: String): Try[String] =
      encrypt(key16(key), text.getBytes).map(Base64.getEncoder.encodeToString)

    def decryptFromBase64ToTextWithKey16(key: String, encryptedBase64String: String): Try[String] =
      decrypt(key16(key), Base64.getDecoder.decode(encryptedBase64String)).map(_.map(_.toChar).mkString)
  }
}

object CipherEncryptor {
  def apply(secret: String): CipherEncryptor = new CipherEncryptor(secret)
}
