package org.sripas.seaman
package streaming.core.utils.encryption

trait Encryptor {

  def encryptString(value: String): String

  def decryptString(value: String): String

  def encryptAuth(username: Option[String], password: Option[String]): (Option[String], Option[String])

  def decryptAuth(username: Option[String], password: Option[String]): (Option[String], Option[String])
}
