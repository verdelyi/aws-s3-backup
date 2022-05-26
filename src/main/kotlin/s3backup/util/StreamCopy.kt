package s3backup.util

import java.io.InputStream
import java.io.OutputStream

/**
 * Copies this stream to the given output stream, returning the number of bytes copied
 *
 * **Note** It is the caller's responsibility to close both of these resources.
 */
fun InputStream.copyToWithProgress(out: OutputStream, reportPerBytes: Int, onProgressEvent: (totalBytesCopied: Long) -> Any): Long {
    var bytesCopied: Long = 0
    val buffer = ByteArray(1_000_000)
    var bytes = read(buffer)
    var bytesCopiedAtLastReport: Long = 0
    while (bytes >= 0) {
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        if (bytesCopied - bytesCopiedAtLastReport > reportPerBytes) {
            onProgressEvent(bytesCopied)
            bytesCopiedAtLastReport = bytesCopied
        }
        bytes = read(buffer)
    }
    return bytesCopied
}