package s3backup.util

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.Deflater
import java.util.zip.ZipEntry
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
}