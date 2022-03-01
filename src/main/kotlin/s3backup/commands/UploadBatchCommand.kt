package s3backup.commands

import com.amazonaws.AmazonServiceException
import com.amazonaws.SdkClientException
import s3backup.S3APIWrapper
import s3backup.S3ClientFactory
import java.io.File
import java.util.*

class UploadBatchCommand(private val config: Properties,
                         private val backupItemsFile: File) : Runnable {
    override fun run() {
        try {
            val s3Plain = S3APIWrapper(config, S3ClientFactory.makePlaintextClientWithCredentials(config))
            val s3Encrypted = S3APIWrapper(config, S3ClientFactory.makeEncryptionClientWithCredentials(config))
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
                            val client = if (encrypt) s3Encrypted else s3Plain
                            client.uploadFolderAsZip(fromLocalFolder = localFolder, targetKey = targetKey, encryption = encrypt)
                        }
                        "UPLOADFOLDER" -> {
                            val localFolder = File(split[1])
                            val remoteFolder = split[2]
                            val encrypt = Utils.parseEncryptField(split[3])
                            val client = if (encrypt) s3Encrypted else s3Plain
                            client.uploadFolder(fromLocalFolder = localFolder, toRemoteFolder = remoteFolder, encryption = encrypt)
                        }
                        "UPLOADFILE" -> {
                            val localFolder = File(split[1])
                            val targetKey = split[2]
                            val encrypt = Utils.parseEncryptField(split[3])
                            val client = if (encrypt) s3Encrypted else s3Plain
                            client.uploadFile(sourceFile = localFolder, targetKey = targetKey, encryption = encrypt)
                        }
                        else -> throw UnsupportedOperationException("command $command not implemented")
                    }
                }
            }
            println("=== Uploads completed ===")
        } catch (e: AmazonServiceException) {
            // The call was transmitted successfully, but Amazon S3 couldn't process it, so it returned an error response.
            e.printStackTrace()
        } catch (e: SdkClientException) {
            // Amazon S3 couldn't be contacted for a response, or the client couldn't parse the response from Amazon S3.
            e.printStackTrace()
        }
    }
}