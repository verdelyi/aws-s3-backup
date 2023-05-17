package s3backup.commands

import s3backup.S3APIWrapper
import s3backup.S3ClientFactory
import java.util.*

class ListCommand(
    private val config: Properties,
    private val prefix: String,
    private val format: String
) : Runnable {
    override fun run() {
        val s3 = S3APIWrapper(config = config, s3Client = S3ClientFactory.makePlaintextClientWithCredentials(config))
        s3.listAllObjects(prefix = prefix, format = format)
    }
}