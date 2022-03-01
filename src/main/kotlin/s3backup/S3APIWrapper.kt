package s3backup

import Utils
import com.amazonaws.event.ProgressEvent
import com.amazonaws.event.ProgressListener
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.StorageClass
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import s3backup.util.FolderZipper
import s3backup.util.copyTo
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.*

class S3APIWrapper(config: Properties, private val s3Client: AmazonS3) {
    private val bucketName: String = config.getProperty("aws.s3.bucketName")
    private val progressReportPerBytes: Long = 100_000_000
    private val temporaryZipPath: String = config.getProperty("config.temporaryZipPath")

    object TagNames {
        const val encryption = "client-side-encryption"
        const val hash = "sha256"
    }

    private val dirFilter: (File) -> Boolean = { it.name !in listOf(".idea", ".gradle") }

    fun uploadFolder(
        fromLocalFolder: File, toRemoteFolder: String,
        storageClass: StorageClass = StorageClass.Standard,
        encryption: Boolean
    ) {
        require(fromLocalFolder.isDirectory) { "${fromLocalFolder.absolutePath} must be a folder!" }
        require(!toRemoteFolder.endsWith("/")) { "Don't put '/' at the end!" }
        fromLocalFolder.walkTopDown()
            .onEnter(dirFilter)
            .filter { it.isFile }
            .forEach { file ->
                uploadFile(
                    sourceFile = file,
                    targetKey = "$toRemoteFolder/${file.relativeTo(fromLocalFolder).path.replace('\\', '/')}",
                    storageClass = storageClass,
                    encryption = encryption
                )
            }
    }

    fun uploadFolderAsZip(
        fromLocalFolder: File, targetKey: String,
        storageClass: StorageClass = StorageClass.Standard,
        encryption: Boolean
    ) {
        println("Processing folder ${fromLocalFolder.absolutePath}...")
        require(fromLocalFolder.isDirectory) { "${fromLocalFolder.absolutePath} must be a folder!" }
        val zip = File(temporaryZipPath)
        println("Zipping into temporary zip file ${zip.absolutePath})...")
        FolderZipper.pack(sourceDir = fromLocalFolder, zipFile = zip, dirFilter = dirFilter)
        println("Uploading...")
        uploadFile(sourceFile = zip, targetKey = targetKey, storageClass = storageClass, encryption = encryption)
        Files.delete(zip.toPath())
    }

    fun downloadFile(sourceKey: String, targetFile: File) {
        println("Downloading file [S3:/$sourceKey] -> [${targetFile.absolutePath}]...")
        s3Client.getObject(bucketName, sourceKey).use { o ->
            val fos = FileOutputStream(targetFile)
            val progressHandler: (Long) -> Unit = { println("${Utils.bytesToGigabytes(it)} GB downloaded") }
            o.objectContent.use {
                it.copyTo(out = fos, reportPerBytes = 100_000_000, onProgressEvent = progressHandler)
            }
        }
    }

    fun uploadFile(
        sourceFile: File, targetKey: String, storageClass: StorageClass = StorageClass.Standard,
        encryption: Boolean
    ) {
        require(sourceFile.isFile) { "must be a file" }
        val localFileHash: String = Utils.computeFileHash(sourceFile)
        if (s3Client.doesObjectExist(bucketName, targetKey)) {
            val metadata = s3Client.getObjectMetadata(bucketName, targetKey)
            val remoteFileHash = metadata.getUserMetaDataOf(TagNames.hash)
            println("Object '$targetKey' already exists")
            println("    Last modified: ${metadata.lastModified}")
            println("    Client-side encryption: ${metadata.getUserMetaDataOf(TagNames.encryption)}")
            println("    SHA-256 hash: $remoteFileHash")
            if (localFileHash == remoteFileHash) {
                println("Remote file hash is identical, skipping upload")
                return
            }
        }
        println(
            "Uploading [${sourceFile.absolutePath}] -> [S3:/$targetKey]... " +
                    "(Storage class: ${storageClass.name}, Encryption: $encryption)"
        )
        val metadata = ObjectMetadata().apply {
            addUserMetadata(TagNames.encryption, encryption.toString())
            addUserMetadata(TagNames.hash, localFileHash)
        }
        val progressListener = object : ProgressListener {
            var byteCounter: Long = 0
            var bytesAtLastReport: Long = 0
            override fun progressChanged(progressEvent: ProgressEvent) {
                //println("Progress event: ${progressEvent.eventType}")
                byteCounter += progressEvent.bytesTransferred
                if (byteCounter - bytesAtLastReport > progressReportPerBytes) {
                    println("${sourceFile.absolutePath}: ${Utils.bytesToGigabytes(byteCounter)} GB transferred")
                    bytesAtLastReport = byteCounter
                }
            }
        }
        val request: PutObjectRequest = PutObjectRequest(bucketName, targetKey, sourceFile)
            .withStorageClass(storageClass)
            .withMetadata(metadata)
            .withGeneralProgressListener(progressListener)
        val tm = TransferManagerBuilder.standard()
            .withS3Client(s3Client)
            .build()
        val upload = tm.upload(request)
        upload.waitForCompletion()
        tm.shutdownNow(false) // don't shut down the client itself as it might be reused for other transfers
    }

    @Throws(IOException::class)
    fun uploadObject(objectKeyName: String, plaintext: ByteArray): ByteArray {
        S3Object().use { obj ->
            obj.key = objectKeyName
            obj.setObjectContent(ByteArrayInputStream(plaintext))
            val metadata = ObjectMetadata()
            metadata.contentLength = plaintext.size.toLong()
            val putRequest = PutObjectRequest(
                bucketName, obj.key, obj.objectContent, metadata
            ).withStorageClass(StorageClass.Standard)
            s3Client.putObject(putRequest)
        }
        return plaintext
    }

    fun getMetadata(objectKeyName: String) {
        val metadata = s3Client.getObjectMetadata(bucketName, objectKeyName)
        println("Result: contenttype ${metadata.contentType}, lastModified ${metadata.lastModified}")
    }

    fun listAllObjects() {
        val listing = s3Client.listObjects(bucketName)
        listing.objectSummaries.forEach {
            val metadata = s3Client.getObjectMetadata(bucketName, it.key)
            println("found ${it.key}, contenttype ${metadata.contentType}, lastModified ${metadata.lastModified}")
        }
        println("Istruncated? --> ${listing.isTruncated}")
    }
}