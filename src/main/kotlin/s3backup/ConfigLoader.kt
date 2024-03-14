package s3backup

import java.io.FileInputStream
import java.util.*

object ConfigLoader {
    fun load(propertiesFile: String): Properties {
        println("Loading configuration properties from $propertiesFile")
        val config = Properties()
        FileInputStream(propertiesFile).use { config.load(it) }
        return config
    }
}