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
import s3backup.crypto.AWSEncryptionSDK
import s3backup.util.FolderZipper
import s3backup.util.copyToWithProgress
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.isRegularFile
import kotlin.math.roundToInt

class S3APIWrapper(config: Properties, private val s3Client: AmazonS3) {
    private val bucketName: String = config.getProperty("aws.s3.bucketName")
    private val progressReportPerBytes: Long = 100_000_000
    private val temporaryFile = Paths.get(config.getProperty("config.temporaryFilePath"))
    private val masterKeyFile = Paths.get(config.getProperty("config.encryptionKeyFile"))

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
                    sourceFile = file.toPath(),
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
        println("Zipping into temporary zip file $temporaryFile)...")
        FolderZipper.pack(sourceDir = fromLocalFolder, zipFile = temporaryFile.toFile(), dirFilter = dirFilter)
        println("Uploading...")
        uploadFile(sourceFile = temporaryFile, targetKey = targetKey, storageClass = storageClass, encryption = encryption)
        Files.delete(temporaryFile)
    }

    fun downloadFile(sourceKey: String, targetFile: File) {
        println("Downloading file [S3:/$sourceKey] -> [${targetFile.absolutePath}]...")
        val metadata = s3Client.getObjectMetadata(bucketName, sourceKey)
        val isEncrypted = metadata.getUserMetaDataOf(TagNames.encryption).toBooleanStrict()
        println("Remote encryption status: $isEncrypted")

        s3Client.getObject(bucketName, sourceKey).use { o ->
            o.objectContent.use { inStream ->
                if (isEncrypted) {
                    val crypto = AWSEncryptionSDK.makeCryptoObject()
                    val masterKey = AWSEncryptionSDK.loadKey(masterKeyFile)
                    AWSEncryptionSDK.decryptFromStream(
                        crypto = crypto,
                        inStream = inStream,
                        outFile = targetFile.toPath(),
                        masterKey = masterKey
                    )
                } else {
                    val fos = FileOutputStream(targetFile)
                    inStream.copyToWithProgress(out = fos,
                        reportPerBytes = 100_000_000,
                        onProgressEvent = { println("${Utils.bytesToGigabytes(it)} GB downloaded") }
                    )
                }
            }
        }
    }

    fun uploadFile(
        sourceFile: Path, targetKey: String, storageClass: StorageClass = StorageClass.Standard,
        encryption: Boolean
    ) {
        require(sourceFile.isRegularFile()) { "must be a file" }
        println("Comparing local and remote file hashes...")
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
            "Uploading [$sourceFile] -> [S3:/$targetKey]... " +
                    "(Storage class: ${storageClass.name}, Encryption: $encryption)"
        )
        val metadata = ObjectMetadata().apply {
            addUserMetadata(TagNames.encryption, encryption.toString())
            addUserMetadata(TagNames.hash, localFileHash)
        }
        if (encryption) {
            println(" -- Encrypting to $temporaryFile...") // because we need to know the size of the ciphertext in advance...
            val crypto = AWSEncryptionSDK.makeCryptoObject()
            val masterKey = AWSEncryptionSDK.loadKey(masterKeyFile)
            AWSEncryptionSDK.encryptToFile(crypto, sourceFile, temporaryFile, masterKey)
            uploadFileCore(sourceFile = temporaryFile, targetKey = targetKey, storageClass = storageClass, metadata = metadata)
            Files.delete(temporaryFile) // Clean up temporary encrypted file
        } else {
            println(" -- NOT encrypting")
            uploadFileCore(sourceFile = sourceFile, targetKey = targetKey, storageClass = storageClass, metadata = metadata)
        }
    }

    private fun uploadFileCore(sourceFile: Path, targetKey: String, storageClass: StorageClass, metadata: ObjectMetadata) {
        println(" -- Performing actual upload...")
        val progressListener = object : ProgressListener {
            var byteCounter: Long = 0
            var bytesAtLastReport: Long = 0
            override fun progressChanged(progressEvent: ProgressEvent) {
                //println("Progress event: ${progressEvent.eventType}")
                byteCounter += progressEvent.bytesTransferred
                if (byteCounter - bytesAtLastReport > progressReportPerBytes) {
                    println("$sourceFile: ${Utils.bytesToGigabytes(byteCounter)} GB transferred")
                    bytesAtLastReport = byteCounter
                }
            }
        }
        val request: PutObjectRequest = PutObjectRequest(bucketName, targetKey, sourceFile.toFile())
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
    fun uploadByteArray(objectKeyName: String, plaintext: ByteArray): ByteArray {
        S3Object().use { obj ->
            obj.key = objectKeyName
            obj.setObjectContent(ByteArrayInputStream(plaintext))
            val metadata = ObjectMetadata()
            metadata.contentLength = plaintext.size.toLong()
            val putRequest = PutObjectRequest(bucketName, obj.key, obj.objectContent, metadata).withStorageClass(StorageClass.Standard)
            s3Client.putObject(putRequest)
        }
        return plaintext
    }

    fun getMetadata(objectKeyName: String) {
        val metadata = s3Client.getObjectMetadata(bucketName, objectKeyName)
        println("Result: contenttype ${metadata.contentType}, lastModified ${metadata.lastModified}")
    }

    fun listAllObjects(prefix: String) {
        val listing = s3Client.listObjects(bucketName, prefix)
        listing.objectSummaries.forEach {
            //val metadata = s3Client.getObjectMetadata(bucketName, it.key)
            println("Key: ${it.key} (${(it.size / 1e6).roundToInt()} MB, Storage class: ${it.storageClass})")
        }
        println("Total: ${listing.objectSummaries.size} objects found")
        check(!listing.isTruncated) {
            "FIXME: RESULTS ARE TRUNCATED. USE s3Client.listNextBatchOfObjects(listing) TO GET MORE."
        }
    }
}