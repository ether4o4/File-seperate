package com.ether4o4.filevault.data

import android.webkit.MimeTypeMap
import java.util.Locale

/**
 * Which bundled viewer should open a given file. Detection is by extension first
 * (reliable for imported files) and MIME type second.
 */
enum class FileKind {
    TEXT, CODE, IMAGE, PDF, HTML, SQLITE, AUDIO, VIDEO, OFFICE, OTHER;

    companion object {
        private val CODE_EXT = setOf(
            "js", "ts", "tsx", "jsx", "json", "kt", "kts", "java", "py", "rb",
            "go", "rs", "c", "h", "cpp", "hpp", "cc", "cs", "swift", "php", "sh",
            "bash", "zsh", "sql", "yaml", "yml", "toml", "xml", "gradle", "src",
            "css", "scss", "ini", "conf", "properties", "lua", "pl", "r", "dart",
        )
        private val TEXT_EXT = setOf("txt", "md", "markdown", "log", "csv", "tsv", "rtf")
        private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "heic", "heif", "svg", "ico")
        private val AUDIO_EXT = setOf("mp3", "wav", "ogg", "oga", "flac", "aac", "m4a", "opus", "amr", "mid", "midi")
        private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "ts", "flv")
        private val OFFICE_EXT = setOf(
            "doc", "docx", "docm", "dot", "dotx", "odt", "ott", "rtf",
            "xls", "xlsx", "xlsm", "xlt", "xltx", "ods", "ots", "csv",
            "ppt", "pptx", "pptm", "pot", "potx", "odp", "otp",
            "odg", "otg", "fodt", "fods", "fodp",
        )
        private val SQLITE_EXT = setOf("db", "sqlite", "sqlite3", "db3")

        fun of(name: String, mime: String? = null): FileKind {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            when {
                ext in SQLITE_EXT -> return SQLITE
                ext == "pdf" -> return PDF
                ext == "html" || ext == "htm" || ext == "xhtml" -> return HTML
                ext in IMAGE_EXT -> return IMAGE
                ext in AUDIO_EXT -> return AUDIO
                ext in VIDEO_EXT -> return VIDEO
                // Office check precedes generic text so csv/rtf open in the office
                // viewer when LibreOffice is available; text viewer is the fallback.
                ext in OFFICE_EXT -> return OFFICE
                ext in CODE_EXT -> return CODE
                ext in TEXT_EXT -> return TEXT
            }
            // Fall back to MIME type.
            val m = mime ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            return when {
                m == null -> OTHER
                m == "application/pdf" -> PDF
                m == "text/html" -> HTML
                m.startsWith("image/") -> IMAGE
                m.startsWith("audio/") -> AUDIO
                m.startsWith("video/") -> VIDEO
                m.startsWith("text/") -> TEXT
                m.contains("officedocument") || m.contains("opendocument") ||
                    m.contains("msword") || m.contains("ms-excel") || m.contains("ms-powerpoint") -> OFFICE
                else -> OTHER
            }
        }

        /** highlight.js language token for a code file, or null to auto-detect. */
        fun syntaxLanguage(name: String): String? =
            when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
                "js", "jsx" -> "javascript"
                "ts", "tsx" -> "typescript"
                "kt", "kts" -> "kotlin"
                "py" -> "python"
                "rb" -> "ruby"
                "rs" -> "rust"
                "go" -> "go"
                "java" -> "java"
                "c", "h" -> "c"
                "cpp", "hpp", "cc" -> "cpp"
                "cs" -> "csharp"
                "sh", "bash", "zsh" -> "bash"
                "json" -> "json"
                "yaml", "yml" -> "yaml"
                "xml", "gradle" -> "xml"
                "html", "htm" -> "xml"
                "css", "scss" -> "css"
                "sql" -> "sql"
                "md", "markdown" -> "markdown"
                else -> null
            }
    }
}
