package s3backup.commands

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.StorageClass
import s3backup.S3APIWrapper
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class UploadFileAndDelete(
    private val config: Properties,
    private val file: Path,
    private val targetKey: String,
    private val s3Client: AmazonS3,
    private val storageClass: StorageClass = StorageClass.StandardInfrequentAccess,
    private val encryption: Boolean
) : Runnable {
    override fun run() {
        val s3 = S3APIWrapper(config, s3Client)
        s3.uploadFile(sourceFile = file, targetKey = targetKey, storageClass = storageClass, encryption = encryption)
        print("Upload completed, deleting...")
        Files.delete(file)
        println("OK")
    }
}