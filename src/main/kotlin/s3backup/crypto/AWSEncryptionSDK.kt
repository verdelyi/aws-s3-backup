package s3backup.crypto

// Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

import Utils
import com.amazonaws.encryptionsdk.*
import com.amazonaws.encryptionsdk.jce.JceMasterKey
import s3backup.util.copyToWithProgress
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.SecureRandom
import java.util.*
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

/**
 * This program demonstrates using a standard Java [SecretKey] object as a [MasterKey] to
 * encrypt and decrypt streaming data.
 */
object AWSEncryptionSDK {

    fun encryptToStream(crypto: AwsCrypto, inFile: Path, masterKey: JceMasterKey): InputStream {
        // Create an encryption context to identify this ciphertext
        val context = Collections.singletonMap("Example", "FileStreaming")

        // Because the file might be too large to load into memory, we stream the data, instead of
        // loading it all at once.
        val inStream = Files.newInputStream(inFile)
        val encryptingStream: CryptoInputStream<JceMasterKey> = crypto.createEncryptingStream(masterKey, inStream, context)
        return encryptingStream
    }

    fun encryptToFile(crypto: AwsCrypto, inFile: Path, outFile: Path, masterKey: JceMasterKey) {
        val encryptingStream = encryptToStream(crypto, inFile, masterKey)
        val out = Files.newOutputStream(outFile)
        encryptingStream.copyTo(out)
        encryptingStream.close()
        out.close()
    }

    fun decryptFromStream(crypto: AwsCrypto, inStream: InputStream, outFile: Path, masterKey: JceMasterKey) {
        // Since we encrypted using an unsigned algorithm suite, we can use the recommended
        // createUnsignedMessageDecryptingStream method that only accepts unsigned messages.
        val decryptingStream: CryptoInputStream<JceMasterKey> = crypto.createUnsignedMessageDecryptingStream(masterKey, inStream)
        // Verify the encryption context before returning the plaintext -- Does it contain the expected encryption context?
        check("FileStreaming" == decryptingStream.cryptoResult.encryptionContext["Example"]) { "Bad encryption context" }

        // Write the plaintext data to disk.
        val outStream = Files.newOutputStream(outFile)
        decryptingStream.copyToWithProgress(
            out = outStream,
            reportPerBytes = 100_000_000,
            onProgressEvent = { println("${Utils.bytesToGigabytes(it)} GB downloaded") })
        //IOUtils.copy(decryptingStream, outStream)

        decryptingStream.close()
        outStream.close()
    }

    fun decryptFromFile(crypto: AwsCrypto, inFile: Path, outFile: Path, masterKey: JceMasterKey) {
        val inStream = Files.newInputStream(inFile)
        decryptFromStream(crypto, inStream, outFile, masterKey)
    }

    fun generateKey(keyfile: String) {
        val rnd = SecureRandom()
        val rawKey = ByteArray(32)
        rnd.nextBytes(rawKey)
        Paths.get(keyfile).writeBytes(rawKey)
    }

    fun loadKey(keyfile: Path): JceMasterKey {
        val rawKey = keyfile.readBytes()
        val secretkey: SecretKey = SecretKeySpec(rawKey, "AES")
        // Create a JCE master key provider using the random key and an AES-GCM encryption algorithm
        return JceMasterKey.getInstance(secretkey, "Example", "PLRBackupKeyID", "AES/GCM/NoPadding")
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