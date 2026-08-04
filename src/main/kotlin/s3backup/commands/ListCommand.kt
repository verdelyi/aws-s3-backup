package s3backup.commands

import s3backup.S3APIWrapper
import s3backup.S3ClientFactory
import kotlin.math.roundToInt

class ListCommand(
    private val prefix: String,
    private val format: String
) : Runnable {
    override fun run() {
        val s3 = S3APIWrapper(S3ClientFactory.makePlaintextClient(useCredentials = true))
        val objects = s3.getObjectKeys(prefix)
        var problems = 0
        if (format == "CHECK") {
            println("Checking ${if (prefix.isEmpty()) "all objects" else "objects under '$prefix'"} for:")
            println("  - the '${S3APIWrapper.TagNames.encryption}' flag, without which a restore cannot tell")
            println("    whether to decrypt (lost by copies that don't preserve source settings)")
            println("  - a stored CRC64NVME checksum, recorded at upload time to verify integrity")
            println("Object contents are not downloaded, so this does not detect damaged data.")
            println()
        }
        objects.forEach {
            when (format) {
                "NICE" -> println("Key: ${it.key()} (${(it.size() / 1e6).roundToInt()} MB, Storage class: ${it.storageClassAsString()})")
                "SIMPLE" -> println(it.key())
                // Reports whether each object still carries what a restore needs, without downloading it.
                "CHECK" -> if (!checkObject(s3, it.key(), (it.size() / 1e6).roundToInt())) problems++
                else -> error("unknown format $format")
            }
        }
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
        val checksum = head.checksumCRC64NVME()
        val issues = buildList {
            if (encryptionFlag == null) add("no '${S3APIWrapper.TagNames.encryption}' metadata (restore cannot tell if it is encrypted)")
            if (checksum == null) add("no stored CRC64NVME checksum")
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