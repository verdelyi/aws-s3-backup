import software.amazon.awssdk.checksums.DefaultChecksumAlgorithm
import software.amazon.awssdk.checksums.SdkChecksum
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
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

    // Reads a stream to its end, discarding the content: for zip entries and encrypted streams
    // the act of reading is what verifies the CRC32 / GCM auth tag, so the bytes aren't wanted.
    fun drain(input: InputStream) {
        val buffer = ByteArray(64 * 1024)
        while (input.read(buffer) != -1) {
            // discard
        }
    }

    // CRC64NVME is the one checksum algorithm S3 computes over the WHOLE object even for
    // multipart uploads (ChecksumType FULL_OBJECT), because CRCs are mathematically composable.
    // That makes this value directly comparable to what S3 reports, with no need to know or
    // replicate the part size the uploader chose. SHA256 etc. only yield per-part composite
    // checksums for multipart uploads, which are not comparable to a whole-file hash.
    fun crc64NvmeBase64(file: Path): String {
        val checksum = SdkChecksum.forAlgorithm(DefaultChecksumAlgorithm.CRC64NVME)
        Files.newInputStream(file).use { inStream ->
            val buffer = ByteArray(64 * 1024)
            var read = inStream.read(buffer)
            while (read != -1) {
                checksum.update(buffer, 0, read)
                read = inStream.read(buffer)
            }
        }
        return Base64.getEncoder().encodeToString(checksum.checksumBytes)
    }
}
