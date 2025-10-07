import s3backup.ConfigLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.pow

object Utils {

    fun bytesToGigabytes(bytes: Long): Float {
        return bytes.toFloat() / 1024f.pow(3)
    }

    fun parseEncryptField(s: String): Boolean {
        return when (s) {
            "encrypt" -> true
            "plaintext" -> false
            else -> throw IllegalArgumentException("encrypt parameter '$s' unparseable")
        }
    }

    fun createTempFile(prefix: String, suffix: String): Path {
        val tmpDir = ConfigLoader.getTmpDir()
        return if (tmpDir != null) {
            Files.createTempFile(tmpDir, prefix, suffix)
        } else {
            Files.createTempFile(prefix, suffix)
        }
    }
}