package s3backup.commands

import software.amazon.awssdk.awscore.exception.AwsServiceException
import s3backup.S3APIWrapper
import s3backup.S3ClientFactory
import software.amazon.awssdk.core.exception.SdkClientException
import java.io.File
import java.nio.file.Paths
import java.util.*

class UploadBatchCommand(private val config: Properties,
                         private val backupItemsFile: File) : Runnable {
    override fun run() {
        try {
            val s3 = S3APIWrapper(config, S3ClientFactory.makePlaintextClientWithCredentials(config))
            backupItemsFile.useLines { lines ->
                lines.forEach { line ->
                    if (line.startsWith("#") || line.isEmpty()) return@forEach
                    val split = line.split(" // ")
                    val command = split[0]
                    when (command) {
                        "UPLOADFOLDERZIP" -> {
                            val localFolder = File(split[1])
                            val targetKey = split[2]
                            val encrypt = Utils.parseEncryptField(split[3])
                            s3.uploadFolderAsZip(fromLocalFolder = localFolder, targetKey = targetKey, encryption = encrypt)
                        }
                        "UPLOADFOLDER" -> {
                            val localFolder = File(split[1])
                            val remoteFolder = split[2]
                            val encrypt = Utils.parseEncryptField(split[3])
                            s3.uploadFolder(fromLocalFolder = localFolder, toRemoteFolder = remoteFolder, encryption = encrypt)
                        }
                        "UPLOADFILE" -> {
                            val localFolder = Paths.get(split[1])
                            val targetKey = split[2]
                            val encrypt = Utils.parseEncryptField(split[3])
                            s3.uploadFile(sourceFile = localFolder, targetKey = targetKey, encryption = encrypt)
                        }
                        else -> throw UnsupportedOperationException("command $command not implemented")
                    }
                }
            }
            println("=== Uploads completed ===")
        } catch (e: AwsServiceException) {
            // The call was transmitted successfully, but Amazon S3 couldn't process it, so it returned an error response.
            e.printStackTrace()
        } catch (e: SdkClientException) {
            // Amazon S3 couldn't be contacted for a response, or the client couldn't parse the response from Amazon S3.
            e.printStackTrace()
        }
    }
}