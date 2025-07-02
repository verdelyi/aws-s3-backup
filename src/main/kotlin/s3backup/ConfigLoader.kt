package s3backup

import java.io.FileInputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

object ConfigLoader {
    private lateinit var config: Properties
    
    fun load(propertiesFile: String) {
        println("Loading configuration properties from $propertiesFile")
        config = Properties()
        FileInputStream(propertiesFile).use { config.load(it) }
    }
    
    fun getProperty(key: String): String? = config.getProperty(key)
    
    fun getRequiredProperty(key: String): String = 
        config.getProperty(key) ?: error("Required property '$key' not found in config")
    
    fun getAwsAccessKey(): String = getRequiredProperty("aws.accessKey")
    fun getAwsSecretKey(): String = getRequiredProperty("aws.secretKey")
    fun getBucketName(): String = getRequiredProperty("aws.s3.bucketName")
    fun getEncryptionKeyFile(): Path = Paths.get(getRequiredProperty("config.encryptionKeyFile"))
    fun getTmpDir(): Path? = getProperty("config.tmpDir")?.let { Paths.get(it) }
}