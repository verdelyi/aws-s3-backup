package s3backup

import Utils
import s3backup.crypto.AWSEncryptionSDK
import s3backup.util.FolderZipper
import s3backup.util.copyToWithProgress
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.math.roundToInt


class S3APIWrapper(config: Properties, private val s3Client: S3Client) {
    private val bucketName: String = config.getProperty("aws.s3.bucketName")
    private val progressReportPerBytes: Long = 100_000_000
    private val temporaryZipFile = Paths.get(config.getProperty("config.temporaryZipFilePath"))
    private val temporaryEncryptedFile = Paths.get(config.getProperty("config.temporaryEncryptedFilePath"))
    private val masterKeyFile = Paths.get(config.getProperty("config.encryptionKeyFile"))

    object TagNames {
        const val encryption = "client-side-encryption"
    }

    private val dirFilter: (File) -> Boolean = { it.name !in listOf(".idea", ".gradle") }

    fun uploadFolder(
        fromLocalFolder: File, toRemoteFolder: String,
        storageClass: StorageClass = StorageClass.STANDARD,
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
        storageClass: StorageClass = StorageClass.STANDARD,
        encryption: Boolean
    ) {
        require(fromLocalFolder.isDirectory) { "${fromLocalFolder.absolutePath} must be a folder!" }
        println("Zipping into temporary zip file $temporaryZipFile)...")
        FolderZipper.pack(sourceDir = fromLocalFolder, zipFile = temporaryZipFile.toFile(), dirFilter = dirFilter)
        println("Uploading...")
        uploadFile(
            sourceFile = temporaryZipFile,
            targetKey = targetKey,
            storageClass = storageClass,
            encryption = encryption
        )
        Files.delete(temporaryZipFile) // clean up temporary zip file
    }

    fun downloadFile(sourceKey: String, targetFile: Path) {
        println("Downloading file [S3:/$sourceKey] -> [${targetFile}]...")
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(sourceKey)
            .build()
        val responseStream = s3Client.getObject(getObjectRequest)
        val isEncrypted = responseStream.response().metadata()[TagNames.encryption]!!.toBooleanStrict()
        println("Remote encryption status: $isEncrypted")
        responseStream.use { inStream ->
            if (isEncrypted) {
                val crypto = AWSEncryptionSDK.makeCryptoObject()
                val masterKey = AWSEncryptionSDK.loadKey(masterKeyFile)
                AWSEncryptionSDK.decryptFromStream(
                    crypto = crypto,
                    inStream = inStream,
                    outFile = targetFile,
                    masterKey = masterKey
                )
            } else {
                Files.newOutputStream(targetFile).use { fos ->
                    inStream.copyToWithProgress(out = fos,
                        reportPerBytes = 100_000_000,
                        onProgressEvent = { println("${Utils.bytesToGigabytes(it)} GB downloaded") }
                    )
                }
            }
        }
    }

    fun uploadFile(
        sourceFile: Path, targetKey: String, storageClass: StorageClass = StorageClass.STANDARD,
        encryption: Boolean
    ) {
        require(sourceFile.isRegularFile()) { "must be a file" }
        try {
            val objectRequest = HeadObjectRequest.builder().key(targetKey).bucket(bucketName).build()
            val objectHead: HeadObjectResponse = s3Client.headObject(objectRequest)
            println("Object '$targetKey' already exists -- will overwrite")
            println("    Last modified: ${objectHead.lastModified()}")
            println("    Client-side encryption: ${objectHead.metadata()[TagNames.encryption]}")
        } catch (e: NoSuchKeyException) {
            println("Object does not exist yet -- " + e.awsErrorDetails().errorMessage())
        }
        println("Uploading [$sourceFile] -> [S3:/$targetKey]... ")
        println(" -- File size: ${sourceFile.fileSize() / 1e6} MB")
        println(" -- S3 Storage class: ${storageClass.name}")
        println(" -- S3 client-side encryption: $encryption")
        val metadata = mapOf(
            TagNames.encryption to encryption.toString(),
        )
        if (encryption) {
            println(" -- Encrypting to ${temporaryEncryptedFile}...") // because we need to know the size of the ciphertext in advance...
            val crypto = AWSEncryptionSDK.makeCryptoObject()
            val masterKey = AWSEncryptionSDK.loadKey(masterKeyFile)
            AWSEncryptionSDK.encryptToFile(crypto, sourceFile, temporaryEncryptedFile, masterKey)
            uploadFileCore(
                sourceFile = temporaryEncryptedFile,
                targetKey = targetKey,
                storageClass = storageClass,
                metadata = metadata
            )
            Files.delete(temporaryEncryptedFile) // Clean up temporary encrypted file
        } else {
            println(" -- NOT encrypting")
            uploadFileCore(
                sourceFile = sourceFile, targetKey = targetKey, storageClass = storageClass, metadata = metadata
            )
        }
    }

    /** the new S3 transfer manager could support progress tracking, retries etc.
     * https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/transfer-manager.html
     */
    private fun uploadFileCore(
        sourceFile: Path,
        targetKey: String,
        storageClass: StorageClass,
        metadata: Map<String, String>
    ) {
        print(" -- Performing actual upload...")
        /*val progressListener = object : ProgressListener {
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
        }*/
        val objectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(targetKey)
            .storageClass(storageClass)
            .metadata(metadata)
            .checksumAlgorithm(ChecksumAlgorithm.SHA256)
            .build()
        s3Client.putObject(objectRequest, sourceFile)
        println("OK")
    }

    @Throws(IOException::class)
    fun uploadByteArray(objectKeyName: String, plaintext: ByteArray) {
        val objectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(objectKeyName)
            .storageClass(StorageClass.STANDARD)
            .build()
        s3Client.putObject(objectRequest, RequestBody.fromBytes(plaintext))
    }

    // See https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/pagination.html
    fun listAllObjects(prefix: String, format: String) {
        val listObjects = ListObjectsV2Request
            .builder()
            .bucket(bucketName)
            .prefix(prefix)
            .build()
        val res: ListObjectsV2Iterable = s3Client.listObjectsV2Paginator(listObjects)
        val objects = res.contents()
        objects.forEach {
            when (format) {
                "NICE" -> println("Key: ${it.key()} (${(it.size() / 1e6).roundToInt()} MB, Storage class: ${it.storageClassAsString()})")
                "SIMPLE" -> println(it.key())
                else -> error("unknown format $format")
            }
        }
        println("Total: ${objects.count()} objects found")
    }
}