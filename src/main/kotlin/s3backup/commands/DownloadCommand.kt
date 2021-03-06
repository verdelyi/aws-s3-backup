package s3backup.commands

import com.amazonaws.AmazonServiceException
import com.amazonaws.SdkClientException
import s3backup.S3APIWrapper
import s3backup.S3ClientFactory
import java.io.File
import java.util.*

class DownloadCommand(
    private val config: Properties,
    private val s3SourceKey: String,
    private val targetDir: String
) : Runnable {
    override fun run() {
        try {
            // files COULD be encrypted, so use the encryption client
            val s3 = S3APIWrapper(
                config = config,
                s3Client = S3ClientFactory.makeEncryptionClientWithCredentials(config)
            )
            s3.downloadFile(sourceKey = s3SourceKey, targetFile = File(targetDir, s3SourceKey.split("/").last()))
            println("=== Download completed ===")
        } catch (e: AmazonServiceException) {
            // The call was transmitted successfully, but Amazon S3 couldn't process it, so it returned an error response.
            e.printStackTrace()
        } catch (e: SdkClientException) {
            // Amazon S3 couldn't be contacted for a response, or the client couldn't parse the response from Amazon S3.
            e.printStackTrace()
        }
    }
}