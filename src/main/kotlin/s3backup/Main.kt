package s3backup

import s3backup.commands.*
import java.io.File
import java.util.*

// TODO object metadata: encrypt SHA256 hash (and remove existing plaintext hashes.
//  Just get hash, encrypt it, put it back. No need to get the objects.)
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

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            println("ERROR: Please supply a command")
            return
        }
        val command: Runnable? = when (val commandStr = args[0].uppercase(Locale.US)) {
            "KEYGEN" -> KeygenCommand(config = config)
            "LIST" -> ListCommand(config = config, prefix = args[1])
            "UPLOAD-BATCH" -> UploadBatchCommand(config = config, backupItemsFile = File(args[1]))
            "UPLOADFILEANDDELETE-ENCRYPT" -> UploadFileAndDelete(
                config = config,
                file = File(args[1]),
                targetKey = args[2],
                s3Client = S3ClientFactory.makeEncryptionClientWithCredentials(config),
                encryption = true
            )
            "UPLOADFILEANDDELETE-PLAINTEXT" -> UploadFileAndDelete(
                config = config,
                file = File(args[1]),
                targetKey = args[2],
                s3Client = S3ClientFactory.makePlaintextClientWithCredentials(config),
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