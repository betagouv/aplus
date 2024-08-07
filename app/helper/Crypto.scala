package helper

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.{Arrays, Base64}
import javax.crypto.{Cipher, KeyGenerator, SecretKey}
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import scala.util.Try

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

  /** Returns (nonce || ciphertext) */
  def encryptBytes(plaintext: Array[Byte], aad: String, key: Key): Try[Array[Byte]] = Try {
    val (cipher, nonce) = encryptCipher(aad, key)

    // Always plaintext.length + 16 bytes of ChaCha20-Poly1305 authentication tag
    val outputSize = cipher.getOutputSize(plaintext.length)

    // We allocate only one Array for the ciphertext
    val ciphertextWithNonce = Arrays.copyOf(nonce, NONCE_SIZE_BYTES + outputSize)

    // Fills the result Array after the nonce
    cipher.doFinal(plaintext, 0, plaintext.length, ciphertextWithNonce, NONCE_SIZE_BYTES)

    ciphertextWithNonce
  }

  /** Returns base64(nonce || ciphertext) */
  def encryptField(plaintext: String, aad: String, key: Key): Try[String] =
    encryptBytes(plaintext.getBytes(StandardCharsets.UTF_8), aad, key)
      .map(base64Encoder.encodeToString)

  private def decryptCipher(nonce: Array[Byte], aad: String, key: Key): Cipher = {
    val cipher = Cipher.getInstance(AE_ALGORITHM)
    cipher.init(Cipher.DECRYPT_MODE, key.key, new IvParameterSpec(nonce))
    cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8))
    cipher
  }

  def decryptBytes(ciphertextWithNonce: Array[Byte], aad: String, key: Key): Try[Array[Byte]] =
    Try {
      val nonce = Arrays.copyOf(ciphertextWithNonce, NONCE_SIZE_BYTES)
      val cipher = decryptCipher(nonce, aad, key)
      val plaintext = {
        val inputOffset = NONCE_SIZE_BYTES
        val inputLen = ciphertextWithNonce.length - NONCE_SIZE_BYTES
        // We avoid allocating a new Array
        cipher.doFinal(ciphertextWithNonce, inputOffset, inputLen)
      }
      plaintext
    }

  def decryptField(ciphertextBase64: String, aad: String, key: Key): Try[String] =
    Try(base64Decoder.decode(ciphertextBase64))
      .flatMap(ciphertextWithNonce => decryptBytes(ciphertextWithNonce, aad, key))
      .flatMap(plaintext => Try(new String(plaintext, StandardCharsets.UTF_8)))

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

    /** The implementation tries to reduce copies to a minimum. */
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
            val (nonce, ciphertext) = chunk.splitAt(NONCE_SIZE_BYTES)
            val cipher = decryptCipher(nonce.toArray, aad, key)
            val plaintext = cipher.doFinal(ciphertext.toArray)
            Chunk.array(plaintext)
          }
        }
        .unchunks

  }

}
