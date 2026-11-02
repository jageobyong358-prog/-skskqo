package io.github.jageobyong358.limbusarchive.unity;

/** LZ4 block decompression (the format Unity's LZ4 / LZ4HC bundles use). */
public final class Lz4 {
    private Lz4() { }

    public static void decompress(byte[] src, int sp, int sLen, byte[] dst, int dp, int dLen) {
        int sEnd = sp + sLen, dEnd = dp + dLen, dStart = dp;
        while (sp < sEnd) {
            int token = src[sp++] & 0xFF;
            int lit = token >>> 4;
            if (lit == 15) {
                int b;
                do {
                    b = src[sp++] & 0xFF;
                    lit += b;
                } while (b == 255);
            }
            if (dp + lit > dEnd || sp + lit > sEnd) throw new IllegalStateException("LZ4: corrupt literals");
            System.arraycopy(src, sp, dst, dp, lit);
            sp += lit;
            dp += lit;
            if (sp >= sEnd) break;
            int off = (src[sp] & 0xFF) | ((src[sp + 1] & 0xFF) << 8);
            sp += 2;
            if (off == 0 || dp - off < dStart) throw new IllegalStateException("LZ4: bad offset");
            int len = token & 15;
            if (len == 15) {
                int b;
                do {
                    b = src[sp++] & 0xFF;
                    len += b;
                } while (b == 255);
            }
            len += 4;
            if (dp + len > dEnd) throw new IllegalStateException("LZ4: output overflow");
            int m = dp - off;
            if (off >= len) {
                System.arraycopy(dst, m, dst, dp, len);
                dp += len;
            } else {
                for (int i = 0; i < len; i++) dst[dp++] = dst[m++];
            }
        }
        if (dp != dEnd) throw new IllegalStateException("LZ4: size mismatch " + (dp - dStart) + " != " + dLen);
    }
}
