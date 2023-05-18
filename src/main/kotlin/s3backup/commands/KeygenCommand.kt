package s3backup.commands

import s3backup.crypto.AWSEncryptionSDK
import java.nio.file.Path
import java.util.*

class KeygenCommand : Runnable {
    override fun run() {
        val keyFilePath = Path.of("new-aws-s3-backup-encryption-key.dat")
        println("Generating encryption key to ${keyFilePath}...")
        AWSEncryptionSDK.generateKey(keyFilePath)
        println("Done.")
    }
}