// JNI bridge to LibreOfficeKit's C API.
//
// The engine (liblo-native-code.so) is bundled in jniLibs and loaded by
// NativeLok before this library. We resolve `libreofficekit_hook_2` from the
// already-loaded engine via dlsym(RTLD_DEFAULT, …) — so this bridge does NOT
// link against the engine at build time (it only needs the LibreOfficeKit C
// headers), which keeps compilation independent of the ~200 MB payload.
//
// Rendering path per page/part:
//   setPart(i) -> getDocumentSize() [twips] -> paintTile() into an Android
//   Bitmap's pixels. LOK may paint BGRA; we swap to RGBA if getTileMode says so.

#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <dlfcn.h>
#include <cstring>
#include <string>

#include <LibreOfficeKit/LibreOfficeKit.h>
#include <LibreOfficeKit/LibreOfficeKitEnums.h>

#define LOG_TAG "LokBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef LibreOfficeKit* (*LokHook2)(const char* install_path, const char* user_profile_url);

static LibreOfficeKit* gKit = nullptr;

static std::string jstr(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_ether4o4_filevault_office_NativeLok_nativeInit(
        JNIEnv* env, jobject, jstring installPath, jstring userProfile) {
    if (gKit) return JNI_TRUE;
    // The engine exports both hooks; the _2 form takes a user-profile URL.
    LokHook2 hook = reinterpret_cast<LokHook2>(dlsym(RTLD_DEFAULT, "libreofficekit_hook_2"));
    if (!hook) {
        LOGE("libreofficekit_hook_2 not found: %s", dlerror());
        return JNI_FALSE;
    }
    std::string install = jstr(env, installPath);
    std::string profile = jstr(env, userProfile);
    gKit = hook(install.c_str(), profile.empty() ? nullptr : profile.c_str());
    if (!gKit || !gKit->pClass) {
        LOGE("libreofficekit_hook_2 returned null (install=%s)", install.c_str());
        gKit = nullptr;
        return JNI_FALSE;
    }
    LOGI("LibreOfficeKit initialized (install=%s)", install.c_str());
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_ether4o4_filevault_office_NativeLok_nativeGetError(JNIEnv* env, jobject) {
    if (!gKit) return nullptr;
    char* err = gKit->pClass->getError(gKit);
    if (!err || !*err) return nullptr;
    jstring s = env->NewStringUTF(err);
    if (gKit->pClass->freeError) gKit->pClass->freeError(err);
    return s;
}

JNIEXPORT jlong JNICALL
Java_com_ether4o4_filevault_office_NativeLok_nativeLoadDoc(
        JNIEnv* env, jobject, jstring path) {
    if (!gKit) return 0;
    std::string p = jstr(env, path);
    LibreOfficeKitDocument* doc =
            gKit->pClass->documentLoadWithOptions(gKit, p.c_str(), nullptr);
    if (!doc) {
        char* err = gKit->pClass->getError(gKit);
        LOGE("documentLoad failed for %s: %s", p.c_str(), err ? err : "(none)");
        if (err && gKit->pClass->freeError) gKit->pClass->freeError(err);
        return 0;
    }
    doc->pClass->initializeForRendering(doc, nullptr);
    return reinterpret_cast<jlong>(doc);
}

JNIEXPORT jint JNICALL
Java_com_ether4o4_filevault_office_NativeLok_nativeGetParts(JNIEnv*, jobject, jlong h) {
    auto* doc = reinterpret_cast<LibreOfficeKitDocument*>(h);
    if (!doc) return 0;
    int n = doc->pClass->getParts(doc);
    return n < 1 ? 1 : n;
}

// Returns [widthTwips, heightTwips] for the given part.
JNIEXPORT jlongArray JNICALL
Java_com_ether4o4_filevault_office_NativeLok_nativeGetSize(
        JNIEnv* env, jobject, jlong h, jint part) {
    auto* doc = reinterpret_cast<LibreOfficeKitDocument*>(h);
    jlong vals[2] = {0, 0};
    if (doc) {
        doc->pClass->setPart(doc, part);
        long w = 0, hh = 0;
        doc->pClass->getDocumentSize(doc, &w, &hh);
        vals[0] = w; vals[1] = hh;
    }
    jlongArray out = env->NewLongArray(2);
    env->SetLongArrayRegion(out, 0, 2, vals);
    return out;
}

// Paints `part` into an ARGB_8888 Bitmap sized by the caller. Returns true on success.
JNIEXPORT jboolean JNICALL
Java_com_ether4o4_filevault_office_NativeLok_nativePaint(
        JNIEnv* env, jobject, jlong h, jint part, jobject bitmap) {
    auto* doc = reinterpret_cast<LibreOfficeKitDocument*>(h);
    if (!doc || !bitmap) return JNI_FALSE;

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;

    doc->pClass->setPart(doc, part);
    long twW = 0, twH = 0;
    doc->pClass->getDocumentSize(doc, &twW, &twH);
    if (twW <= 0 || twH <= 0) return JNI_FALSE;

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;

    // Paint the whole part into the canvas (tile == full document).
    doc->pClass->paintTile(doc, static_cast<unsigned char*>(pixels),
                           static_cast<int>(info.width), static_cast<int>(info.height),
                           0, 0, static_cast<int>(twW), static_cast<int>(twH));

    // LOK may emit BGRA; Android wants RGBA. Swap R/B if needed.
    bool bgra = true;
    if (doc->pClass->getTileMode) bgra = (doc->pClass->getTileMode(doc) == LOK_TILEMODE_BGRA);
    if (bgra) {
        auto* p = static_cast<uint8_t*>(pixels);
        const size_t n = static_cast<size_t>(info.stride) * info.height;
        for (size_t i = 0; i + 3 < n; i += 4) { uint8_t t = p[i]; p[i] = p[i + 2]; p[i + 2] = t; }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_ether4o4_filevault_office_NativeLok_nativeDestroyDoc(JNIEnv*, jobject, jlong h) {
    auto* doc = reinterpret_cast<LibreOfficeKitDocument*>(h);
    if (doc) doc->pClass->destroy(doc);
}

} // extern "C"
