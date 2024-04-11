package s3backup.crypto

import Utils
import com.amazonaws.encryptionsdk.*
import com.amazonaws.encryptionsdk.jce.JceMasterKey
import s3backup.util.copyToWithProgress
import software.amazon.cryptography.materialproviders.IKeyring
import software.amazon.cryptography.materialproviders.MaterialProviders
import software.amazon.cryptography.materialproviders.model.AesWrappingAlg
import software.amazon.cryptography.materialproviders.model.CreateRawAesKeyringInput
import software.amazon.cryptography.materialproviders.model.MaterialProvidersConfig
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.*
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

/**
 * Based on this example:
 * https://docs.aws.amazon.com/encryption-sdk/latest/developer-guide/java-example-code.html
 */
object AWSEncryptionSDK {

    private const val reportEncryptionProgressPerBytes = 1_000_000_000L

    fun encryptToStream(crypto: AwsCrypto, inFile: Path, masterKey: IKeyring): InputStream {
        // Create an encryption context to identify this ciphertext
        val context = Collections.singletonMap("Example", "FileStreaming")

        // Because the file might be too large to load into memory, we stream the data, instead of
        // loading it all at once.
        val inStream = Files.newInputStream(inFile)
        val encryptingStream: CryptoInputStream<JceMasterKey> =
            crypto.createEncryptingStream(masterKey, inStream, context)
        return encryptingStream
    }

    fun encryptToFile(crypto: AwsCrypto, inFile: Path, outFile: Path, masterKey: IKeyring) {
        val encryptingStream = encryptToStream(crypto, inFile, masterKey)
        val out = Files.newOutputStream(outFile)
        print("Encrypting...")
        encryptingStream.copyToWithProgress(
            out = out,
            reportPerBytes = reportEncryptionProgressPerBytes,
            onProgressEvent = { print("${Utils.bytesToGigabytes(it)} GB...") }
        )
        println("Encryption done.")
        encryptingStream.close()
        out.close()
    }

    fun decryptFromStream(crypto: AwsCrypto, inStream: InputStream, outFile: Path, masterKey: IKeyring) {
        // Since we encrypted using an unsigned algorithm suite, we can use the recommended
        // createUnsignedMessageDecryptingStream method that only accepts unsigned messages.
        val decryptingStream: CryptoInputStream<JceMasterKey> =
            crypto.createUnsignedMessageDecryptingStream(masterKey, inStream)
        // Verify the encryption context before returning the plaintext -- Does it contain the expected encryption context?
        check("FileStreaming" == decryptingStream.cryptoResult.encryptionContext["Example"]) { "Bad encryption context" }

        // Write the plaintext data to disk.
        val outStream = Files.newOutputStream(outFile)
        print("Decrypting...")
        decryptingStream.copyToWithProgress(
            out = outStream,
            reportPerBytes = reportEncryptionProgressPerBytes,
            onProgressEvent = { print("${Utils.bytesToGigabytes(it)} GB...") }
        )
        //IOUtils.copy(decryptingStream, outStream)
        println("Decryption done.")

        decryptingStream.close()
        outStream.close()
    }

    fun decryptFromFile(crypto: AwsCrypto, inFile: Path, outFile: Path, masterKey: IKeyring) {
        val inStream = Files.newInputStream(inFile)
        decryptFromStream(crypto, inStream, outFile, masterKey)
    }

    fun generateKey(keyFilePath: Path) {
        val rnd = SecureRandom()
        val rawKey = ByteArray(32)
        rnd.nextBytes(rawKey)
        keyFilePath.writeBytes(rawKey)
    }

    fun loadKey(keyfile: Path): IKeyring {
        val rawKey = keyfile.readBytes()
        val secretkey: SecretKey = SecretKeySpec(rawKey, "AES")
        val materialProviders = MaterialProviders.builder()
            .MaterialProvidersConfig(MaterialProvidersConfig.builder().build())
            .build()
        val keyringInput: CreateRawAesKeyringInput = CreateRawAesKeyringInput.builder()
            .wrappingKey(ByteBuffer.wrap(secretkey.encoded))
            .keyNamespace("Example")
            .keyName("PLRBackupKeyID")
            .wrappingAlg(AesWrappingAlg.ALG_AES256_GCM_IV12_TAG16)
            .build()
        return materialProviders.CreateRawAesKeyring(keyringInput)
    }

    fun makeCryptoObject(): AwsCrypto {
        // Instantiate the SDK.
        // This builds the AwsCrypto client with the RequireEncryptRequireDecrypt commitment policy,
        // which enforces that this client only encrypts using committing algorithm suites and enforces
        // that this client will only decrypt encrypted messages that were created with a committing algorithm suite.
        // This is the default commitment policy if you build the client with `AwsCrypto.builder().build()`
        // or `AwsCrypto.standard()`.
        // This also chooses to encrypt with an algorithm suite that doesn't include signing for faster decryption.
        // The use case assumes that the contexts that encrypt and decrypt are equally trusted.
        return AwsCrypto.builder()
            .withCommitmentPolicy(CommitmentPolicy.RequireEncryptRequireDecrypt)
            .withEncryptionAlgorithm(CryptoAlgorithm.ALG_AES_256_GCM_HKDF_SHA512_COMMIT_KEY)
            .build()
    }
}