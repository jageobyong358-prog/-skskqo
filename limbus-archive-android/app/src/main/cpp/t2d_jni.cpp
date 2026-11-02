// JNI bridge to texture2ddecoder: compressed texture bytes -> ARGB ints (0xAARRGGBB, rows as stored)
#include <jni.h>
#include <cstdint>
#include <cstdlib>
#include "bcn.h"
#include "etc.h"
#include "astc.h"
#include "atc.h"
#include "pvrtc.h"
#include "crunch.h"
#include "unitycrunch.h"

enum Codec {
    BC1 = 1, BC3 = 2, BC4 = 3, BC5 = 4, BC6 = 5, BC7 = 6,
    ETC1 = 10, ETC2 = 11, ETC2A1 = 12, ETC2A8 = 13, EACR = 14, EACR_S = 15, EACRG = 16, EACRG_S = 17,
    ASTC = 20, ATC_RGB4 = 30, ATC_RGBA8 = 31, PVRTC2 = 40, PVRTC4 = 41
};

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_jageobyong358_limbusarchive_unity_TexDecoder_decodeNative(
        JNIEnv *env, jclass, jbyteArray data, jint codec, jint w, jint h, jint bw, jint bh, jintArray out) {
    jbyte *src = env->GetByteArrayElements(data, nullptr);
    jint *dst = env->GetIntArrayElements(out, nullptr);
    if (!src || !dst) return JNI_FALSE;
    auto *d = reinterpret_cast<const uint8_t *>(src);
    auto *img = reinterpret_cast<uint32_t *>(dst);
    int ok = 0;
    switch (codec) {
        case BC1: ok = decode_bc1(d, w, h, img); break;
        case BC3: ok = decode_bc3(d, w, h, img); break;
        case BC4: ok = decode_bc4(d, w, h, img); break;
        case BC5: ok = decode_bc5(d, w, h, img); break;
        case BC6: ok = decode_bc6(d, w, h, img); break;
        case BC7: ok = decode_bc7(d, w, h, img); break;
        case ETC1: ok = decode_etc1(d, w, h, img); break;
        case ETC2: ok = decode_etc2(d, w, h, img); break;
        case ETC2A1: ok = decode_etc2a1(d, w, h, img); break;
        case ETC2A8: ok = decode_etc2a8(d, w, h, img); break;
        case EACR: ok = decode_eacr(d, w, h, img); break;
        case EACR_S: ok = decode_eacr_signed(d, w, h, img); break;
        case EACRG: ok = decode_eacrg(d, w, h, img); break;
        case EACRG_S: ok = decode_eacrg_signed(d, w, h, img); break;
        case ASTC: ok = decode_astc(d, w, h, bw, bh, img); break;
        case ATC_RGB4: ok = decode_atc_rgb4(d, w, h, img); break;
        case ATC_RGBA8: ok = decode_atc_rgba8(d, w, h, img); break;
        case PVRTC2: ok = decode_pvrtc(d, w, h, img, 1); break;
        case PVRTC4: ok = decode_pvrtc(d, w, h, img, 0); break;
        default: ok = 0;
    }
    env->ReleaseIntArrayElements(out, dst, 0);
    env->ReleaseByteArrayElements(data, src, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

/** Crunched texture -> the DXT/ETC bytes of its first level (null if it fails). unity: Unity's crunch fork. */
extern "C" JNIEXPORT jbyteArray JNICALL
Java_io_github_jageobyong358_limbusarchive_unity_TexDecoder_uncrunchNative(
        JNIEnv *env, jclass, jbyteArray data, jboolean unity) {
    jsize n = env->GetArrayLength(data);
    jbyte *src = env->GetByteArrayElements(data, nullptr);
    if (!src) return nullptr;
    void *ret = nullptr;
    uint32_t size = 0;
    bool ok = unity
              ? unity_crunch_unpack_level(reinterpret_cast<const uint8_t *>(src), (uint32_t) n, 0, &ret, &size)
              : crunch_unpack_level(reinterpret_cast<const uint8_t *>(src), (uint32_t) n, 0, &ret, &size);
    env->ReleaseByteArrayElements(data, src, JNI_ABORT);
    jbyteArray out = nullptr;
    if (ok && ret) {
        out = env->NewByteArray((jsize) size);
        env->SetByteArrayRegion(out, 0, (jsize) size, reinterpret_cast<const jbyte *>(ret));
    }
    if (ret) delete[] static_cast<uint8_t *>(ret);
    return out;
}
