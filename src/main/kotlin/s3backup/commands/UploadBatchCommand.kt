package s3backup.commands

import Utils
import s3backup.S3APIWrapper
import s3backup.S3ClientFactory
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.s3.model.StorageClass
import java.io.File
import java.nio.file.Paths

class UploadBatchCommand(
    private val backupItemsFile: File,
    private val storageClass: StorageClass
) : Runnable {
    override fun run() {
        try {
            val s3 = S3APIWrapper(S3ClientFactory.makePlaintextClientWithCredentials())
            backupItemsFile.useLines { lines ->
                lines.forEach { line ->
                    if (line.startsWith("#") || line.isEmpty()) return@forEach
                    println("Processing batch line ${line}...")
                    val split = line.split(" // ")
                    val command = split[0]
                    when (command) {
                        "UPLOADFOLDERZIP" -> {
                            val localFolder = File(split[1])
                            val targetKey = split[2]
                            val encrypt = Utils.parseEncryptField(split[3])
                            s3.uploadFolderAsZip(
                                fromLocalFolder = localFolder,
                                targetKey = targetKey,
                                storageClass = storageClass,
                                encryption = encrypt
                            )
                        }

                        "UPLOADFOLDER" -> {
                            val localFolder = File(split[1])
                            val remoteFolder = split[2]
                            val encrypt = Utils.parseEncryptField(split[3])
                            s3.uploadFolder(
                                fromLocalFolder = localFolder,
                                toRemoteFolder = remoteFolder,
                                storageClass = storageClass,
                                encryption = encrypt
                            )
                        }

                        "UPLOADFILE" -> {
                            val localFile = Paths.get(split[1])
                            val targetKey = split[2]
                            val encrypt = Utils.parseEncryptField(split[3])
                            s3.uploadFile(sourceFile = localFile, targetKey = targetKey, storageClass = storageClass, encryption = encrypt)
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