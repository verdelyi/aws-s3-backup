package s3backup

import s3backup.commands.*
import software.amazon.awssdk.services.s3.model.StorageClass
import java.io.File
import java.nio.file.Paths
import java.util.*

// TODO object metadata: encrypt SHA256 hash? (or get rid of the hash...)
object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            println("ERROR: Please supply arguments (configfile, command, others)")
            return
        }

        val storageClass = StorageClass.STANDARD_IA

        // Fixed 1st param: config file
        val configFilePath = args[0]
        val config: Properties = ConfigLoader.load(configFilePath)

        // Fixed 2nd param: command
        val commandStr = args[1].uppercase(Locale.US)

        // Other params are command-dependent
        val command: Runnable? = when (commandStr) {
            "KEYGEN" -> KeygenCommand()
            "LIST" -> ListCommand(config = config, prefix = args[2], format = args[3])
            "UPLOAD-BATCH" -> UploadBatchCommand(config = config, backupItemsFile = File(args[2]), storageClass = storageClass)
            "UPLOADFILE-ENCRYPT" -> UploadFile(
                config = config,
                file = Paths.get(args[2]),
                targetKey = args[3],
                s3Client = S3ClientFactory.makePlaintextClientWithCredentials(config), // use encryption SDK separately.
                storageClass = storageClass,
                encryption = true
            )

            "UPLOADFILE-PLAINTEXT" -> UploadFile(
                config = config,
                file = Paths.get(args[2]),
                targetKey = args[3],
                s3Client = S3ClientFactory.makePlaintextClientWithCredentials(config),
                storageClass = storageClass,
                encryption = false
            )
            // For example, AWS EC2 instances may have permission without explicitly providing credentials
            "UPLOADFILE-PLAINTEXT-NOCREDS" -> UploadFile(
                config = config,
                file = Paths.get(args[2]),
                targetKey = args[3],
                s3Client = S3ClientFactory.makePlaintextClientWithoutCredentials(),
                storageClass = storageClass,
                encryption = false
            )

            "DOWNLOAD" -> DownloadCommand(config = config, s3SourceKey = args[2], targetDir = args[3])

            "SELFTEST" -> SelfTest()
            else -> {
                println("Unknown command $commandStr")
                null
            }
        }
        command?.run()
    }
}