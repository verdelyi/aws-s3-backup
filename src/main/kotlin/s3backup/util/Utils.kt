import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.math.pow

object Utils {
    fun computeFileHash(file: Path): String {
        val md = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { inputStream ->
            DigestInputStream(inputStream, md).use { dis ->
                val buf = ByteArray(1000)
                @Suppress("ControlFlowWithEmptyBody")
                while (dis.read(buf) != -1) {
                }
            }
        }
        val digest = md.digest()
        return BigInteger(1, digest).toString(16)
    }

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
}