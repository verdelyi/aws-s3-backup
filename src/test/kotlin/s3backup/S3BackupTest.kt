package s3backup

import Utils
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import s3backup.crypto.AWSEncryptionSDK
import software.amazon.awssdk.services.s3.model.StorageClass
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The S3 upload/download tests need a real bucket and AWS credentials, so they can't run
 * on an arbitrary machine. Point S3BACKUP_TEST_CONFIG at a valid config file (see README) to
 * enable them; otherwise they're skipped. testEncryptionRoundtrip needs neither and always runs.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3BackupTest {

    private lateinit var tempDir: Path
    private var s3ConfigAvailable = false

    @BeforeAll
    fun setup() {
        tempDir = Files.createTempDirectory("s3backup_test")
        System.getenv("S3BACKUP_TEST_CONFIG")?.let { configFile ->
            ConfigLoader.load(configFile)
            s3ConfigAvailable = true
        }
    }

    @AfterAll
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun testHexKeyRoundtrip() {
        // Confirms bytes -> hex -> bytes is lossless and produces a working encryption key,
        // independent of the actual encryption key used in production configs.
        repeat(20) {
            val originalKey = Random.nextBytes(32)
            val hex = Utils.bytesToHex(originalKey)
            val decodedKey = Utils.hexToBytes(hex)
            assertTrue(originalKey.contentEquals(decodedKey), "hex round-trip must be lossless")
        }

        val srcFile = tempDir.resolve("hex_test_data.bin")
        val encryptedFile = tempDir.resolve("hex_encrypted.bin")
        val decryptedFile = tempDir.resolve("hex_decrypted.bin")
        try {
            val testData = Random.nextBytes(64_000)
            Files.write(srcFile, testData)

            val originalKey = Random.nextBytes(32)
            val keyFromHex = Utils.hexToBytes(Utils.bytesToHex(originalKey))

            val crypto = AWSEncryptionSDK.makeCryptoObject()
            AWSEncryptionSDK.encryptToFile(
                crypto = crypto, inFile = srcFile, outFile = encryptedFile,
                masterKey = AWSEncryptionSDK.makeKeyRingFromRawKey(originalKey)
            )
            AWSEncryptionSDK.decryptFromFile(
                crypto = crypto, inFile = encryptedFile, outFile = decryptedFile,
                masterKey = AWSEncryptionSDK.makeKeyRingFromRawKey(keyFromHex)
            )

            assertTrue(testData.contentEquals(Files.readAllBytes(decryptedFile)), "decryption with hex-roundtripped key must match original")
        } finally {
            Files.deleteIfExists(srcFile)
            Files.deleteIfExists(encryptedFile)
            Files.deleteIfExists(decryptedFile)
        }
    }

    @Test
    fun testEncryptionRoundtrip() {
        val srcFile = tempDir.resolve("test_data.bin")
        val encryptedFile = tempDir.resolve("encrypted.bin")
        val decryptedFile = tempDir.resolve("decrypted.bin")

        try {
            // Generate random test data
            val testData = Random.nextBytes(1024 * 1024) // 1MB of random data
            Files.write(srcFile, testData)
            println("Created test file with ${testData.size} bytes of random data")

            val crypto = AWSEncryptionSDK.makeCryptoObject()
            val masterKey = AWSEncryptionSDK.makeKeyRingFromRawKey(Random.nextBytes(32))

            println("Encrypting...")
            AWSEncryptionSDK.encryptToFile(crypto = crypto, inFile = srcFile, outFile = encryptedFile, masterKey = masterKey)

            println("Decrypting...")
            AWSEncryptionSDK.decryptFromFile(crypto = crypto, inFile = encryptedFile, outFile = decryptedFile, masterKey = masterKey)

            // Verify data integrity
            val decryptedData = Files.readAllBytes(decryptedFile)
            val isValid = testData.contentEquals(decryptedData)

            println("Original size: ${testData.size} bytes")
            println("Decrypted size: ${decryptedData.size} bytes")
            println("Data integrity: ${if (isValid) "PASS" else "FAIL"}")

            assertTrue(isValid, "Encryption/decryption round-trip failed - data corruption detected")
            assertEquals(testData.size, decryptedData.size)
        } finally {
            Files.deleteIfExists(srcFile)
            Files.deleteIfExists(encryptedFile)
            Files.deleteIfExists(decryptedFile)
        }
    }

    @Test
    fun testUploadDownloadPlaintext() {
        assumeTrue(s3ConfigAvailable, "Set S3BACKUP_TEST_CONFIG to a config file to run S3 integration tests")
        val testFile = tempDir.resolve("test_plaintext.txt")
        val downloadFile = tempDir.resolve("downloaded_plaintext.txt")
        val testContent = "Hello World - Plaintext Test"
        val testKey = "test/plaintext-${System.currentTimeMillis()}.txt"
        val s3Wrapper = S3APIWrapper(S3ClientFactory.makePlaintextClient(useCredentials = true))

        try {
            // Create test file
            Files.writeString(testFile, testContent)

            // Upload
            println("Uploading plaintext file...")
            s3Wrapper.uploadFile(
                sourceFile = testFile,
                targetKey = testKey,
                storageClass = StorageClass.STANDARD,
                encryption = false
            )

            // Download
            println("Downloading plaintext file...")
            s3Wrapper.downloadFile(sourceKey = testKey, targetFile = downloadFile)

            // Verify
            val downloadedContent = Files.readString(downloadFile)
            assertEquals(testContent, downloadedContent, "Downloaded content should match uploaded content")
            println("Plaintext upload/download test PASSED")
        } finally {
            Files.deleteIfExists(testFile)
            Files.deleteIfExists(downloadFile)
            s3Wrapper.deleteObject(testKey)
        }
    }

    @Test
    fun testUploadDownloadEncrypted() {
        assumeTrue(s3ConfigAvailable, "Set S3BACKUP_TEST_CONFIG to a config file to run S3 integration tests")
        val testFile = tempDir.resolve("test_encrypted.txt")
        val downloadFile = tempDir.resolve("downloaded_encrypted.txt")
        val testContent = "Hello World - Encrypted Test"
        val testKey = "test/encrypted-${System.currentTimeMillis()}.txt"
        val s3Wrapper = S3APIWrapper(S3ClientFactory.makePlaintextClient(useCredentials = true))

        try {
            // Create test file
            Files.writeString(testFile, testContent)

            // Upload
            println("Uploading encrypted file...")
            s3Wrapper.uploadFile(
                sourceFile = testFile,
                targetKey = testKey,
                storageClass = StorageClass.STANDARD,
                encryption = true
            )

            // Download
            println("Downloading encrypted file...")
            s3Wrapper.downloadFile(sourceKey = testKey, targetFile = downloadFile)

            // Verify
            val downloadedContent = Files.readString(downloadFile)
            assertEquals(testContent, downloadedContent, "Downloaded content should match uploaded content")
            println("Encrypted upload/download test PASSED")
        } finally {
            Files.deleteIfExists(testFile)
            Files.deleteIfExists(downloadFile)
            s3Wrapper.deleteObject(testKey)
        }
    }

    @Test
    fun testDeleteObject() {
        assumeTrue(s3ConfigAvailable, "Set S3BACKUP_TEST_CONFIG to a config file to run S3 integration tests")
        val testFile = tempDir.resolve("test_delete.txt")
        val testKey = "test/delete-${System.currentTimeMillis()}.txt"
        val s3Wrapper = S3APIWrapper(S3ClientFactory.makePlaintextClient(useCredentials = true))

        try {
            Files.writeString(testFile, "Hello World - Delete Test")

            println("Uploading file to delete...")
            s3Wrapper.uploadFile(
                sourceFile = testFile,
                targetKey = testKey,
                storageClass = StorageClass.STANDARD,
                encryption = false
            )
            assertTrue(s3Wrapper.getObjectKeys(testKey).any { it.key() == testKey }, "Object should exist after upload")

            println("Deleting object...")
            s3Wrapper.deleteObject(testKey)

            assertTrue(s3Wrapper.getObjectKeys(testKey).none { it.key() == testKey }, "Object should be gone after delete")
            println("Delete test PASSED")
        } finally {
            Files.deleteIfExists(testFile)
        }
    }
}
