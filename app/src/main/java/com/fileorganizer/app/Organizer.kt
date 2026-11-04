package com.fileorganizer.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

enum class Category(val folderName: String, val extensions: Set<String>) {
    IMAGES("Images", setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "svg", "tiff", "tif")),
    VIDEOS("Videos", setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "mpeg", "mpg")),
    DOCUMENTS("Documents", setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "odp", "csv", "epub", "md")),
    AUDIO("Audio", setOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "wma", "opus", "amr")),
    ARCHIVES("Archives", setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz"))
}

data class OrganizeResult(
    val moved: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val errors: List<String> = emptyList()
)

object Organizer {

    fun categoryOf(fileName: String): Category? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        return Category.values().firstOrNull { ext in it.extensions }
    }

    suspend fun organize(
        context: Context,
        roots: List<Uri>,
        onProgress: (String) -> Unit
    ): OrganizeResult = withContext(Dispatchers.IO) {
        var moved = 0
        var skipped = 0
        var failed = 0
        val errors = mutableListOf<String>()

        for (rootUri in roots) {
            val root = DocumentFile.fromTreeUri(context, rootUri)
            if (root == null || !root.isDirectory || !root.canWrite()) {
                failed++
                errors += "Cannot access folder: $rootUri"
                continue
            }
            val targetDirs = HashMap<Category, DocumentFile>()
            val existingNames = HashMap<Category, MutableSet<String>>()

            val files = root.listFiles().filter { it.isFile }
            for (file in files) {
                val name = file.name ?: continue
                val category = categoryOf(name)
                if (category == null) {
                    skipped++
                    continue
                }
                onProgress("Moving: $name")

                val targetDir = targetDirs[category]
                    ?: (root.findFile(category.folderName)?.takeIf { it.isDirectory }
                        ?: root.createDirectory(category.folderName))?.also { targetDirs[category] = it }
                if (targetDir == null) {
                    failed++
                    errors += "Could not create folder ${category.folderName}"
                    continue
                }
                val names = existingNames.getOrPut(category) {
                    targetDir.listFiles().mapNotNull { it.name?.lowercase() }.toMutableSet()
                }

                val outcome = moveSafely(context, file, targetDir, name, names)
                if (outcome == null) moved++ else {
                    failed++
                    errors += "$name: $outcome"
                }
            }
        }
        OrganizeResult(moved, skipped, failed, errors)
    }

    private fun moveSafely(
        context: Context,
        src: DocumentFile,
        targetDir: DocumentFile,
        originalName: String,
        usedNames: MutableSet<String>
    ): String? {
        val finalName = uniqueName(originalName, usedNames)
        val mime = src.type ?: "application/octet-stream"
        val expectedSize = src.length()

        val dest = try {
            targetDir.createFile(mime, finalName)
        } catch (e: Exception) {
            null
        } ?: return "could not create destination (original kept)"

        try {
            val input = context.contentResolver.openInputStream(src.uri)
                ?: throw IOException("cannot open source")
            val output = context.contentResolver.openOutputStream(dest.uri, "w")
                ?: throw IOException("cannot open destination")
            input.use { i -> output.use { o -> i.copyTo(o); o.flush() } }

            if (dest.length() != expectedSize) throw IOException("size mismatch")
        } catch (e: Exception) {
            dest.delete()
            return "copy failed (original kept)"
        }

        usedNames += (dest.name ?: finalName).lowercase()
        return if (src.delete()) null else "copied, but original could not be deleted"
    }

    private fun uniqueName(name: String, used: Set<String>): String {
        if (name.lowercase() !in used) return name
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var n = 1
        while (true) {
            val candidate = if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext"
            if (candidate.lowercase() !in used) return candidate
            n++
        }
    }
}
