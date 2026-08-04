package s3backup.commands

import s3backup.S3APIWrapper
import s3backup.S3ClientFactory

class DeleteCommand(
    private val s3Key: String
) : Runnable {
    override fun run() {
        val s3 = S3APIWrapper(S3ClientFactory.makePlaintextClient(useCredentials = true))
        s3.deleteObject(s3Key)
    }
}
