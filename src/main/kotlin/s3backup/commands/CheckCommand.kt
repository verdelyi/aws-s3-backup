package s3backup.commands

import s3backup.S3APIWrapper
import s3backup.S3ClientFactory
import kotlin.math.roundToInt

/**
 * Reports whether each object still carries what a restore needs, using one HeadObject per key
 * so that no object contents are transferred.
 */
class CheckCommand(
    private val prefix: String
) : Runnable {
    override fun run() {
        val s3 = S3APIWrapper(S3ClientFactory.makePlaintextClient(useCredentials = true))
        println("Checking ${if (prefix.isEmpty()) "all objects" else "objects under '$prefix'"} for:")
        println("  - the '${S3APIWrapper.TagNames.encryption}' flag, without which a restore cannot tell")
        println("    whether to decrypt (lost by copies that don't preserve source settings)")
        println("  - a stored CRC64NVME checksum, recorded at upload time to verify integrity")
        println("Object contents are not downloaded, so this does not detect damaged data.")
        println()

        val objects = s3.getObjectKeys(prefix)
        val problems = objects.count { !checkObject(s3, it.key(), (it.size() / 1e6).roundToInt()) }
        println("Total: ${objects.size} objects found")
        check(problems == 0) { "$problems of ${objects.size} objects have problems (see above)" }
    }

    private fun checkObject(s3: S3APIWrapper, key: String, sizeMB: Int): Boolean {
        val head = try {
            s3.headObject(key)
        } catch (e: Exception) {
            println("PROBLEM  $key -- could not read metadata: ${e.message}")
            return false
        }
        val encryptionFlag = head.metadata()[S3APIWrapper.TagNames.encryption]
        val issues = buildList {
            if (encryptionFlag == null) add("no '${S3APIWrapper.TagNames.encryption}' metadata (restore cannot tell if it is encrypted)")
            if (head.checksumCRC64NVME() == null) add("no stored CRC64NVME checksum")
        }
        val details = "$sizeMB MB, ${head.storageClassAsString() ?: "STANDARD"}, encrypted=$encryptionFlag"
        return if (issues.isEmpty()) {
            println("OK       $key ($details)")
            true
        } else {
            println("PROBLEM  $key ($details) -- ${issues.joinToString("; ")}")
            false
        }
    }
}
