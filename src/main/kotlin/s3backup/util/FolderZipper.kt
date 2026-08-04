package s3backup.util

import Utils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FolderZipper {
    @Throws(IOException::class)
    fun pack(sourceDir: File, zipFile: File, dirFilter: (File) -> Boolean) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zs ->
            zs.setLevel(Deflater.BEST_SPEED)
            var counter = 0
            sourceDir.walkTopDown()
                .onEnter(dirFilter)
                .filter { file -> !file.isDirectory }
                .forEach { file ->
                    val attr = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
                    val zipEntry = ZipEntry(file.relativeTo(sourceDir).toString()).apply {
                        lastAccessTime = attr.lastAccessTime()
                        creationTime = attr.creationTime()
                        lastModifiedTime = attr.lastModifiedTime()
                    }
                    zs.putNextEntry(zipEntry)
                    Files.copy(file.toPath(), zs)
                    zs.closeEntry()
                    counter++
                    if (counter % 1000 == 0) println("Added $counter files")
                }
        }
    }

    // Reading each entry fully forces java.util.zip to check its CRC32 against the value recorded
    // in the archive, throwing ZipException on mismatch -- catches corruption from the packing step.
    @Throws(IOException::class)
    fun validate(zipFile: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var count = 0
            while (zis.nextEntry != null) {
                Utils.drain(zis)
                count++
            }
            println("  Verified $count zip entries (CRC32 OK)")
        }
    }
}
