package s3backup.commands

import s3backup.S3APIWrapper
import s3backup.S3ClientFactory

class ListCommand(
    private val prefix: String,
    private val format: String
) : Runnable {
    override fun run() {
        val s3 = S3APIWrapper(S3ClientFactory.makePlaintextClientWithCredentials())
        s3.listObjects(prefix = prefix, format = format)
    }
}