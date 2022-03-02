package s3backup

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Regions
import com.amazonaws.services.kms.AWSKMS
import com.amazonaws.services.kms.AWSKMSClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.AmazonS3EncryptionClientV2Builder
import com.amazonaws.services.s3.model.CryptoConfigurationV2
import com.amazonaws.services.s3.model.CryptoMode
import com.amazonaws.services.s3.model.EncryptionMaterials
import com.amazonaws.services.s3.model.StaticEncryptionMaterialsProvider
import java.io.File
import java.util.*

object S3ClientFactory {
    private val clientRegion = Regions.AP_NORTHEAST_1

    private fun makeCredentials(config: Properties): AWSCredentialsProvider {
        return AWSStaticCredentialsProvider(
            BasicAWSCredentials(config.getProperty("aws.accessKey"), config.getProperty("aws.secretKey")))
    }

    fun makePlaintextClientWithCredentials(config: Properties): AmazonS3 = AmazonS3ClientBuilder.standard()
        .withCredentials(makeCredentials(config))
        .withRegion(clientRegion)
        .build()

    fun makePlaintextClientWithoutCredentials(): AmazonS3 = AmazonS3ClientBuilder.standard()
        .withRegion(clientRegion)
        .build()

    /*fun makeEncryptionClientWithCredentials_OLD(config: Properties): AmazonS3 {
        val keyPair = KeyOps.loadKeyPair(
            publicKeyFile = File(config.getProperty("config.publicKeyFile")),
            privateKeyFile = File(config.getProperty("config.privateKeyFile")),
            algorithm = "RSA")
        return AmazonS3EncryptionClientBuilder.standard()
            .withCredentials(makeCredentials(config))
            .withEncryptionMaterials(StaticEncryptionMaterialsProvider(EncryptionMaterials(keyPair)))
            .withRegion(clientRegion)
            .build()
    }*/

    fun makeEncryptionClientWithCredentials(config: Properties): AmazonS3 {
        val kmsClient: AWSKMS = AWSKMSClientBuilder.standard().withRegion(clientRegion).build()
        val keyPair = KeyOps.loadKeyPair(
            publicKeyFile = File(config.getProperty("config.publicKeyFile")),
            privateKeyFile = File(config.getProperty("config.privateKeyFile")),
            algorithm = "RSA")
        return AmazonS3EncryptionClientV2Builder.standard()
            .withKmsClient(kmsClient) // workaround for issue https://github.com/aws/aws-sdk-java/issues/2530
            .withCredentials(makeCredentials(config))
            .withEncryptionMaterialsProvider(StaticEncryptionMaterialsProvider(EncryptionMaterials(keyPair)))
            // The following setting allows the client to read V1 encrypted objects
            .withCryptoConfiguration(CryptoConfigurationV2().withCryptoMode(CryptoMode.AuthenticatedEncryption))
            .withRegion(clientRegion)
            .build()
    }
}