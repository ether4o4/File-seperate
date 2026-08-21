package com.ether4o4.filevault.tools

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class OrganizerEngine(private val context: Context) {
    private val resolver = context.contentResolver
    private val undoFile get() = java.io.File(context.filesDir, "organizer_undo.tsv")

    val categories = listOf(
        "APKs", "Apple", "iPhone_Data", "Metadata", "HTML_Dashboards", "Web", "Archives", "Audio", "Music", "Videos",
        "Images", "Icons", "Screenshots", "Documents", "PDFs", "Spreadsheets", "Presentations", "Ebooks", "Backups",
        "Databases", "Code", "Projects", "Config", "JSON", "XML", "YAML", "Logs", "Markdown", "Text", "Fonts",
        "Subtitles", "ROMs", "Games", "Executables", "Unknown", "Other",
    )

    data class Result(val scanned: Int, val moved: Int, val errors: Int, val counts: Map<String, Int>)

    suspend fun organize(treeUri: Uri, dryRun: Boolean): Result = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: error("Folder is unavailable")
        if (!root.isDirectory) error("Selected item is not a folder")
        val dirs = if (dryRun) emptyMap() else categories.associateWith { category ->
            root.findFile(category)?.takeIf { it.isDirectory }
                ?: root.createDirectory(category)
                ?: error("Cannot create $category")
        }
        val counts = categories.associateWith { 0 }.toMutableMap()
        var scanned = 0
        var moved = 0
        var errors = 0
        if (!dryRun) undoFile.writeText("")

        fun walk(dir: DocumentFile, relativePath: List<String>) {
            for (file in dir.listFiles()) {
                if (file.isDirectory) {
                    if (file.name in categories || file.name == "_Organizer") continue
                    walk(file, relativePath + (file.name ?: "unnamed"))
                    continue
                }
                scanned++
                val category = classify(file)
                counts[category] = counts.getValue(category) + 1
                if (dryRun) continue
                try {
                    val name = file.name ?: "unnamed"
                    val destination = move(file, dirs.getValue(category), name)
                    val parentPath = Uri.encode(relativePath.joinToString("/"))
                    undoFile.appendText("${treeUri}\t$parentPath\t$name\t${destination.uri}\n")
                    moved++
                } catch (_: Throwable) {
                    errors++
                }
            }
        }
        walk(root, emptyList())
        Result(scanned, moved, errors, counts)
    }

    suspend fun undo(): Int = withContext(Dispatchers.IO) {
        if (!undoFile.exists()) return@withContext 0
        var restored = 0
        for (line in undoFile.readLines().asReversed()) {
            val parts = line.split('\t')
            if (parts.size < 4) continue
            try {
                val root = DocumentFile.fromTreeUri(context, Uri.parse(parts[0])) ?: continue
                val relative = Uri.decode(parts[1]).takeIf { it.isNotEmpty() }?.split('/') ?: emptyList()
                var parent = root
                for (segment in relative) parent = parent.findFile(segment)?.takeIf { it.isDirectory } ?: root
                val source = DocumentFile.fromSingleUri(context, Uri.parse(parts[3])) ?: continue
                val target = parent.createFile(source.type ?: "application/octet-stream", parts[2]) ?: continue
                resolver.openInputStream(source.uri)?.use { input ->
                    resolver.openOutputStream(target.uri)?.use { output -> input.copyTo(output, 256 * 1024) }
                }
                if (source.delete()) restored++
            } catch (_: Throwable) { }
        }
        undoFile.delete()
        restored
    }

    private fun move(source: DocumentFile, dir: DocumentFile, name: String): DocumentFile {
        val destinationName = uniqueName(dir, name)
        val destination = dir.createFile(source.type ?: "application/octet-stream", destinationName)
            ?: error("Cannot create destination")
        resolver.openInputStream(source.uri)?.use { input ->
            resolver.openOutputStream(destination.uri)?.use { output -> input.copyTo(output, 256 * 1024) }
        } ?: error("Cannot read source")
        if (!source.delete()) error("Copied but could not delete source")
        return destination
    }

    private fun uniqueName(dir: DocumentFile, name: String): String {
        if (dir.findFile(name) == null) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (dir.findFile("$base ($n)$ext") != null) n++
        return "$base ($n)$ext"
    }

    private fun classify(file: DocumentFile): String {
        val name = (file.name ?: "").lowercase(Locale.US)
        val ext = name.substringAfterLast('.', "")
        fun hasAny(vararg terms: String) = terms.any { name.contains(it) }
        fun contentHas(vararg terms: String): Boolean {
            if (file.length() > 10 * 1024 * 1024) return false
            return try {
                val text = resolver.openInputStream(file.uri)?.use { input ->
                    val buffer = ByteArray(65536)
                    val count = input.read(buffer)
                    if (count <= 0) "" else String(buffer, 0, count, Charsets.UTF_8).lowercase(Locale.US)
                } ?: ""
                terms.any { text.contains(it) }
            } catch (_: Throwable) { false }
        }

        if (hasAny("screenshot", "screen_shot", "screen-capture", "screen_capture") || name.startsWith("screen_")) return "Screenshots"
        if (hasAny("icon", "logo", "favicon", "launcher_icon", "ic_launcher", "app_icon") || name.startsWith("ic_")) return "Icons"
        if (hasAny("iphone", "ios", "apple", "icloud", "itunes", "mobilebackup", "mobile_backup", "applebackup", "sysdiagnose", "ipsw", "propertylist", "plist")) {
            return when (ext) { "png", "jpg", "jpeg", "webp", "heic" -> "Apple"; "html", "htm" -> "HTML_Dashboards"; else -> "iPhone_Data" }
        }
        if (hasAny("imessage", "chatstorage", "addressbook", "callhistory", "call_history", "safari_history", "browser_history", "manifest", "camera_roll", "photolibrary")) return "iPhone_Data"
        if (hasAny("metadata", "meta_data", "meta-data", "meta.json", "meta.xml")) return if (ext in setOf("html", "htm")) "HTML_Dashboards" else "Metadata"
        if (hasAny("dashboard", "forensic", "evidence", "extraction", "extracted", "export", "report") && ext in setOf("html", "htm")) return "HTML_Dashboards"
        if (ext in setOf("html", "htm")) return if (contentHas("iphone", "ios", "apple", "icloud", "metadata", "mobilebackup", "forensic", "dashboard") || hasAny("meta", "data", "iphone", "ios", "apple", "report", "dashboard", "export")) "HTML_Dashboards" else "Web"
        if (ext == "json") return if (contentHas("iphone", "ios", "apple", "icloud", "mobilebackup", "metadata", "cfbundle", "meta data") || hasAny("iphone", "apple", "meta", "data")) "iPhone_Data" else "JSON"
        if (ext == "xml") return if (contentHas("apple", "iphone", "ios", "cfbundle", "plist") || hasAny("iphone", "apple", "plist")) "iPhone_Data" else "XML"
        if (ext in setOf("plist", "mobileconfig", "mobileprovision")) return "Apple"
        if (ext in setOf("db", "sqlite", "sqlite3", "sqlitedb", "db3")) return if (hasAny("iphone", "ios", "apple", "message", "sms", "chat", "contact", "call", "safari", "history", "addressbook", "manifest")) "iPhone_Data" else "Databases"
        if (ext == "csv") return if (hasAny("iphone", "ios", "apple", "metadata", "meta", "data", "export", "extraction")) "Metadata" else "Spreadsheets"

        return when (ext) {
            "png", "jpg", "jpeg", "jpe", "gif", "bmp", "webp", "heic", "heif", "avif", "tif", "tiff" -> "Images"
            "ico", "icns" -> "Icons"
            "svg" -> if (hasAny("icon", "logo", "favicon", "launcher")) "Icons" else "Images"
            "mp4", "mkv", "mov", "avi", "wmv", "flv", "webm", "m4v", "3gp", "3gpp", "mpeg", "mpg", "ts", "mts", "m2ts", "vob" -> "Videos"
            "mp3", "flac", "m4a", "aac", "ogg", "opus", "alac", "wma", "aiff", "aif" -> "Music"
            "wav", "wave", "amr", "caf", "ac3", "dts" -> "Audio"
            "pdf" -> "PDFs"
            "doc", "docx", "odt", "rtf", "pages" -> "Documents"
            "xls", "xlsx", "ods", "numbers" -> "Spreadsheets"
            "ppt", "pptx", "odp", "key" -> "Presentations"
            "epub", "mobi", "azw", "azw3", "fb2", "cbz", "cbr" -> "Ebooks"
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst", "tgz", "tbz", "txz" -> "Archives"
            "apk", "xapk", "apks", "aab" -> "APKs"
            "py", "pyw", "js", "jsx", "ts", "tsx", "java", "kt", "kts", "c", "h", "cpp", "cc", "cxx", "hpp", "rs", "go", "rb", "php", "swift", "dart", "lua", "pl", "pm", "r", "scala", "groovy", "gradle", "sql", "vue", "svelte" -> "Code"
            "css", "scss", "sass", "less", "map", "wasm" -> "Web"
            "sh", "bash", "zsh", "fish", "bat", "cmd", "ps1" -> "Executables"
            "env", "properties", "prefs", "conf", "cfg", "ini", "toml" -> "Config"
            "yaml", "yml" -> "YAML"
            "md", "markdown", "mdown", "mkdn", "rst" -> "Markdown"
            "log" -> "Logs"
            "txt", "text", "nfo", "data" -> if (hasAny("meta", "metadata", "iphone", "ios", "apple", "icloud", "backup", "extract", "forensic", "mobilebackup") || contentHas("iphone", "ios", "apple", "icloud", "metadata", "mobilebackup", "meta data", "cfbundle")) "Metadata" else "Text"
            "ttf", "otf", "woff", "woff2", "eot" -> "Fonts"
            "srt", "ass", "ssa", "sub", "vtt" -> "Subtitles"
            "iso", "nes", "gba", "gbc", "gb", "nds", "n64", "z64", "smc", "sfc" -> "ROMs"
            "sav", "savestate", "pak", "wad" -> "Games"
            else -> if (hasAny("package.json", "package-lock", "yarn.lock", "pnpm-lock", "requirements.txt", "pyproject", "cargo.toml", "dockerfile", "makefile", "androidmanifest", "build.gradle", "settings.gradle")) "Projects" else "Unknown"
        }
    }
}
