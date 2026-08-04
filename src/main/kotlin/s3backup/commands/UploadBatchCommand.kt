package s3backup.commands

import s3backup.BatchItem
import s3backup.S3APIWrapper
import s3backup.S3ClientFactory
import software.amazon.awssdk.services.s3.model.StorageClass
import java.io.File
import java.nio.file.Paths

class UploadBatchCommand(
    private val batchItems: List<BatchItem>,
    private val storageClass: StorageClass
) : Runnable {
    override fun run() {
        val s3 = S3APIWrapper(S3ClientFactory.makePlaintextClient(useCredentials = true))
        batchItems.forEach { item ->
            println("Processing batch item: $item")
            when (item.command) {
                "UPLOADFOLDERZIP" -> s3.uploadFolderAsZip(
                    fromLocalFolder = File(item.localPath),
                    targetKey = item.remoteAWSPath,
                    storageClass = storageClass,
                    encryption = item.encrypt
                )

                "UPLOADFOLDER" -> s3.uploadFolder(
                    fromLocalFolder = File(item.localPath),
                    toRemoteFolder = item.remoteAWSPath,
                    storageClass = storageClass,
                    encryption = item.encrypt
                )

                "UPLOADFILE" -> s3.uploadFile(
                    sourceFile = Paths.get(item.localPath),
                    targetKey = item.remoteAWSPath,
                    storageClass = storageClass,
                    encryption = item.encrypt
                )

                else -> throw UnsupportedOperationException("command ${item.command} not implemented")
            }
        }
        println("=== Uploads completed ===")
    }
}
