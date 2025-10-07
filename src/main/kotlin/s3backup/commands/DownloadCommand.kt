package s3backup.commands

import s3backup.S3APIWrapper
import s3backup.S3ClientFactory
import java.nio.file.Files
import java.nio.file.Paths

class DownloadCommand(
    private val s3SourceKey: String,
    private val targetDir: String
) : Runnable {
    override fun run() {
        val s3 = S3APIWrapper(S3ClientFactory.makePlaintextClient(useCredentials = true))
        val targetDirPath = Paths.get(targetDir)
        Files.createDirectories(targetDirPath)

        if (s3SourceKey.endsWith("*")) {
            // Download multiple files with prefix
            val prefix = s3SourceKey.removeSuffix("*")
            println("Wildcard detected -- interpreting key as prefix...")
            val objects = s3.getObjectKeys(prefix)
            println("Found ${objects.size} objects to download")

            objects.forEachIndexed { index, s3Object ->
                val key = s3Object.key()
                println("\n=== Downloading ${index + 1}/${objects.size}: $key ===")
                try {
                    val targetFile = targetDirPath.resolve(key)
                    Files.createDirectories(targetFile.parent)
                    s3.downloadFile(sourceKey = key, targetFile = targetFile)
                } catch (e: Exception) {
                    println("ERROR downloading $key: ${e.message}")
                    e.printStackTrace()
                }
            }
            println("\n=== Download batch completed: ${objects.size} files ===")
        } else {
            // Download single file
            s3.downloadFile(
                sourceKey = s3SourceKey,
                targetFile = targetDirPath.resolve(s3SourceKey.split("/").last())
            )
            println("=== Download completed ===")
        }
    }
}