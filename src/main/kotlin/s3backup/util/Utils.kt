import s3backup.ConfigLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.pow

object Utils {

    fun bytesToGigabytes(bytes: Long): Float {
        return bytes.toFloat() / 1024f.pow(3)
    }

    fun createTempFile(prefix: String, suffix: String): Path {
        val tmpDir = ConfigLoader.getTmpDir()
        return if (tmpDir != null) {
            Files.createTempFile(tmpDir, prefix, suffix)
        } else {
            Files.createTempFile(prefix, suffix)
        }
    }

    fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append("%02x".format(b))
        return sb.toString()
    }

    fun hexToBytes(hex: String): ByteArray {
        val cleaned = hex.trim()
        require(cleaned.length % 2 == 0) { "Hex string must have an even length" }
        return ByteArray(cleaned.length / 2) { i ->
            cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}