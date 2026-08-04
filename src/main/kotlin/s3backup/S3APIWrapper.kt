package s3backup

import Utils
import s3backup.crypto.AWSEncryptionSDK
import s3backup.util.FolderZipper
import s3backup.util.SimpleProgressListener
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Publisher
import software.amazon.awssdk.transfer.s3.S3TransferManager
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletionException
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile


class S3APIWrapper(private val s3AsyncClient: S3AsyncClient) {
    private val bucketName: String = ConfigLoader.getBucketName()
    private val encryptionKeyBytes = ConfigLoader.getEncryptionKeyBytes()

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
        val temporaryZipFile = Utils.createTempFile("s3backup-zip-", ".zip")
        try {
            println("  Zipping folder into temporary file: $temporaryZipFile")
            FolderZipper.pack(sourceDir = fromLocalFolder, zipFile = temporaryZipFile.toFile(), dirFilter = dirFilter)
            FolderZipper.validate(temporaryZipFile.toFile())
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

    // Metadata, checksum and storage class in one request, without transferring the object itself.
    fun headObject(key: String): HeadObjectResponse = s3AsyncClient.headObject(
        HeadObjectRequest.builder().bucket(bucketName).key(key).checksumMode(ChecksumMode.ENABLED).build()
    ).join()

    fun downloadFile(sourceKey: String, targetFile: Path) {
        val temporaryEncryptedFile = Utils.createTempFile("s3backup-download-", ".tmp")
        println("Downloading S3 bucket '$bucketName', key '$sourceKey' to local file '${temporaryEncryptedFile}'...")
        try {
            val downloadFileRequest = DownloadFileRequest.builder()
                .getObjectRequest { b: GetObjectRequest.Builder -> b.bucket(bucketName).key(sourceKey) }
                .addTransferListener(SimpleProgressListener())
                .destination(temporaryEncryptedFile)
                .build()
            val downloadFile = transferManager.downloadFile(downloadFileRequest)
            val downloadResult = try {
                downloadFile.completionFuture().join()
            } catch (e: CompletionException) {
                val cause = e.cause
                if (cause is S3Exception && cause.statusCode() == 404) {
                    throw IllegalStateException(
                        "No object named '$sourceKey' in bucket '$bucketName' -- check the key " +
                                "(run LIST to see what is actually there)"
                    )
                }
                throw e
            }

            // Every object we upload records this. Guessing when it's absent risks handing back a
            // still-encrypted file as if it were plaintext, so refuse instead. A missing flag
            // usually means the metadata was dropped, e.g. by an S3 copy that didn't carry it over.
            val isEncrypted = checkNotNull(downloadResult.response().metadata()[TagNames.encryption]) {
                "Object '$sourceKey' has no '${TagNames.encryption}' metadata, so it is unknown whether " +
                        "it is encrypted. The metadata was probably lost by a copy/storage-class change " +
                        "that did not preserve source settings."
            }.toBooleanStrict()

            Files.newInputStream(temporaryEncryptedFile).use { inStream ->
                if (isEncrypted) {
                    println(" -- File is encrypted. Decrypting to $targetFile...")
                    val crypto = AWSEncryptionSDK.makeCryptoObject()
                    val masterKey = AWSEncryptionSDK.makeKeyRingFromRawKey(encryptionKeyBytes)
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
            println("  Overwriting existing object (last modified: ${objectHead.lastModified()})")
        } catch (e: CompletionException) {
            if (e.cause is NoSuchKeyException) {
                println("  Object does not exist yet, creating new")
            } else {
                throw e
            }
        }
        val sizeMB = String.format("%.1f", sourceFile.fileSize() / 1_000_000.0)
        println("  Uploading: $sourceFile ($sizeMB MB)")
        println("  Destination: s3://$bucketName/$targetKey (class: ${storageClass.name}, encrypted: $encryption)")
        val metadata = mapOf(TagNames.encryption to encryption.toString())
        if (encryption) {
            val temporaryEncryptedFile = Utils.createTempFile("s3backup-encrypted-", ".tmp")
            try {
                // need the ciphertext file to know its size in advance
                println("  Encrypting to temporary file: $temporaryEncryptedFile")
                val crypto = AWSEncryptionSDK.makeCryptoObject()
                val masterKey = AWSEncryptionSDK.makeKeyRingFromRawKey(encryptionKeyBytes)
                AWSEncryptionSDK.encryptToFile(crypto, sourceFile, temporaryEncryptedFile, masterKey)
                AWSEncryptionSDK.verifyByDecrypting(crypto, temporaryEncryptedFile, masterKey)
                println("  Verified encrypted file decrypts correctly")
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
                    // CRC64NVME (unlike SHA256) is computed by S3 over the whole object even for
                    // multipart uploads, so the response is directly comparable to a local digest.
                    .checksumAlgorithm(ChecksumAlgorithm.CRC64_NVME)
            }
            .addTransferListener(SimpleProgressListener())
            .source(sourceFile)
            .build()
        val fileUpload = transferManager.uploadFile(uploadFileRequest)
        val uploadResult = fileUpload.completionFuture().join()
        val etag = uploadResult.response().eTag()
        // The upload response doesn't always carry the checksum (it depends on how the upload was
        // routed), so fall back to asking S3 what it actually stored.
        val remoteChecksum = uploadResult.response().checksumCRC64NVME() ?: fetchStoredChecksum(targetKey)
        val localChecksum = Utils.crc64NvmeBase64(sourceFile)
        // An unverifiable upload counts as a failure: a backup we can't check isn't one we can trust.
        check(remoteChecksum == localChecksum) {
            "Checksum verification failed for $targetKey: local=$localChecksum remote=$remoteChecksum"
        }
        println("  Done (etag: $etag, crc64nvme verified: $remoteChecksum)")
    }

    // Multipart uploads don't report their checksum in the upload response, so read back what S3
    // stored. Failures are rethrown as IllegalStateException so they abort the run rather than
    // being swallowed by Main's handling of AWS exceptions.
    private fun fetchStoredChecksum(targetKey: String): String? = try {
        s3AsyncClient.getObjectAttributes(
            GetObjectAttributesRequest.builder()
                .bucket(bucketName).key(targetKey)
                .objectAttributes(ObjectAttributes.CHECKSUM)
                .build()
        ).join().checksum()?.checksumCRC64NVME()
    } catch (e: Exception) {
        throw IllegalStateException("Could not read back the stored checksum for $targetKey to verify the upload", e)
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

    fun deleteObject(key: String) {
        val deleteRequest = DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build()
        s3AsyncClient.deleteObject(deleteRequest).join()
        println("Deleted S3->$bucketName->$key")
    }

    // Pagination needed to get more than 1000 objects.
    // See https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/pagination.html
    fun getObjectKeys(prefix: String): List<S3Object> {
        val listObjects = ListObjectsV2Request
            .builder()
            .bucket(bucketName)
            .prefix(prefix)
            .build()
        val res: ListObjectsV2Publisher = s3AsyncClient.listObjectsV2Paginator(listObjects)
        val objects = mutableListOf<S3Object>()
        res.contents().subscribe { objects.add(it) }.get()
        return objects
    }
}