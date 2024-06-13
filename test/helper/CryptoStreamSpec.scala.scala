package helper

import cats.effect.IO
import cats.effect.testing.specs2.CatsEffect
import fs2.Stream
import helper.Crypto._
import org.junit.runner.RunWith
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.scalacheck.Parameters

/** Note that we decide to block on the IO effect in scalacheck property testing, this is due to the
  * fact that there does not exist any integration between specs2, scalacheck and cats-effect. This
  * would require some kind of wrapper. However this is unnecessary since everything tested here is
  * "CPU blocking", and not I/O blocking.
  *
  * List of useful code in case a wrapper is needed in the future:
  *   - cats-effect tests uses specs2
  *     https://github.com/typelevel/cats-effect/blob/2c0406fa3700c2805f976f1767e9e7f05b9cf7cd/tests/shared/src/test/scala/cats/effect/Runners.scala#L50
  *   - there exists a wrapper for scalacheck + cats-effect, but it only implements munit and not
  *     specs2 https://github.com/typelevel/scalacheck-effect
  *   - specs2 integrates with scalacheck using prop
  *     https://github.com/etorreborre/specs2/blob/bbf99b7be990890271d76e196dda1d8e5cb940c7/scalacheck/src/main/scala/org/specs2/scalacheck/ScalaCheckPropertyCreation.scala#L20
  *   - if one would want to integrate specs2 + scalacheck + cats-effect, the obvious way is to
  *     rewrite the specs2 integration of scalacheck for the `PropF` in
  *     https://github.com/typelevel/scalacheck-effect
  *   - cats-effect integration with specs2 can provide another useful reference
  *     https://github.com/typelevel/cats-effect-testing/blob/f6c9a1b8b24b7210f7293349637e58382f2e5525/specs2/shared/src/main/scala/cats/effect/testing/specs2/CatsEffect.scala#L36
  */
@RunWith(classOf[JUnitRunner])
class CryptoStreamSpec extends Specification with ScalaCheck with CatsEffect {

  "Given hand-picked data, crypto streams should" >> {
    import CryptoTestValues._

    "encrypt with empty AAD" >> {
      for {
        // Note: there is a random nonce in the cipher text
        cipherText <- Stream
          .emits(ptBytes)
          .covary[IO]
          .through(stream.encrypt("", testKey))
          .compile
          .to(Array)
        roundtripPlainText <- Stream
          .emits(cipherText)
          .covary[IO]
          .through(stream.decrypt("", testKey))
          .compile
          .to(Array)
      } yield {
        // Note: specs2 knows about Arrays and compares elements
        roundtripPlainText must equalTo(ptBytes)
      }
    }

    "decrypt with empty AAD" >> {
      Stream
        .emits(ctNoAADBytes)
        .covary[IO]
        .through(stream.decrypt("", testKey))
        .compile
        .to(Array)
        .map { decryptedBytes =>
          decryptedBytes must equalTo(ptBytes)
        }
    }

    "fail if AAD is incorrect when AAD is empty" >> {
      Stream
        .emits(ctNoAADBytes)
        .covary[IO]
        .through(stream.decrypt("something", testKey))
        .compile
        .drain
        .attempt
        .map { result =>
          result.isLeft must beTrue
          result.left.toOption.get.isInstanceOf[javax.crypto.AEADBadTagException] must beTrue
        }
    }

    "decrypt with non-empty AAD" >> {
      Stream
        .emits(ctWithAADBytes)
        .covary[IO]
        .through(stream.decrypt("some aad", testKey))
        .compile
        .to(Array)
        .map { decryptedBytes =>
          decryptedBytes must equalTo(ptBytes)
        }
    }

    "fail if AAD is incorrect when AAD is non-empty" >> {
      Stream
        .emits(ctWithAADBytes)
        .covary[IO]
        .through(stream.decrypt("other aad", testKey))
        .compile
        .drain
        .attempt
        .map { result =>
          result.isLeft must beTrue
          result.left.toOption.get.isInstanceOf[javax.crypto.AEADBadTagException] must beTrue
        }
    }
  }

  "Given arbitrary values, crypto streams should" >> {
    import CryptoScalaCheck._

    import cats.effect.unsafe.implicits.global

    "encrypt then decrypt in an idempotent way when plaintext is a String" >> {
      prop { (pt: String, key: Key, aad: String) =>
        val ptBytes = pt.getBytes
        val resultBytes = Stream
          .emits(ptBytes)
          .covary[IO]
          .through(stream.encrypt(aad, key))
          .through(stream.decrypt(aad, key))
          .compile
          .to(Array)
          .unsafeRunSync()
        ptBytes === resultBytes
      }
    }

    "encrypt then fail to decrypt with a different key" >> {
      prop { (pt: String, key: Key, otherKey: Key, aad: String) =>
        val ptBytes = pt.getBytes
        val result = Stream
          .emits(ptBytes)
          .covary[IO]
          .through(stream.encrypt(aad, key))
          .through(stream.decrypt(aad, otherKey))
          .compile
          .to(Array)
          .attempt
          .unsafeRunSync()
        result.isLeft &&
        result.left.toOption.get.isInstanceOf[javax.crypto.AEADBadTagException]
      }
    }

    "encrypt then fail to decrypt with a different aad" >> {
      prop { (pt: String, key: Key, aad: String, otherAad: String) =>
        val ptBytes = pt.getBytes
        val result = Stream
          .emits(ptBytes)
          .covary[IO]
          .through(stream.encrypt(aad, key))
          .through(stream.decrypt(otherAad, key))
          .compile
          .to(Array)
          .attempt
          .unsafeRunSync()

        aad == otherAad ||
        (
          result.isLeft &&
            result.left.toOption.get.isInstanceOf[javax.crypto.AEADBadTagException]
        )
      }
    }

    "encrypt then decrypt arbitrary byte arrays of size less than 10Mb (CPU intensive)" >> {
      // "overrides" the number of checks (CPU intensive)
      implicit val defaultParameters = Parameters(minTestsOk = 5)

      prop { (pt: BlobLessThan10Mb, key: Key, aad: String) =>
        // We create chunks to mimic the way the bytes are presented to the encrypting Pipe
        val chunks = pt.bytes.grouped(1024 * 1024).map(chunk => fs2.Chunk.array(chunk)).toVector
        val resultBytes = Stream
          .emits(chunks)
          .covary[IO]
          .unchunks
          .through(stream.encrypt(aad, key))
          .through(stream.decrypt(aad, key))
          .compile
          .to(Array)
          .unsafeRunSync()
        pt.bytes === resultBytes
      }
    }

    "output an encrypted size of (original size + nonce size + tag size)" >> {
      prop { (pt: String, key: Key, aad: String) =>
        val ptBytes = pt.getBytes
        val resultBytes = Stream
          .emits(ptBytes)
          .covary[IO]
          .through(stream.encrypt(aad, key))
          .compile
          .to(Array)
          .unsafeRunSync()
        resultBytes.length === ptBytes.length + NONCE_SIZE_BYTES + TAG_SIZE_BYTES
      }
    }

    "output an encrypted size of (original size + nonce size + tag size) (CPU intensive)" >> {

      implicit val defaultParameters = Parameters(minTestsOk = 5)

      prop { (pt: BlobLessThan10Mb, key: Key, aad: String) =>
        val resultBytes = Stream
          .emits(pt.bytes)
          .covary[IO]
          .through(stream.encrypt(aad, key))
          .compile
          .to(Array)
          .unsafeRunSync()
        resultBytes.length === pt.bytes.length + NONCE_SIZE_BYTES + TAG_SIZE_BYTES
      }
    }
  }
}
