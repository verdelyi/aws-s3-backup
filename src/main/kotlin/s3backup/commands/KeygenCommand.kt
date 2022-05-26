package s3backup.commands

import s3backup.crypto.AWSEncryptionSDK
import java.util.*

class KeygenCommand(private val config: Properties): Runnable {
    override fun run() {
        AWSEncryptionSDK.generateKey(config.getProperty("config.encryptionKeyFile"))
    }
}