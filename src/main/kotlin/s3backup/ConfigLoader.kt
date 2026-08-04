package s3backup

import Utils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Serializable
data class AwsConfig(
    val accessKey: String? = null,
    val secretKey: String? = null,
    val bucketName: String
)

@Serializable
data class BatchItem(
    val command: String,
    val localPath: String,
    val remoteAWSPath: String,
    val encrypt: Boolean = false
)

@Serializable
data class Config(
    val aws: AwsConfig,
    val encryptionKeyHex: String,
    val tmpDir: String? = null,
    val batchItems: List<BatchItem> = emptyList()
)

object ConfigLoader {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var config: Config

    fun load(configFile: String) {
        println("Loading configuration from $configFile")
        config = json.decodeFromString(Files.readString(Paths.get(configFile)))
    }

    fun getAwsAccessKey(): String = config.aws.accessKey ?: error("'aws.accessKey' not set in config")
    fun getAwsSecretKey(): String = config.aws.secretKey ?: error("'aws.secretKey' not set in config")
    fun getBucketName(): String = config.aws.bucketName
    fun getEncryptionKeyBytes(): ByteArray = Utils.hexToBytes(config.encryptionKeyHex)
    fun getTmpDir(): Path? = config.tmpDir?.let { Paths.get(it) }
    fun getBatchItems(): List<BatchItem> = config.batchItems
}
