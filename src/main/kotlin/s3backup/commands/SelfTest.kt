package s3backup.commands

import s3backup.crypto.AWSEncryptionSDK
import java.nio.file.Files
import kotlin.random.Random

class SelfTest : Runnable {
    override fun run() {
        val tempDir = Files.createTempDirectory("encryption_test")
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

            println("===================== Encryption test ========================")
            AWSEncryptionSDK.encryptToFile(crypto = crypto, inFile = srcFile, outFile = encryptedFile, masterKey = masterKey)

            println("===================== Decryption test ========================")
            AWSEncryptionSDK.decryptFromFile(crypto = crypto, inFile = encryptedFile, outFile = decryptedFile, masterKey = masterKey)

            // Verify data integrity
            val decryptedData = Files.readAllBytes(decryptedFile)
            val isValid = testData.contentEquals(decryptedData)

            println("===================== Verification ========================")
            println("Original size: ${testData.size} bytes")
            println("Decrypted size: ${decryptedData.size} bytes")
            println("Data integrity: ${if (isValid) "PASS" else "FAIL"}")

            if (!isValid) {
                throw RuntimeException("Encryption/decryption round-trip failed - data corruption detected")
            }

        } finally {
            // Clean up temp files
            Files.deleteIfExists(srcFile)
            Files.deleteIfExists(encryptedFile)
            Files.deleteIfExists(decryptedFile)
            Files.deleteIfExists(tempDir)
        }
    }
}