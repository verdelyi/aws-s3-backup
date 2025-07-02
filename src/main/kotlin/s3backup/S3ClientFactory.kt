package s3backup

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.transfer.s3.SizeConstant.MB


object S3ClientFactory {
    private val clientRegion = Region.AP_NORTHEAST_1

    private fun makeCredentials(): AwsCredentialsProvider {
        val awsCreds = AwsBasicCredentials.create(
            ConfigLoader.getAwsAccessKey(),
            ConfigLoader.getAwsSecretKey()
        )
        return StaticCredentialsProvider.create(awsCreds)
    }

    fun makePlaintextClientWithCredentials(): S3AsyncClient = S3AsyncClient.crtBuilder()
        .credentialsProvider(makeCredentials())
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