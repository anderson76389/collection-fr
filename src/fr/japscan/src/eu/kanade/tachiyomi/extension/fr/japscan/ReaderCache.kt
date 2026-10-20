package eu.kanade.tachiyomi.extension.fr.japscan

import java.io.File

internal fun readerCacheFile(cacheDir: File, path: String): File? {
    val file = File(path).canonicalFile
    return file.takeIf { it.parentFile == cacheDir.canonicalFile && it.name.startsWith("japscan-") && it.isFile }
}
