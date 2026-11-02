package io.github.jageobyong358.limbusarchive.unity;

import org.tukaani.xz.LZMAInputStream;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A UnityFS asset bundle: its files (serialized files, .resS / .resource data) laid out in one buffer. */
public final class BundleFile {

    public static final class Node {
        public final String path;
        public final long offset, size;
        public final int flags;

        Node(String path, long offset, long size, int flags) {
            this.path = path;
            this.offset = offset;
            this.size = size;
            this.flags = flags;
        }

        public String name() {
            int i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            return i >= 0 ? path.substring(i + 1) : path;
        }
    }

    public final int format;
    public final String versionPlayer, versionEngine;
    public final List<Node> nodes = new ArrayList<>();
    /** All files, back to back (null when only the directory was read). */
    public byte[] data;

    private BundleFile(int format, String player, String engine) {
        this.format = format;
        this.versionPlayer = player;
        this.versionEngine = engine;
    }

    public static boolean isBundle(byte[] head) {
        return startsWith(head, "UnityFS") || startsWith(head, "UnityWeb") || startsWith(head, "UnityRaw");
    }

    static boolean startsWith(byte[] b, String s) {
        if (b.length < s.length() + 1) return false;
        for (int i = 0; i < s.length(); i++) if (b[i] != s.charAt(i)) return false;
        return b[s.length()] == 0;
    }

    /** Reads the bundle; with {@code directoryOnly} just its list of files (cheap: no data blocks are unpacked). */
    public static BundleFile read(byte[] file, boolean directoryOnly) throws IOException {
        BinReader r = new BinReader(file);
        r.big = true;
        String sig = r.cstr();
        int format = r.s32();
        String player = r.cstr();
        String engine = r.cstr();
        BundleFile b = new BundleFile(format, player, engine);
        if (sig.equals("UnityFS") || ((sig.equals("UnityWeb") || sig.equals("UnityRaw")) && format == 6)) {
            b.readFs(r, sig, directoryOnly);
        } else if (sig.equals("UnityWeb") || sig.equals("UnityRaw")) {
            b.readWebRaw(r, sig);
        } else {
            throw new IOException("not a Unity bundle (" + sig + ")");
        }
        return b;
    }

    private void readWebRaw(BinReader r, String sig) throws IOException {
        if (format >= 4) {
            r.skip(16);
            r.s32();
        }
        r.s32(); // minimum streamed bytes
        int headerSize = r.s32();
        r.s32();
        int levels = r.s32();
        r.skip(4 * 2 * (levels - 1));
        int compressed = r.s32();
        r.s32();
        r.pos = headerSize;
        byte[] raw = r.bytes(compressed);
        if (sig.equals("UnityWeb")) raw = lzma(raw, 0, raw.length, -1, true);
        BinReader d = new BinReader(raw);
        d.big = true;
        int n = d.s32();
        for (int i = 0; i < n; i++) {
            String p = d.cstr();
            long off = d.u32(), size = d.u32();
            nodes.add(new Node(p, off, size, 0));
        }
        data = raw;
    }

    private static int[] engineVersion(String s) {
        try {
            String[] parts = s.split("\\.");
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            String c = parts[2].replaceAll("[^0-9].*$", "");
            return new int[]{a, b, Integer.parseInt(c)};
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean newFlags(int[] v) {
        if (v == null || v[0] < 2020) return v == null; // stripped ("5.x.x"): a recent Unity (Limbus is on Unity 6)
        if (v[0] == 2020) return v[1] > 3 || (v[1] == 3 && v[2] >= 34);
        if (v[0] == 2021) return v[1] > 3 || (v[1] == 3 && v[2] >= 2);
        if (v[0] == 2022) return v[1] > 1 || (v[1] == 1 && v[2] >= 1);
        return true;
    }

    private void readFs(BinReader r, String sig, boolean directoryOnly) throws IOException {
        r.s64(); // file size
        int compressedSize = r.s32();
        int uncompressedSize = r.s32();
        int flags = r.s32();
        if (!sig.equals("UnityFS")) r.u8();
        int[] v = engineVersion(versionEngine);
        boolean newF = newFlags(v);
        int encryption = newF ? 0x1400 : 0x200;
        if ((flags & encryption) != 0) throw new IOException("encrypted bundle (not supported)");
        if (format >= 7 || (v != null && v[0] == 2019 && (v[1] > 4 || (v[1] == 4 && v[2] >= 15)))) r.align(16);

        int start = r.pos;
        byte[] info;
        if ((flags & 0x80) != 0) {
            info = java.util.Arrays.copyOfRange(r.buf, r.end - compressedSize, r.end);
        } else {
            info = r.bytes(compressedSize);
        }
        info = decompress(info, 0, info.length, uncompressedSize, flags & 0x3F);
        BinReader ir = new BinReader(info);
        ir.big = true;
        ir.skip(16);
        int blockCount = ir.s32();
        long[] usize = new long[blockCount], csize = new long[blockCount], boff = new long[blockCount];
        int[] bflags = new int[blockCount];
        long total = 0;
        for (int i = 0; i < blockCount; i++) {
            usize[i] = ir.u32();
            csize[i] = ir.u32();
            bflags[i] = ir.u16();
            boff[i] = format >= 9 ? ir.s64() : -1;
            total += usize[i];
        }
        int nodeCount = ir.s32();
        for (int i = 0; i < nodeCount; i++) {
            long off = ir.s64(), size = ir.s64();
            int f = ir.s32();
            nodes.add(new Node(ir.cstr(), off, size, f));
        }
        if (directoryOnly) return;
        if (newF && (flags & 0x200) != 0) r.align(16);
        if (total > Integer.MAX_VALUE - 16) throw new IOException("bundle too large to open");
        data = new byte[(int) total];
        int base = r.pos, dp = 0;
        for (int i = 0; i < blockCount; i++) {
            if (boff[i] >= 0) r.pos = (int) (base + boff[i]);
            int cs = (int) csize[i], us = (int) usize[i];
            if (r.pos + cs > r.end) throw new IOException("bundle is cut short");
            decompressInto(r.buf, r.pos, cs, data, dp, us, bflags[i] & 0x3F);
            r.pos += cs;
            dp += us;
        }
    }

    private static byte[] decompress(byte[] src, int off, int len, int usize, int comp) throws IOException {
        if (comp == 0) return java.util.Arrays.copyOfRange(src, off, off + len);
        byte[] out = new byte[usize];
        decompressInto(src, off, len, out, 0, usize, comp);
        return out;
    }

    private static void decompressInto(byte[] src, int off, int len, byte[] dst, int dp, int usize, int comp) throws IOException {
        if (usize == 0) return;
        switch (comp) {
            case 0:
                System.arraycopy(src, off, dst, dp, Math.min(len, usize));
                break;
            case 1: {
                byte[] out = lzma(src, off, len, usize, false);
                System.arraycopy(out, 0, dst, dp, usize);
                break;
            }
            case 2:
            case 3:
                Lz4.decompress(src, off, len, dst, dp, usize);
                break;
            default:
                throw new IOException("unknown compression " + comp + " (LZHAM or a custom one)");
        }
    }

    /** Unity's LZMA: 5 bytes of properties (+ 8 of size for UnityWeb) then the raw stream. */
    private static byte[] lzma(byte[] src, int off, int len, int usize, boolean sizeInHeader) throws IOException {
        int props = src[off] & 0xFF;
        int dict = (src[off + 1] & 0xFF) | ((src[off + 2] & 0xFF) << 8) | ((src[off + 3] & 0xFF) << 16) | ((src[off + 4] & 0xFF) << 24);
        int dataOff = off + (sizeInHeader ? 13 : 5);
        long size = usize;
        if (sizeInHeader) {
            size = 0;
            for (int i = 0; i < 8; i++) size |= (long) (src[off + 5 + i] & 0xFF) << (8 * i);
        }
        // size unknown to the decoder: Unity's streams may carry an end marker, which a known size rejects
        LZMAInputStream in = new LZMAInputStream(new ByteArrayInputStream(src, dataOff, len - (dataOff - off)),
                -1, (byte) props, dict);
        byte[] out = new byte[(int) size];
        new DataInputStream(in).readFully(out);
        return out;
    }
}
