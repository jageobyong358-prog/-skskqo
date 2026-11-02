package io.github.jageobyong358.limbusarchive.unity;

/** Texture2D pixel data → ARGB ints, top row first. Compressed formats go through the native texture2ddecoder. */
public final class TexDecoder {
    private TexDecoder() { }

    private static boolean nativeLoaded;
    private static String nativeError;

    static {
        try {
            System.loadLibrary("t2d");
            nativeLoaded = true;
        } catch (Throwable t) {
            nativeError = t.toString();
        }
    }

    static native boolean decodeNative(byte[] data, int codec, int w, int h, int bw, int bh, int[] out);

    static native byte[] uncrunchNative(byte[] data, boolean unity);

    public static String formatName(int f) {
        switch (f) {
            case 1: return "Alpha8";
            case 2: return "ARGB4444";
            case 3: return "RGB24";
            case 4: return "RGBA32";
            case 5: return "ARGB32";
            case 7: return "RGB565";
            case 8: return "BGR24";
            case 9: return "R16";
            case 10: return "DXT1";
            case 11: return "DXT3";
            case 12: return "DXT5";
            case 13: return "RGBA4444";
            case 14: return "BGRA32";
            case 15: return "RHalf";
            case 16: return "RGHalf";
            case 17: return "RGBAHalf";
            case 18: return "RFloat";
            case 19: return "RGFloat";
            case 20: return "RGBAFloat";
            case 24: return "BC6H";
            case 25: return "BC7";
            case 26: return "BC4";
            case 27: return "BC5";
            case 28: return "DXT1Crunched";
            case 29: return "DXT5Crunched";
            case 30: return "PVRTC_RGB2";
            case 31: return "PVRTC_RGBA2";
            case 32: return "PVRTC_RGB4";
            case 33: return "PVRTC_RGBA4";
            case 34: return "ETC_RGB4";
            case 35: return "ATC_RGB4";
            case 36: return "ATC_RGBA8";
            case 41: return "EAC_R";
            case 42: return "EAC_R_SIGNED";
            case 43: return "EAC_RG";
            case 44: return "EAC_RG_SIGNED";
            case 45: return "ETC2_RGB";
            case 46: return "ETC2_RGBA1";
            case 47: return "ETC2_RGBA8";
            case 62: return "RG16";
            case 63: return "R8";
            case 64: return "ETC_RGB4Crunched";
            case 65: return "ETC2_RGBA8Crunched";
            default:
                if (f >= 48 && f <= 53) return "ASTC_RGB_" + astcSize(f) + "x" + astcSize(f);
                if (f >= 54 && f <= 59) return "ASTC_RGBA_" + astcSize(f) + "x" + astcSize(f);
                if (f >= 66 && f <= 71) return "ASTC_HDR_" + astcSize(f) + "x" + astcSize(f);
                return "format " + f;
        }
    }

    private static int astcSize(int f) {
        int[] s = {4, 5, 6, 8, 10, 12};
        if (f >= 48 && f <= 53) return s[f - 48];
        if (f >= 54 && f <= 59) return s[f - 54];
        return s[f - 66];
    }

    /**
     * @param unityMajor the Unity version's year (e.g. 2022, 6000), for which crunch flavour; 0 if unknown
     */
    public static int[] decode(byte[] d, int format, int w, int h, int unityMajor, int unityMinor) {
        if (w <= 0 || h <= 0) throw new IllegalArgumentException("empty texture");
        int[] out = new int[w * h];
        int n = w * h;
        switch (format) {
            case 1: // Alpha8
                for (int i = 0; i < n; i++) out[i] = (d[i] & 0xFF) << 24 | 0xFFFFFF;
                break;
            case 2: // ARGB4444
                for (int i = 0; i < n; i++) {
                    int p = (d[2 * i] & 0xFF) | (d[2 * i + 1] & 0xFF) << 8;
                    out[i] = argb(((p >> 12) & 15) * 17, ((p >> 8) & 15) * 17, ((p >> 4) & 15) * 17, (p & 15) * 17);
                }
                break;
            case 13: // RGBA4444
                for (int i = 0; i < n; i++) {
                    int p = (d[2 * i] & 0xFF) | (d[2 * i + 1] & 0xFF) << 8;
                    out[i] = argb((p & 15) * 17, ((p >> 12) & 15) * 17, ((p >> 8) & 15) * 17, ((p >> 4) & 15) * 17);
                }
                break;
            case 3: // RGB24
                for (int i = 0; i < n; i++) out[i] = argb(255, d[3 * i] & 0xFF, d[3 * i + 1] & 0xFF, d[3 * i + 2] & 0xFF);
                break;
            case 8: // BGR24
                for (int i = 0; i < n; i++) out[i] = argb(255, d[3 * i + 2] & 0xFF, d[3 * i + 1] & 0xFF, d[3 * i] & 0xFF);
                break;
            case 4: // RGBA32
                for (int i = 0; i < n; i++)
                    out[i] = argb(d[4 * i + 3] & 0xFF, d[4 * i] & 0xFF, d[4 * i + 1] & 0xFF, d[4 * i + 2] & 0xFF);
                break;
            case 5: // ARGB32
                for (int i = 0; i < n; i++)
                    out[i] = argb(d[4 * i] & 0xFF, d[4 * i + 1] & 0xFF, d[4 * i + 2] & 0xFF, d[4 * i + 3] & 0xFF);
                break;
            case 14: // BGRA32
                for (int i = 0; i < n; i++)
                    out[i] = argb(d[4 * i + 3] & 0xFF, d[4 * i + 2] & 0xFF, d[4 * i + 1] & 0xFF, d[4 * i] & 0xFF);
                break;
            case 7: // RGB565
                for (int i = 0; i < n; i++) {
                    int p = (d[2 * i] & 0xFF) | (d[2 * i + 1] & 0xFF) << 8;
                    int r = (p >> 11) & 31, g = (p >> 5) & 63, b = p & 31;
                    out[i] = argb(255, (r << 3) | (r >> 2), (g << 2) | (g >> 4), (b << 3) | (b >> 2));
                }
                break;
            case 9: // R16
                for (int i = 0; i < n; i++) out[i] = argb(255, d[2 * i + 1] & 0xFF, 0, 0);
                break;
            case 63: // R8
                for (int i = 0; i < n; i++) out[i] = argb(255, d[i] & 0xFF, 0, 0);
                break;
            case 62: // RG16
                for (int i = 0; i < n; i++) out[i] = argb(255, d[2 * i] & 0xFF, d[2 * i + 1] & 0xFF, 0);
                break;
            case 15: // RHalf
                for (int i = 0; i < n; i++) out[i] = argb(255, f2b(half(d, 2 * i)), 0, 0);
                break;
            case 16: // RGHalf
                for (int i = 0; i < n; i++) out[i] = argb(255, f2b(half(d, 4 * i)), f2b(half(d, 4 * i + 2)), 0);
                break;
            case 17: // RGBAHalf
                for (int i = 0; i < n; i++)
                    out[i] = argb(f2b(half(d, 8 * i + 6)), f2b(half(d, 8 * i)), f2b(half(d, 8 * i + 2)), f2b(half(d, 8 * i + 4)));
                break;
            case 18: // RFloat
                for (int i = 0; i < n; i++) out[i] = argb(255, f2b(fl(d, 4 * i)), 0, 0);
                break;
            case 19: // RGFloat
                for (int i = 0; i < n; i++) out[i] = argb(255, f2b(fl(d, 8 * i)), f2b(fl(d, 8 * i + 4)), 0);
                break;
            case 20: // RGBAFloat
                for (int i = 0; i < n; i++)
                    out[i] = argb(f2b(fl(d, 16 * i + 12)), f2b(fl(d, 16 * i)), f2b(fl(d, 16 * i + 4)), f2b(fl(d, 16 * i + 8)));
                break;
            default:
                compressed(d, format, w, h, unityMajor, unityMinor, out);
        }
        flip(out, w, h);
        return out;
    }

    private static void compressed(byte[] d, int format, int w, int h, int major, int minor, int[] out) {
        if (!nativeLoaded) throw new IllegalStateException("texture decoder not loaded: " + nativeError);
        int codec, bw = 0, bh = 0;
        switch (format) {
            case 28: case 29: case 64: case 65: {
                boolean unity = major > 2017 || (major == 2017 && minor >= 3) || major == 0 || format >= 64;
                byte[] u = uncrunchNative(d, unity);
                if (u == null && major == 0) u = uncrunchNative(d, false);
                if (u == null) throw new IllegalStateException("crunch unpacking failed");
                d = u;
                codec = format == 28 ? 1 : format == 29 ? 2 : format == 64 ? 10 : 13;
                break;
            }
            case 10: codec = 1; break;
            case 11: case 12: codec = 2; break; // DXT3 is decoded as DXT5 (as UnityPy does)
            case 26: codec = 3; break;
            case 27: codec = 4; break;
            case 24: codec = 5; break;
            case 25: codec = 6; break;
            case 34: case 60: case 61: codec = 10; break;
            case 45: codec = 11; break;
            case 46: codec = 12; break;
            case 47: codec = 13; break;
            case 41: codec = 14; break;
            case 42: codec = 15; break;
            case 43: codec = 16; break;
            case 44: codec = 17; break;
            case 35: codec = 30; break;
            case 36: codec = 31; break;
            case 30: case 31: codec = 40; break;
            case 32: case 33: codec = 41; break;
            default:
                if ((format >= 48 && format <= 59) || (format >= 66 && format <= 71)) {
                    codec = 20;
                    bw = bh = astcSize(format);
                } else {
                    throw new IllegalStateException("unsupported texture format: " + formatName(format));
                }
        }
        if (!decodeNative(d, codec, w, h, bw, bh, out)) throw new IllegalStateException("decoding " + formatName(format) + " failed");
    }

    private static int argb(int a, int r, int g, int b) {
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static float fl(byte[] d, int o) {
        return Float.intBitsToFloat((d[o] & 0xFF) | (d[o + 1] & 0xFF) << 8 | (d[o + 2] & 0xFF) << 16 | (d[o + 3] & 0xFF) << 24);
    }

    private static float half(byte[] d, int o) {
        int h = (d[o] & 0xFF) | (d[o + 1] & 0xFF) << 8;
        int s = (h >> 15) & 1, e = (h >> 10) & 31, m = h & 1023;
        float v;
        if (e == 0) v = (float) (m / 1024.0 * Math.pow(2, -14));
        else if (e == 31) v = m == 0 ? Float.POSITIVE_INFINITY : Float.NaN;
        else v = (float) ((1 + m / 1024.0) * Math.pow(2, e - 15));
        return s == 1 ? -v : v;
    }

    private static int f2b(float f) {
        if (Float.isNaN(f)) return 0;
        return Math.max(0, Math.min(255, Math.round(f * 255)));
    }

    private static void flip(int[] px, int w, int h) {
        int[] row = new int[w];
        for (int y = 0; y < h / 2; y++) {
            int a = y * w, b = (h - 1 - y) * w;
            System.arraycopy(px, a, row, 0, w);
            System.arraycopy(px, b, px, a, w);
            System.arraycopy(row, 0, px, b, w);
        }
    }
}
