package s3backup.commands

import Utils
import s3backup.crypto.AWSEncryptionSDK

class KeygenCommand : Runnable {
    override fun run() {
        val keyHex = Utils.bytesToHex(AWSEncryptionSDK.generateKeyBytes())
        println("Generated encryption key (put this into the 'encryptionKeyHex' field of your config file):")
        println(keyHex)
    }
}
