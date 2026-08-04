import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.pow

object Utils {

    private val workDir: Path by lazy {
        val dir = Paths.get(System.getProperty("user.home"), "tmp-${System.nanoTime()}")
        Files.createDirectories(dir)
        Runtime.getRuntime().addShutdownHook(Thread { dir.toFile().deleteRecursively() })
        dir
    }

    fun bytesToGigabytes(bytes: Long): Float {
        return bytes.toFloat() / 1024f.pow(3)
    }

    fun createTempFile(prefix: String, suffix: String): Path {
        return Files.createTempFile(workDir, prefix, suffix)
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