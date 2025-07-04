package s3backup.commands

import s3backup.S3APIWrapper
import s3backup.S3ClientFactory
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.core.exception.SdkClientException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

class DownloadCommand(
    private val s3SourceKey: String,
    private val targetDir: String
) : Runnable {
    override fun run() {
        try {
            val s3 = S3APIWrapper(S3ClientFactory.makePlaintextClientWithCredentials())
            val targetDirPath = Paths.get(targetDir)
            Files.createDirectories(targetDirPath)
            s3.downloadFile(
                sourceKey = s3SourceKey,
                targetFile = targetDirPath.resolve(s3SourceKey.split("/").last())
            )
            println("=== Download completed ===")
        } catch (e: AwsServiceException) {
            // The call was transmitted successfully, but Amazon S3 couldn't process it, so it returned an error response.
            e.printStackTrace()
        } catch (e: SdkClientException) {
            // Amazon S3 couldn't be contacted for a response, or the client couldn't parse the response from Amazon S3.
            e.printStackTrace()
        }
    }
}