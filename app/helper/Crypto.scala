package helper

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.{Cipher, KeyGenerator, SecretKey}
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import scala.util.{Failure, Success, Try}

// This singleton regroups high level functions using AEAD with ChaCha20-Poly1305
//
// Libsodium gives a summary of limitations of AEAD schemes:
// https://doc.libsodium.org/secret-key_cryptography/aead
//
// Java code for ChaCha20-Poly1305 can be found here:
// https://openjdk.java.net/jeps/329
//
// Rekeying:
// - NIST recommandations:
//     SP 800-57 Part 1 Rev. 5
//     Recommendation for Key Management: Part 1 – General
//     https://csrc.nist.gov/publications/detail/sp/800-57-part-1/rev-5/final
//     5.6.4 Transitioning to New Algorithms and Key Sizes in Systems
//     If the protected data is retained, it should be re-protected using
//     an approved algorithm and key size that will protect the information
//     for the remainder of its security life.
// - Here `KeySet` is a pair of (encrypting key, old keys previously used to encrypt)
//   It is useful for smooth key rotation and fields that do not require key rotation
//   because they are wiped after a fixed amount of time.
//
object Crypto {

  // Key generator for use with the ChaCha20 and ChaCha20-Poly1305 algorithms.
  // https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html#keygenerator-algorithms
  val KEYGENERATOR_ALGORITHM = "ChaCha20"
  val KEY_SIZE_BYTES = 32
  val KEY_SIZE_BITS = 256
  val AE_ALGORITHM = "ChaCha20-Poly1305"
  val NONCE_SIZE_BYTES = 12 // 96 bits

  // Basic Base64 Alphabet (contains '+' and '/')
  // https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/Base64.html
  private val base64Decoder = Base64.getDecoder()
  private val base64Encoder = Base64.getEncoder()

  case class Key(key: SecretKey)

  /** A `KeySet` holds an encrypt/decrypt key `validKey` and 'decrypt-only' keys `oldKeys`. This is
    * used in order to smoothly rotate keys. `oldKeys` are assumed to be insecure for encrypting new
    * data (due to scheduled rotation or compromise).
    */
  case class KeySet(validKey: Key, oldKeys: List[Key])

  /** This class wraps the ciphertext in order to avoid keeping the plaintext in memory when this is
    * not necessary.
    */
  case class EncryptedField private (cipherTextBase64: String, aad: String) {

    private def decryptWithOldKeys(keyset: KeySet, noOldKeysError: Throwable): Try[String] =
      keyset.oldKeys.foldLeft[Try[String]](Failure(noOldKeysError)) { case (acc, oldKey) =>
        if (acc.isSuccess)
          acc
        else
          decryptField(cipherTextBase64, aad, oldKey)
      }

    def decrypt(keyset: KeySet): Try[String] =
      decryptField(cipherTextBase64, aad, keyset.validKey)
        .recoverWith { case firstError: javax.crypto.AEADBadTagException =>
          decryptWithOldKeys(keyset, firstError)
        }

    /** Does nothing if the encrypting key has been used to encrypt the field. Decrypt and encrypt
      * if one of the old keys has been used to encrypt the field. Used for key rotation.
      *
      * Returns None if nothing has been done, Some if key rotation has occured.
      *
      * `encryptIfPlainText` is used for updating a plain text field to a encrypted field. The field
      * is encrypted if it fails base64 decoding or decryption.
      */
    def updateKey(keyset: KeySet, encryptIfPlainText: Boolean): Try[Option[EncryptedField]] =
      decryptField(cipherTextBase64, aad, keyset.validKey) match {
        case Success(_) => Success(None)
        case Failure(firstError: javax.crypto.AEADBadTagException) =>
          decryptWithOldKeys(keyset, firstError) match {
            case Success(plainText) =>
              EncryptedField.fromPlainText(plainText, aad, keyset).map(Some.apply)
            case Failure(e) =>
              if (encryptIfPlainText)
                EncryptedField.fromPlainText(cipherTextBase64, aad, keyset).map(Some.apply)
              else
                Failure(e)
          }
        case Failure(e) =>
          if (encryptIfPlainText)
            EncryptedField.fromPlainText(cipherTextBase64, aad, keyset).map(Some.apply)
          else
            Failure(e)
      }

  }

  object EncryptedField {

    def fromPlainText(plainText: String, aad: String, keyset: KeySet): Try[EncryptedField] =
      encryptField(plainText, aad, keyset.validKey).map(ct => EncryptedField(ct, aad))

    def fromCipherText(cipherText: String, aad: String): EncryptedField =
      EncryptedField(cipherText, aad)

  }

  def generateRandomKey(): Key = {
    val generator = KeyGenerator.getInstance(KEYGENERATOR_ALGORITHM)
    val random = SecureRandom.getInstanceStrong()
    generator.init(KEY_SIZE_BITS, random)
    val key = generator.generateKey()
    Key(key)
  }

  def decodeKeyBase64(base64Key: String): Key = {
    val bytes = base64Decoder.decode(base64Key)
    val key = new SecretKeySpec(bytes, KEYGENERATOR_ALGORITHM)
    Key(key)
  }

  def encodeKeyBase64(key: Key): String =
    base64Encoder.encodeToString(key.key.getEncoded)

  /** Returns base64(nonce || cipherText) */
  def encryptField(plainText: String, aad: String, key: Key): Try[String] = Try {
    val cipher = Cipher.getInstance(AE_ALGORITHM)
    val nonce = {
      val nonce = Array.ofDim[Byte](NONCE_SIZE_BYTES)
      new SecureRandom().nextBytes(nonce)
      nonce
    }
    cipher.init(Cipher.ENCRYPT_MODE, key.key, new IvParameterSpec(nonce))
    cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8))
    val cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8))
    val cipherTextWithNonce = ByteBuffer
      .allocate(cipherText.length + NONCE_SIZE_BYTES)
      .put(nonce)
      .put(cipherText)
      .array()
    base64Encoder.encodeToString(cipherTextWithNonce)
  }

  def decryptField(cipherTextBase64: String, aad: String, key: Key): Try[String] = Try {
    val (nonce, cipherText) = {
      val cipherTextWithNonce = base64Decoder.decode(cipherTextBase64)
      val nonce = Array.ofDim[Byte](NONCE_SIZE_BYTES)
      val ct = Array.ofDim[Byte](cipherTextWithNonce.length - NONCE_SIZE_BYTES)
      ByteBuffer.wrap(cipherTextWithNonce).get(nonce).get(ct)
      (nonce, ct)
    }
    val cipher = Cipher.getInstance(AE_ALGORITHM)
    cipher.init(Cipher.DECRYPT_MODE, key.key, new IvParameterSpec(nonce))
    cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8))
    val plainText = cipher.doFinal(cipherText)
    new String(plainText, StandardCharsets.UTF_8)
  }

}
