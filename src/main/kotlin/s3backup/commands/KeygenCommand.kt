package s3backup.commands

import s3backup.KeyOps
import java.io.File
import java.util.*

class KeygenCommand(private val config: Properties): Runnable {
    override fun run() {
        val keyPair = KeyOps.generateKeyPair(algorithm = "RSA", keySize = 4096)
        KeyOps.saveKeyPair(publicKeyFile = File(config.getProperty("config.publicKeyFile")),
            privateKeyFile = File(config.getProperty("config.privateKeyFile")), keyPair = keyPair)
    }
}