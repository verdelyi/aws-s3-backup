package s3backup.commands

import s3backup.S3APIWrapper
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.StorageClass
import java.nio.file.Path
import java.util.*

class UploadFile(
    private val config: Properties,
    private val file: Path,
    private val targetKey: String,
    private val s3Client: S3AsyncClient,
    private val storageClass: StorageClass = StorageClass.STANDARD_IA,
    private val encryption: Boolean
) : Runnable {
    override fun run() {
        val s3 = S3APIWrapper(config, s3Client)
        s3.uploadFile(sourceFile = file, targetKey = targetKey, storageClass = storageClass, encryption = encryption)
    }
}