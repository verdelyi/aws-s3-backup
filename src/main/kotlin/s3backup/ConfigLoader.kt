package s3backup

import java.io.FileInputStream
import java.util.*

object ConfigLoader {
    fun load(): Properties {
        val propertiesFile = System.getProperty("config.properties.path") ?: "./config.properties"
        println("Loading configuration properties from $propertiesFile")
        val config = Properties()
        FileInputStream(propertiesFile).use { config.load(it) }
        return config
    }
}