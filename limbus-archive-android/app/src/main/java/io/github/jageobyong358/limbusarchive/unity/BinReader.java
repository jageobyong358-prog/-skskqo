package io.github.jageobyong358.limbusarchive.unity;

import java.nio.charset.StandardCharsets;

/** Reads numbers and strings from a byte array, big or little endian. Positions are absolute in {@code buf}. */
public final class BinReader {
    public final byte[] buf;
    public int pos;
    public final int start, end;
    public boolean big = true;

    public BinReader(byte[] buf) {
        this(buf, 0, buf.length);
    }

    public BinReader(byte[] buf, int start, int length) {
        this.buf = buf;
        this.start = start;
        this.end = start + length;
        this.pos = start;
    }

    /** Position relative to {@link #start}. */
    public int rel() {
        return pos - start;
    }

    public void seekRel(long p) {
        pos = (int) (start + p);
    }

    public int remaining() {
        return end - pos;
    }

    private void need(int n) {
        if (n < 0 || pos + n > end) throw new IllegalStateException("read past the end (" + n + " bytes at " + (pos - start) + ")");
    }

    public void align(int n) {
        int r = (pos - start) % n;
        if (r != 0) pos += n - r;
    }

    public int u8() {
        need(1);
        return buf[pos++] & 0xFF;
    }

    public byte s8() {
        need(1);
        return buf[pos++];
    }

    public boolean bool() {
        return u8() != 0;
    }

    public short s16() {
        need(2);
        int a = buf[pos] & 0xFF, b = buf[pos + 1] & 0xFF;
        pos += 2;
        return (short) (big ? (a << 8) | b : (b << 8) | a);
    }

    public int u16() {
        return s16() & 0xFFFF;
    }

    public int s32() {
        need(4);
        int a = buf[pos] & 0xFF, b = buf[pos + 1] & 0xFF, c = buf[pos + 2] & 0xFF, d = buf[pos + 3] & 0xFF;
        pos += 4;
        return big ? (a << 24) | (b << 16) | (c << 8) | d : (d << 24) | (c << 16) | (b << 8) | a;
    }

    public long u32() {
        return s32() & 0xFFFFFFFFL;
    }

    public long s64() {
        long hi, lo;
        if (big) {
            hi = u32();
            lo = u32();
        } else {
            lo = u32();
            hi = u32();
        }
        return (hi << 32) | lo;
    }

    public float f32() {
        return Float.intBitsToFloat(s32());
    }

    public double f64() {
        return Double.longBitsToDouble(s64());
    }

    public byte[] bytes(int n) {
        need(n);
        byte[] out = new byte[n];
        System.arraycopy(buf, pos, out, 0, n);
        pos += n;
        return out;
    }

    public void skip(int n) {
        need(n);
        pos += n;
    }

    public String cstr() {
        int s = pos;
        while (pos < end && buf[pos] != 0) pos++;
        String r = new String(buf, s, pos - s, StandardCharsets.UTF_8);
        if (pos < end) pos++;
        return r;
    }

    /** int length + bytes (+ alignment by the caller). */
    public byte[] byteArray() {
        int n = s32();
        if (n < 0) throw new IllegalStateException("negative length");
        return bytes(n);
    }
}
