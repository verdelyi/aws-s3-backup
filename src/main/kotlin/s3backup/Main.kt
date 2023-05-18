package s3backup

import s3backup.commands.*
import s3backup.crypto.AWSEncryptionSDK
import java.io.File
import java.nio.file.Paths
import java.util.*

// TODO object metadata: encrypt SHA256 hash? (or get rid of the hash...)
object Main {
    private val config: Properties = ConfigLoader.load()

    /*fun test() {
        val dc = DownloadCommand(
            config = config,
            s3SourceKey = "localserver/localserver-20220118-0000-inc-based-on-20220117-0000-inc-dev.tar.zst",
            targetDir = "/home/verdelyi/Desktop/"
        )
        dc.run()
    }*/

    /*fun enctest() {
        val srcFile = Paths.get("/home/verdelyi/Desktop/bigfile.zip")
        val crypto = AWSEncryptionSDK.makeCryptoObject()
        val masterKeyFile = Paths.get(config.getProperty("config.encryptionKeyFile"))
        val masterKey = AWSEncryptionSDK.loadKey(masterKeyFile)
        println("===================== Encryption test ========================")
        AWSEncryptionSDK.encryptToFile(
            crypto = crypto,
            inFile = srcFile,
            outFile = Paths.get("tmp.encrypted"),
            masterKey = masterKey
        )
        println("===================== Decryption test ========================")
        AWSEncryptionSDK.decryptFromFile(
            crypto = crypto,
            inFile = Paths.get("tmp.encrypted"),
            outFile = Paths.get("tmp.decrypted"),
            masterKey = masterKey
        )
    }*/

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            println("ERROR: Please supply a command")
            return
        }
        val command: Runnable? = when (val commandStr = args[0].uppercase(Locale.US)) {
            "KEYGEN" -> KeygenCommand()
            "LIST" -> ListCommand(config = config, prefix = args[1], format = args[2])
            "UPLOAD-BATCH" -> UploadBatchCommand(config = config, backupItemsFile = File(args[1]))
            "UPLOADFILEANDDELETE-ENCRYPT" -> UploadFileAndDelete(
                config = config,
                file = Paths.get(args[1]),
                targetKey = args[2],
                s3Client = S3ClientFactory.makePlaintextClientWithCredentials(config), // use encryption SDK separately.
                encryption = true
            )

            "UPLOADFILEANDDELETE-PLAINTEXT" -> UploadFileAndDelete(
                config = config,
                file = Paths.get(args[1]),
                targetKey = args[2],
                s3Client = S3ClientFactory.makePlaintextClientWithCredentials(config),
                encryption = false
            )
            // For example, AWS EC2 instances may have permission without explicitly providing credentials
            "UPLOADFILEANDDELETE-PLAINTEXT-NOCREDS" -> UploadFileAndDelete(
                config = config,
                file = Paths.get(args[1]),
                targetKey = args[2],
                s3Client = S3ClientFactory.makePlaintextClientWithoutCredentials(),
                encryption = false
            )

            "DOWNLOAD" -> DownloadCommand(config = config, s3SourceKey = args[1], targetDir = args[2])
            else -> {
                println("Unknown command $commandStr")
                null
            }
        }
        command?.run()
    }
}