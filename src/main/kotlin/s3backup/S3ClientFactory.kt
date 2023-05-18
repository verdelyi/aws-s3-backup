package s3backup

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.transfer.s3.SizeConstant.MB
import java.util.*


object S3ClientFactory {
    private val clientRegion = Region.AP_NORTHEAST_1

    private fun makeCredentials(config: Properties): AwsCredentialsProvider {
        val awsCreds = AwsBasicCredentials.create(
            config.getProperty("aws.accessKey"),
            config.getProperty("aws.secretKey")
        )
        return StaticCredentialsProvider.create(awsCreds)
    }

    fun makePlaintextClientWithCredentials(config: Properties): S3AsyncClient = S3AsyncClient.crtBuilder()
        .credentialsProvider(makeCredentials(config))
        .region(clientRegion)
        .targetThroughputInGbps(20.0)
        .minimumPartSizeInBytes(8 * MB)
        .build()

    fun makePlaintextClientWithoutCredentials(): S3AsyncClient = S3AsyncClient.crtBuilder()
        .region(clientRegion)
        .targetThroughputInGbps(20.0)
        .minimumPartSizeInBytes(8 * MB)
        .build()
}