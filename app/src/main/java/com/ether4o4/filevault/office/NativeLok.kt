package com.ether4o4.filevault.office

/**
 * Thin JNI surface over LibreOfficeKit (see cpp/lok_bridge.cpp).
 *
 * Loads the bundled engine (liblo-native-code.so) and its dependencies in the
 * order the build expects, then our small `lovault` bridge. All native methods
 * are no-ops until [ensureLoaded] has succeeded and nativeInit has returned true.
 */
object NativeLok {

    // Dependency order matters: leaf libs first, engine last, bridge last of all.
    private val DEP_LIBS = listOf(
        "c++_shared",
        "nspr4", "plds4", "plc4",
        "nssutil3", "freebl3", "sqlite3", "softokn3",
        "nss3", "nssckbi", "nssdbm3", "smime3", "ssl3",
        "lo-native-code",
    )

    @Volatile
    var loaded: Boolean = false
        private set

    /** Loads native libraries. Returns false if the engine isn't bundled in this build. */
    @Synchronized
    fun ensureLoaded(): Boolean {
        if (loaded) return true
        try {
            for (lib in DEP_LIBS) {
                // Some deps get auto-resolved as DT_NEEDED of the engine; ignore those failures.
                runCatching { System.loadLibrary(lib) }
            }
            System.loadLibrary("lo-native-code") // hard requirement
            System.loadLibrary("lovault")        // our JNI bridge
            loaded = true
        } catch (t: UnsatisfiedLinkError) {
            loaded = false
        }
        return loaded
    }

    external fun nativeInit(installPath: String, userProfileUrl: String): Boolean
    external fun nativeGetError(): String?
    external fun nativeLoadDoc(path: String): Long
    external fun nativeGetParts(handle: Long): Int
    /** Returns [widthTwips, heightTwips] for the given part. */
    external fun nativeGetSize(handle: Long, part: Int): LongArray
    external fun nativePaint(handle: Long, part: Int, bitmap: android.graphics.Bitmap): Boolean
    external fun nativeDestroyDoc(handle: Long)
}
