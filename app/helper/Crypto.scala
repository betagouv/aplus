package helper

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.{Arrays, Base64}
import javax.crypto.{Cipher, KeyGenerator, SecretKey}
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import scala.util.{Failure, Success, Try}

/** This singleton regroups high level functions using AEAD with ChaCha20-Poly1305
  *
  * Libsodium gives a summary of limitations of AEAD schemes:
  * https://doc.libsodium.org/secret-key_cryptography/aead
  *
  * Java code for ChaCha20-Poly1305 can be found here: https://openjdk.java.net/jeps/329
  *
  * Rekeying:
  *   - NIST recommandations:
  *     - SP 800-57 Part 1 Rev. 5
  *     - Recommendation for Key Management: Part 1 – General
  *       https://csrc.nist.gov/publications/detail/sp/800-57-part-1/rev-5/final 5.6.4
  *     - Transitioning to New Algorithms and Key Sizes in Systems If the protected data is
  *       retained, it should be re-protected using an approved algorithm and key size that will
  *       protect the information for the remainder of its security life.
  *   - Here `KeySet` is a pair of (encrypting key, old keys previously used to encrypt) It is
  *     useful for smooth key rotation and fields that do not require key rotation because they are
  *     wiped after a fixed amount of time.
  */
object Crypto {

  // Key generator for use with the ChaCha20 and ChaCha20-Poly1305 algorithms.
  // https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html
  val KEYGENERATOR_ALGORITHM = "ChaCha20"
  val KEY_SIZE_BYTES = 32
  val KEY_SIZE_BITS = 256
  val AE_ALGORITHM = "ChaCha20-Poly1305"
  val NONCE_SIZE_BYTES = 12 // 96 bits
  val TAG_SIZE_BYTES = 16 // the authentication tag is 128 bits

  // Basic Base64 Alphabet (contains '+' and '/')
  // https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Base64.html
  val base64Decoder = Base64.getDecoder()
  val base64Encoder = Base64.getEncoder()

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

    /** Does nothing (returns None) if the encrypting key has been used to encrypt the field.
      * Decrypt and encrypt if one of the old keys has been used to encrypt the field. Used for key
      * rotation.
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

  private def encryptCipher(aad: String, key: Key): (Cipher, Array[Byte]) = {
    val cipher = Cipher.getInstance(AE_ALGORITHM)
    val nonce = {
      val nonce = Array.ofDim[Byte](NONCE_SIZE_BYTES)
      new SecureRandom().nextBytes(nonce)
      nonce
    }
    cipher.init(Cipher.ENCRYPT_MODE, key.key, new IvParameterSpec(nonce))
    cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8))
    (cipher, nonce)
  }

  /** Returns (nonce || cipherText) */
  def encryptBytes(plainText: Array[Byte], aad: String, key: Key): Try[Array[Byte]] = Try {
    val (cipher, nonce) = encryptCipher(aad, key)

    // Always plainText.length + 16 bytes of ChaCha20-Poly1305 authentication tag
    val outputSize = cipher.getOutputSize(plainText.length)

    // We allocate only one Array for the cipherText
    val cipherTextWithNonce = Arrays.copyOf(nonce, NONCE_SIZE_BYTES + outputSize)

    // Fills the result Array after the nonce
    cipher.doFinal(plainText, 0, plainText.length, cipherTextWithNonce, NONCE_SIZE_BYTES)

    cipherTextWithNonce
  }

  /** Returns base64(nonce || cipherText) */
  def encryptField(plainText: String, aad: String, key: Key): Try[String] =
    encryptBytes(plainText.getBytes(StandardCharsets.UTF_8), aad, key)
      .map(base64Encoder.encodeToString)

  private def decryptCipher(nonce: Array[Byte], aad: String, key: Key): Cipher = {
    val cipher = Cipher.getInstance(AE_ALGORITHM)
    cipher.init(Cipher.DECRYPT_MODE, key.key, new IvParameterSpec(nonce))
    cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8))
    cipher
  }

  def decryptBytes(cipherTextWithNonce: Array[Byte], aad: String, key: Key): Try[Array[Byte]] =
    Try {
      val nonce = Arrays.copyOf(cipherTextWithNonce, NONCE_SIZE_BYTES)
      val cipher = decryptCipher(nonce, aad, key)
      val plainText = {
        val inputOffset = NONCE_SIZE_BYTES
        val inputLen = cipherTextWithNonce.length - NONCE_SIZE_BYTES
        // We avoid allocating a new Array
        cipher.doFinal(cipherTextWithNonce, inputOffset, inputLen)
      }
      plainText
    }

  def decryptField(cipherTextBase64: String, aad: String, key: Key): Try[String] =
    Try(base64Decoder.decode(cipherTextBase64))
      .flatMap(cipherTextWithNonce => decryptBytes(cipherTextWithNonce, aad, key))
      .flatMap(plainText => Try(new String(plainText, StandardCharsets.UTF_8)))

  /** ******************** // Stream Utilities // *********************
    *
    * This implementation buffers the whole file before encrypting it. This is done to reduce
    * complexity as encrypting by chunks in a robust way is difficult. (See references below)
    *
    * Implementation note: Encryption/Decryption is CPU blocking, we use IO instead of IO.blocking
    * in order to stay on the compute pool.
    *
    * References:
    *   - An implementation is done in
    *     https://github.com/jwojnowski/fs2-aes/blob/main/src/main/scala/me/wojnowski/fs2/aes/Aes.scala
    *     however we do not use it as it is vulnerable to chunks reordering. Since we do not allow
    *     uploads of more than 10Mb, we avoid adding the complexity of a secure chunking strategy.
    *   - The java class used to encrypt files
    *     https://docs.oracle.com/javase/8/docs/api/javax/crypto/CipherInputStream.html is noted as
    *     potentially not suitable for use with decryption in an authenticated mode of operation.
    *   - libsodium documentation on ChaCha20-Poly1305:
    *     https://libsodium.gitbook.io/doc/secret-key_cryptography/aead/chacha20-poly1305
    *   - Exchange on the difficulty in implementing encryption by chunks:
    *     https://crypto.stackexchange.com/questions/106983/encrypting-arbitrary-large-files-in-aead-chunks-how-to-protect-against-chunk-r
    *   - The fs2 streaming implementation of hashing:
    *     https://github.com/typelevel/fs2/blob/24a3e72b9dabd5076c8696586df7bc727da4c9be/core/jvm/src/main/scala/fs2/hash.scala#L54
    */
  object stream {
    import cats.effect.IO
    import fs2.{Chunk, Pipe, Stream}

    /** The implementation tries to reduce copies at a minimum. */
    def encrypt(aad: String, key: Key): Pipe[IO, Byte, Byte] =
      in =>
        Stream.eval(IO(Crypto.encryptCipher(aad, key))).flatMap { case (cipher, nonce) =>
          Stream.emits(nonce) ++
            in.chunks.noneTerminate
              .evalMapAccumulate(cipher) { (cipher, chunkOpt) =>
                chunkOpt match {
                  case None =>
                    val f = cipher.doFinal()
                    IO((cipher, Some(Chunk.array(f))))
                  case Some(chunk) =>
                    val next =
                      Option(cipher.update(chunk.toArray))
                        .filter(_.nonEmpty)
                        .map(a => Chunk.array(a))
                    IO((cipher, next))
                }
              }
              .evalMapFilter { case (_, c) => IO.pure(c) }
              .unchunks
        }

    def decrypt(aad: String, key: Key): Pipe[IO, Byte, Byte] =
      // `.chunkAll` is efficient and does not copy chunks
      _.chunkAll
        .evalMap { chunk =>
          IO {
            val (nonce, cipherText) = chunk.splitAt(NONCE_SIZE_BYTES)
            val cipher = decryptCipher(nonce.toArray, aad, key)
            val plainText = cipher.doFinal(cipherText.toArray)
            Chunk.array(plainText)
          }
        }
        .unchunks

  }

}
