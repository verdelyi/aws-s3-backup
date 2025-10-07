package s3backup

import s3backup.commands.*
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.s3.model.StorageClass
import java.io.File
import java.nio.file.Paths
import java.util.*

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
        ConfigLoader.load(configFilePath)

        // Fixed 2nd param: command
        val commandStr = args[1].uppercase(Locale.US)

        // Other params are command-dependent
        val command: Runnable? = when (commandStr) {
            "KEYGEN" -> KeygenCommand()
            "LIST" -> ListCommand(prefix = args[2], format = args[3])
            "UPLOAD-BATCH" -> UploadBatchCommand(backupItemsFile = File(args[2]), storageClass = storageClass)
            "UPLOADFILE-ENCRYPT" -> UploadFile(
                file = Paths.get(args[2]),
                targetKey = args[3],
                s3Client = S3ClientFactory.makePlaintextClient(useCredentials = true), // use encryption SDK separately.
                storageClass = storageClass,
                encryption = true
            )

            "UPLOADFILE-PLAINTEXT" -> UploadFile(
                file = Paths.get(args[2]),
                targetKey = args[3],
                s3Client = S3ClientFactory.makePlaintextClient(useCredentials = true),
                storageClass = storageClass,
                encryption = false
            )
            // For example, AWS EC2 instances may have permission without explicitly providing credentials
            "UPLOADFILE-PLAINTEXT-NOCREDS" -> UploadFile(
                file = Paths.get(args[2]),
                targetKey = args[3],
                s3Client = S3ClientFactory.makePlaintextClient(useCredentials = false),
                storageClass = storageClass,
                encryption = false
            )

            "DOWNLOAD" -> DownloadCommand(s3SourceKey = args[2], targetDir = args[3])

            else -> {
                println("Unknown command $commandStr")
                null
            }
        }
        try {
            command?.run()
        } catch (e: AwsServiceException) {
            // The call was transmitted successfully, but Amazon S3 couldn't process it, so it returned an error response.
            e.printStackTrace()
        } catch (e: SdkClientException) {
            // Amazon S3 couldn't be contacted for a response, or the client couldn't parse the response from Amazon S3.
            e.printStackTrace()
        }
    }
}