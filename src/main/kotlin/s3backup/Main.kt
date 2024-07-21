package s3backup

import s3backup.commands.*
import java.io.File
import java.nio.file.Paths
import java.util.*

// TODO object metadata: encrypt SHA256 hash? (or get rid of the hash...)
object Main {

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
            println("ERROR: Please supply arguments (configfile, command, others)")
            return
        }

        val configFilePath = args[0]
        val config: Properties = ConfigLoader.load(configFilePath)

        val commandStr =  args[1].uppercase(Locale.US)
        val param1 = args[2]
        val param2 = args[3]

        val command: Runnable? = when (commandStr) {
            "KEYGEN" -> KeygenCommand()
            "LIST" -> ListCommand(config = config, prefix = param1, format = param2)
            "UPLOAD-BATCH" -> UploadBatchCommand(config = config, backupItemsFile = File(param1))
            "UPLOADFILE-ENCRYPT" -> UploadFile(
                config = config,
                file = Paths.get(param1),
                targetKey = param2,
                s3Client = S3ClientFactory.makePlaintextClientWithCredentials(config), // use encryption SDK separately.
                encryption = true
            )

            "UPLOADFILE-PLAINTEXT" -> UploadFile(
                config = config,
                file = Paths.get(param1),
                targetKey = param2,
                s3Client = S3ClientFactory.makePlaintextClientWithCredentials(config),
                encryption = false
            )
            // For example, AWS EC2 instances may have permission without explicitly providing credentials
            "UPLOADFILE-PLAINTEXT-NOCREDS" -> UploadFile(
                config = config,
                file = Paths.get(param1),
                targetKey = param2,
                s3Client = S3ClientFactory.makePlaintextClientWithoutCredentials(),
                encryption = false
            )

            "DOWNLOAD" -> DownloadCommand(config = config, s3SourceKey = param1, targetDir = param2)
            else -> {
                println("Unknown command $commandStr")
                null
            }
        }
        command?.run()
    }
}