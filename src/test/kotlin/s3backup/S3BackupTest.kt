package s3backup

import org.junit.jupiter.api.AfterAll
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3BackupTest {

    private lateinit var tempDir: Path

    @BeforeAll
    fun setup() {
        val configFile = "C:\\localfiles\\society\\backup configuration\\config-society.properties"
        ConfigLoader.load(configFile)
        tempDir = Files.createTempDirectory("s3backup_test")
    }

    @AfterAll
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
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
        val testFile = tempDir.resolve("test_plaintext.txt")
        val downloadFile = tempDir.resolve("downloaded_plaintext.txt")
        val testContent = "Hello World - Plaintext Test"
        val testKey = "test/plaintext-${System.currentTimeMillis()}.txt"

        try {
            // Create test file
            Files.writeString(testFile, testContent)

            // Upload
            println("Uploading plaintext file...")
            val s3Client = S3ClientFactory.makePlaintextClient(useCredentials = true)
            val s3Wrapper = S3APIWrapper(s3Client)
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
        }
    }

    @Test
    fun testUploadDownloadEncrypted() {
        val testFile = tempDir.resolve("test_encrypted.txt")
        val downloadFile = tempDir.resolve("downloaded_encrypted.txt")
        val testContent = "Hello World - Encrypted Test"
        val testKey = "test/encrypted-${System.currentTimeMillis()}.txt"

        try {
            // Create test file
            Files.writeString(testFile, testContent)

            // Upload
            println("Uploading encrypted file...")
            val s3Client = S3ClientFactory.makePlaintextClient(useCredentials = true)
            val s3Wrapper = S3APIWrapper(s3Client)
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
        }
    }
}
