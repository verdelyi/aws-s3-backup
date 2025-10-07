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
        objects.forEach {
            when (format) {
                "NICE" -> println("Key: ${it.key()} (${(it.size() / 1e6).roundToInt()} MB, Storage class: ${it.storageClassAsString()})")
                "SIMPLE" -> println(it.key())
                else -> error("unknown format $format")
            }
        }
        println("Total: ${objects.size} objects found")
    }
}