package s3backup

import s3backup.crypto.AWSEncryptionSDK
import s3backup.util.FolderZipper
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Publisher
import software.amazon.awssdk.transfer.s3.S3TransferManager
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest
import software.amazon.awssdk.transfer.s3.progress.LoggingTransferListener
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CompletionException
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.math.roundToInt


class S3APIWrapper(config: Properties, private val s3AsyncClient: S3AsyncClient) {
    private val bucketName: String = config.getProperty("aws.s3.bucketName")
    private val masterKeyFile = Paths.get(config.getProperty("config.encryptionKeyFile"))
    private val progressBarNumTicks = 30

    object TagNames {
        const val encryption = "client-side-encryption"
    }

    private val dirFilter: (File) -> Boolean = { it.name !in listOf(".idea", ".gradle") }

    private val transferManager = S3TransferManager.builder().s3Client(s3AsyncClient).build()

    // could try to use the new features in transfermanager for this
    // https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/transfer-manager.html
    fun uploadFolder(
        fromLocalFolder: File, toRemoteFolder: String,
        storageClass: StorageClass,
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

    // could try to use the new features in transfermanager for this
    // https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/transfer-manager.html
    fun uploadFolderAsZip(
        fromLocalFolder: File, targetKey: String,
        storageClass: StorageClass,
        encryption: Boolean
    ) {
        require(fromLocalFolder.isDirectory) { "${fromLocalFolder.absolutePath} must be a folder!" }
        val temporaryZipFile = Files.createTempFile("s3backup-zip-", ".zip")
        try {
            println("Zipping into temporary zip file $temporaryZipFile)...")
            FolderZipper.pack(sourceDir = fromLocalFolder, zipFile = temporaryZipFile.toFile(), dirFilter = dirFilter)
            uploadFile(
                sourceFile = temporaryZipFile,
                targetKey = targetKey,
                storageClass = storageClass,
                encryption = encryption
            )
        } finally {
            Files.deleteIfExists(temporaryZipFile) // clean up temporary zip file
        }
    }

    fun downloadFile(sourceKey: String, targetFile: Path) {
        println("Downloading...")
        println(" -- Source: S3 bucket [$bucketName], key [$sourceKey]")
        val temporaryEncryptedFile = Files.createTempFile("s3backup-download-", ".tmp")
        println(" -- Destination: local file [${temporaryEncryptedFile}]")
        try {
            val downloadFileRequest = DownloadFileRequest.builder()
                .getObjectRequest { b: GetObjectRequest.Builder -> b.bucket(bucketName).key(sourceKey) }
                .addTransferListener(LoggingTransferListener.create(progressBarNumTicks))
                .destination(temporaryEncryptedFile)
                .build()
            val downloadFile = transferManager.downloadFile(downloadFileRequest)
            val downloadResult = downloadFile.completionFuture().join()

            val isEncrypted = downloadResult.response().metadata()[TagNames.encryption]!!.toBooleanStrict()
            Files.newInputStream(temporaryEncryptedFile).use { inStream ->
                if (isEncrypted) {
                    println(" -- File is encrypted. Decrypting to $targetFile...")
                    val crypto = AWSEncryptionSDK.makeCryptoObject()
                    val masterKey = AWSEncryptionSDK.makeKeyRingFromKeyFile(masterKeyFile)
                    AWSEncryptionSDK.decryptFromStream(
                        crypto = crypto, inStream = inStream, outFile = targetFile, masterKey = masterKey
                    )
                    println("Decryption done, deleting temp file.")
                } else {
                    println("-- File is not encrypted. Moving it to $targetFile...")
                    Files.move(temporaryEncryptedFile, targetFile)
                }
            }
        } finally {
            Files.deleteIfExists(temporaryEncryptedFile) // Clean up temporary encrypted file
        }
    }

    fun uploadFile(sourceFile: Path, targetKey: String, storageClass: StorageClass, encryption: Boolean) {
        require(sourceFile.isRegularFile()) { "must be a file" }
        try {
            val objectRequest = HeadObjectRequest.builder().key(targetKey).bucket(bucketName).build()
            val objectHead: HeadObjectResponse = s3AsyncClient.headObject(objectRequest).join()
            println("Object '$targetKey' already exists (Last modified: ${objectHead.lastModified()}) -- will overwrite")
        } catch (e: CompletionException) {
            if (e.cause is NoSuchKeyException) {
                val ce = e.cause as NoSuchKeyException
                println("Object does not exist yet -- " + ce.awsErrorDetails().errorMessage())
            } else {
                throw e
            }
        }
        println(
            "Uploading $sourceFile (${String.format("%.1f", sourceFile.fileSize() / 1_000_000.0)} MB) " +
                    "to S3->$bucketName->$targetKey (S3 Storage class: ${storageClass.name}, client-side encryption: $encryption)"
        )
        val metadata = mapOf(TagNames.encryption to encryption.toString())
        if (encryption) {
            val temporaryEncryptedFile = Files.createTempFile("s3backup-encrypted-", ".tmp")
            try {
                println(" -- Encrypting to ${temporaryEncryptedFile}...") // because we need to know the size of the ciphertext in advance...
                val crypto = AWSEncryptionSDK.makeCryptoObject()
                val masterKey = AWSEncryptionSDK.makeKeyRingFromKeyFile(masterKeyFile)
                AWSEncryptionSDK.encryptToFile(crypto, sourceFile, temporaryEncryptedFile, masterKey)
                uploadFileCore(
                    sourceFile = temporaryEncryptedFile,
                    targetKey = targetKey,
                    storageClass = storageClass,
                    metadata = metadata
                )
            } finally {
                Files.deleteIfExists(temporaryEncryptedFile) // Clean up temporary encrypted file
            }
        } else {
            println(" -- NOT encrypting")
            uploadFileCore(
                sourceFile = sourceFile, targetKey = targetKey, storageClass = storageClass, metadata = metadata
            )
        }
    }

    private fun uploadFileCore(sourceFile: Path, targetKey: String, storageClass: StorageClass, metadata: Map<String, String>) {
        val uploadFileRequest = UploadFileRequest.builder()
            .putObjectRequest { b: PutObjectRequest.Builder ->
                b.bucket(bucketName)
                    .key(targetKey)
                    .storageClass(storageClass)
                    .metadata(metadata)
                    .checksumAlgorithm(ChecksumAlgorithm.SHA256)
            }
            .addTransferListener(LoggingTransferListener.create(progressBarNumTicks))
            .source(sourceFile)
            .build()
        val fileUpload = transferManager.uploadFile(uploadFileRequest)
        val uploadResult = fileUpload.completionFuture().join()
        println("Upload done, received etag: ${uploadResult.response().eTag()}")
    }

    @Throws(IOException::class)
    fun uploadByteArray(objectKeyName: String, plaintext: ByteArray) {
        val objectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(objectKeyName)
            .storageClass(StorageClass.STANDARD)
            .build()
        s3AsyncClient.putObject(objectRequest, AsyncRequestBody.fromBytes(plaintext)).get()
    }

    // Pagination needed to get more than 1000 objects.
    // See https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/pagination.html
    fun listObjects(prefix: String, format: String) {
        val listObjects = ListObjectsV2Request
            .builder()
            .bucket(bucketName)
            .prefix(prefix)
            .build()
        val res: ListObjectsV2Publisher = s3AsyncClient.listObjectsV2Paginator(listObjects)
        var count = 0
        res.contents().subscribe {
            when (format) {
                "NICE" -> println("Key: ${it.key()} (${(it.size() / 1e6).roundToInt()} MB, Storage class: ${it.storageClassAsString()})")
                "SIMPLE" -> println(it.key())
                else -> error("unknown format $format")
            }
            count++
        }.get()
        println("Total: ${count} objects found")
    }
}