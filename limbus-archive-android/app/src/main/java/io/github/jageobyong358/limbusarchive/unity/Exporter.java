package io.github.jageobyong358.limbusarchive.unity;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Turns an object into something to show or save: a picture, a text, a sound, a video, a font or its fields as JSON. */
public final class Exporter {
    private Exporter() { }

    public static final class Result {
        public String kind;      // image, text, audio, video, font, json, binary
        public String fileName;
        public String mime;
        public byte[] data;      // for everything but images
        public int[] pixels;     // for images, ARGB top row first
        public int width, height;
        public String note;      // shown next to the preview (format, size…)
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    static long num(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        return v instanceof Number ? ((Number) v).longValue() : 0;
    }

    static double dbl(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        return v instanceof Number ? ((Number) v).doubleValue() : 0;
    }

    static String str(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        return v == null ? "" : v.toString();
    }

    static byte[] bytes(Object v) {
        if (v instanceof byte[]) return (byte[]) v;
        if (v instanceof UStr) return ((UStr) v).bytes;
        if (v instanceof List) {
            List<?> l = (List<?>) v;
            byte[] b = new byte[l.size()];
            for (int i = 0; i < b.length; i++) b[i] = ((Number) l.get(i)).byteValue();
            return b;
        }
        return null;
    }

    static String safe(String n) {
        String s = n == null ? "" : n.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]", "_").trim();
        return s.isEmpty() ? "object" : s;
    }

    public static String baseName(SerializedFile.Obj o) {
        return safe(o.name != null ? o.name : o.typeName + "_" + o.pathId);
    }

    /** Fields as JSON (any object with a type tree). */
    public static Result json(SerializedFile.Obj o) {
        Result r = new Result();
        r.kind = "json";
        r.mime = "application/json";
        r.fileName = baseName(o) + ".json";
        String text;
        try {
            text = Json.pretty(TypeTreeReader.read(o.file, o));
        } catch (RuntimeException e) {
            text = "{\n \"error\": " + quote(e.toString()) + ",\n \"type\": " + quote(o.typeName) + "\n}";
        }
        r.data = text.getBytes(StandardCharsets.UTF_8);
        return r;
    }

    public static Result raw(SerializedFile.Obj o) {
        Result r = new Result();
        r.kind = "binary";
        r.mime = "application/octet-stream";
        r.fileName = baseName(o) + ".dat";
        r.data = o.raw();
        return r;
    }

    private static String quote(String s) {
        StringBuilder b = new StringBuilder();
        Json.quote(b, s);
        return b.toString();
    }

    /** The natural export of an object (a picture for a texture, the text of a TextAsset…), else its JSON. */
    public static Result export(SerializedFile.Obj o) {
        switch (o.typeName) {
            case "Texture2D": return texture(o);
            case "Sprite": return sprite(o);
            case "TextAsset": return textAsset(o);
            case "AudioClip": return audio(o);
            case "VideoClip": return video(o);
            case "Font": {
                Map<String, Object> m = TypeTreeReader.read(o.file, o);
                byte[] f = bytes(m.get("m_FontData"));
                if (f == null || f.length == 0) return json(o);
                Result r = new Result();
                r.kind = "font";
                boolean otf = f.length > 4 && f[0] == 'O' && f[1] == 'T' && f[2] == 'T' && f[3] == 'O';
                r.mime = otf ? "font/otf" : "font/ttf";
                r.fileName = baseName(o) + (otf ? ".otf" : ".ttf");
                r.data = f;
                return r;
            }
            default: return json(o);
        }
    }

    private static int[] version(SerializedFile f) {
        try {
            String[] p = f.unityVersion.split("\\.");
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
        } catch (Exception e) {
            return new int[]{0, 0};
        }
    }

    private static byte[] streamed(SerializedFile f, Map<String, Object> stream, String pathKey) {
        if (stream == null) return null;
        String path = str(stream, pathKey);
        long off = num(stream, "offset"), size = num(stream, "size");
        if (stream.containsKey("m_Offset")) {
            off = num(stream, "m_Offset");
            size = num(stream, "m_Size");
        }
        if (path.isEmpty() || size <= 0 || !(f.owner instanceof AssetFile)) return null;
        return ((AssetFile) f.owner).resourceRange(f, path, off, size);
    }

    public static Result texture(SerializedFile.Obj o) {
        Map<String, Object> m = TypeTreeReader.read(o.file, o);
        int w = (int) num(m, "m_Width"), h = (int) num(m, "m_Height"), fmt = (int) num(m, "m_TextureFormat");
        byte[] data = bytes(m.get("image data"));
        if (data == null || data.length == 0) data = streamed(o.file, map(m.get("m_StreamData")), "path");
        Result r = new Result();
        r.kind = "image";
        r.mime = "image/png";
        r.fileName = baseName(o) + ".png";
        r.width = w;
        r.height = h;
        r.note = w + "×" + h + " · " + TexDecoder.formatName(fmt);
        if (data == null || data.length == 0) throw new IllegalStateException("no pixel data (" + r.note + ")");
        int[] v = version(o.file);
        r.pixels = TexDecoder.decode(data, fmt, w, h, v[0], v[1]);
        return r;
    }

    /** The texture a PPtr points to, in this file or another one. */
    static SerializedFile.Obj resolve(SerializedFile f, Map<String, Object> pptr) {
        if (pptr == null) return null;
        long fileId = num(pptr, "m_FileID"), pathId = num(pptr, "m_PathID");
        if (pathId == 0) return null;
        SerializedFile target = f;
        if (fileId > 0) {
            if (fileId - 1 >= f.externals.size() || f.owner == null) return null;
            target = f.owner.external(f, f.externals.get((int) (fileId - 1)).path);
            if (target == null) return null;
        }
        return target.objects.get(pathId);
    }

    public static Result sprite(SerializedFile.Obj o) {
        Map<String, Object> m = TypeTreeReader.read(o.file, o);
        Map<String, Object> rd = map(m.get("m_RD"));
        Map<String, Object> texPtr = map(rd == null ? null : rd.get("texture"));
        Map<String, Object> texRect = map(rd == null ? null : rd.get("textureRect"));
        long settings = num(rd, "settingsRaw");
        SerializedFile texFrom = o.file;
        // a sprite packed into a SpriteAtlas takes its texture and rectangle from the atlas: the one it links to,
        // else the atlases of the same file (sprites list them only by tag)
        java.util.List<SerializedFile.Obj> atlases = new java.util.ArrayList<>();
        SerializedFile.Obj linked = resolve(o.file, map(m.get("m_SpriteAtlas")));
        if (linked != null) atlases.add(linked);
        else if (m.get("m_AtlasTags") instanceof List && !((List<?>) m.get("m_AtlasTags")).isEmpty() && o.file.owner instanceof AssetFile)
            for (SerializedFile.Obj x : ((AssetFile) o.file.owner).objects()) if (x.typeName.equals("SpriteAtlas")) atlases.add(x);
        String key = Json.write(m.get("m_RenderDataKey"));
        search:
        for (SerializedFile.Obj atlas : atlases) {
            try {
                Map<String, Object> a = TypeTreeReader.read(atlas.file, atlas);
                Object rdm = a.get("m_RenderDataMap");
                if (rdm instanceof List) for (Object e : (List<?>) rdm) {
                    List<?> pair = (List<?>) e;
                    if (Json.write(pair.get(0)).equals(key)) {
                        Map<String, Object> info = map(pair.get(1));
                        texPtr = map(info.get("texture"));
                        texRect = map(info.get("textureRect"));
                        settings = num(info, "settingsRaw");
                        texFrom = atlas.file;
                        break search;
                    }
                }
            } catch (RuntimeException ignored) { }
        }
        SerializedFile.Obj tex = resolve(texFrom, texPtr);
        if (tex == null) throw new IllegalStateException("the sprite's texture is in a file that is not open");
        Result t = texture(tex);
        int x = (int) Math.round(dbl(texRect, "x")), y = (int) Math.round(dbl(texRect, "y"));
        int w = (int) Math.round(dbl(texRect, "width")), h = (int) Math.round(dbl(texRect, "height"));
        if (w <= 0 || h <= 0) throw new IllegalStateException("empty sprite");
        int top = t.height - (y + h);
        x = Math.max(0, Math.min(x, t.width - 1));
        top = Math.max(0, Math.min(top, t.height - 1));
        w = Math.min(w, t.width - x);
        h = Math.min(h, t.height - top);
        int[] px = new int[w * h];
        for (int row = 0; row < h; row++) System.arraycopy(t.pixels, (top + row) * t.width + x, px, row * w, w);
        int rotation = (int) ((settings >> 2) & 15);
        boolean packed = (settings & 1) != 0;
        if (packed) {
            switch (rotation) {
                case 1: px = flipH(px, w, h); break;
                case 2: px = flipV(px, w, h); break;
                case 3: px = flipV(flipH(px, w, h), w, h); break;
                case 4: { px = rot90cw(px, w, h); int s = w; w = h; h = s; break; }
                default:
            }
        }
        Result r = new Result();
        r.kind = "image";
        r.mime = "image/png";
        r.fileName = safe(str(m, "m_Name")) + ".png";
        r.pixels = px;
        r.width = w;
        r.height = h;
        r.note = w + "×" + h + " · from " + (tex.name != null ? tex.name : "texture");
        return r;
    }

    private static int[] flipH(int[] p, int w, int h) {
        int[] o = new int[p.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) o[y * w + x] = p[y * w + (w - 1 - x)];
        return o;
    }

    private static int[] flipV(int[] p, int w, int h) {
        int[] o = new int[p.length];
        for (int y = 0; y < h; y++) System.arraycopy(p, (h - 1 - y) * w, o, y * w, w);
        return o;
    }

    /** Rotated 90° clockwise (PIL's ROTATE_270): the result is h wide and w high. */
    private static int[] rot90cw(int[] p, int w, int h) {
        int[] o = new int[p.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) o[x * h + (h - 1 - y)] = p[y * w + x];
        return o;
    }

    public static Result textAsset(SerializedFile.Obj o) {
        Map<String, Object> m = TypeTreeReader.read(o.file, o);
        byte[] b = bytes(m.get("m_Script"));
        if (b == null) b = new byte[0];
        Result r = new Result();
        int i = 0;
        while (i < b.length && i < 64 && (b[i] == ' ' || b[i] == '\n' || b[i] == '\r' || b[i] == '\t' || (b[i] & 0xFF) == 0xEF || (b[i] & 0xFF) == 0xBB || (b[i] & 0xFF) == 0xBF)) i++;
        boolean json = i < b.length && (b[i] == '{' || b[i] == '[');
        boolean text = looksText(b);
        r.kind = text ? (json ? "json" : "text") : "binary";
        r.mime = json ? "application/json" : text ? "text/plain; charset=utf-8" : "application/octet-stream";
        String n = baseName(o);
        boolean hasExt = n.matches(".*\\.[A-Za-z0-9]{1,5}$");
        r.fileName = hasExt ? n : n + (json ? ".json" : text ? ".txt" : ".bytes");
        r.data = b;
        r.note = b.length + " bytes";
        return r;
    }

    static boolean looksText(byte[] b) {
        int n = Math.min(b.length, 4096), bad = 0;
        for (int i = 0; i < n; i++) {
            int c = b[i] & 0xFF;
            if (c == 0) return false;
            if (c < 9 || (c > 13 && c < 32)) bad++;
        }
        return bad * 20 <= n;
    }

    public static Result video(SerializedFile.Obj o) {
        Map<String, Object> m = TypeTreeReader.read(o.file, o);
        byte[] d = streamed(o.file, map(m.get("m_ExternalResources")), "m_Source");
        if (d == null) return json(o);
        String orig = str(m, "m_OriginalPath");
        String ext = orig.contains(".") ? orig.substring(orig.lastIndexOf('.') + 1).toLowerCase() : "mp4";
        Result r = new Result();
        r.kind = "video";
        r.mime = ext.equals("webm") ? "video/webm" : ext.equals("mov") ? "video/quicktime" : "video/mp4";
        r.fileName = baseName(o) + "." + ext;
        r.data = d;
        r.note = d.length / 1024 + " KB";
        return r;
    }

    public static Result audio(SerializedFile.Obj o) {
        Map<String, Object> m = TypeTreeReader.read(o.file, o);
        byte[] d = streamed(o.file, map(m.get("m_Resource")), "m_Source");
        if (d == null) d = bytes(m.get("m_AudioData"));
        if (d == null || d.length == 0) return json(o);
        Result r = new Result();
        r.data = d;
        r.kind = "binary";
        r.mime = "application/octet-stream";
        r.fileName = baseName(o) + ".fsb";
        if (d.length > 4 && d[0] == 'F' && d[1] == 'S' && d[2] == 'B' && d[3] == '5') {
            try {
                Fsb5 f = new Fsb5(d);
                r.note = f.codecName() + " · " + f.frequency + " Hz · " + f.channels + " ch";
                byte[] wav = f.toWav();
                if (wav != null) {
                    r.data = wav;
                    r.kind = "audio";
                    r.mime = "audio/wav";
                    r.fileName = baseName(o) + ".wav";
                } else if (f.codec == 15) {
                    r.note += " — Vorbis: saved as the raw FSB5 file (open it with vgmstream / Foobar2000)";
                }
            } catch (RuntimeException e) {
                r.note = "FSB5: " + e.getMessage();
            }
        } else if (d.length > 4 && d[0] == 'O' && d[1] == 'g' && d[2] == 'g' && d[3] == 'S') {
            r.kind = "audio";
            r.mime = "audio/ogg";
            r.fileName = baseName(o) + ".ogg";
        }
        return r;
    }

    /** FMOD sample bank as Unity stores AudioClips; PCM samples are turned into WAV. */
    static final class Fsb5 {
        int codec, frequency, channels, samples;
        int dataStart, dataLen;
        final byte[] d;

        Fsb5(byte[] d) {
            this.d = d;
            BinReader r = new BinReader(d);
            r.big = false;
            r.skip(4);
            int version = r.s32();
            int count = r.s32();
            int sampleHeaders = r.s32();
            int nameTable = r.s32();
            int dataSize = r.s32();
            codec = r.s32();
            int headerSize = version == 0 ? 64 : 60;
            r.pos = headerSize;
            long raw = r.s64();
            boolean next = (raw & 1) != 0;
            int[] freqs = {4000, 8000, 11000, 11025, 16000, 22050, 24000, 32000, 44100, 48000, 96000};
            int fi = (int) ((raw >> 1) & 0xF);
            frequency = fi < freqs.length ? freqs[fi] : 44100;
            channels = new int[]{1, 2, 6, 8}[(int) ((raw >> 5) & 3)];
            long dataOff = ((raw >> 7) & 0x7FFFFFFL) << 5;
            samples = (int) ((raw >> 34) & 0x3FFFFFFFL);
            while (next) {
                long c = r.u32();
                next = (c & 1) != 0;
                int size = (int) ((c >> 1) & 0xFFFFFF);
                int type = (int) ((c >> 25) & 0x7F);
                int at = r.pos;
                if (type == 1) channels = r.u8();
                else if (type == 2) frequency = r.s32();
                r.pos = at + size;
            }
            int base = headerSize + sampleHeaders + nameTable;
            dataStart = (int) (base + dataOff);
            long end = base + dataSize;
            if (count > 1) end = base + (((r.s64() >> 7) & 0x7FFFFFFL) << 5); // where the next sample starts
            dataLen = (int) Math.max(0, Math.min(d.length, end) - dataStart);
        }

        String codecName() {
            switch (codec) {
                case 1: return "PCM8";
                case 2: return "PCM16";
                case 3: return "PCM24";
                case 4: return "PCM32";
                case 5: return "PCM float";
                case 7: return "IMA ADPCM";
                case 11: return "MPEG";
                case 13: return "HEVAG";
                case 15: return "Vorbis";
                case 16: return "FADPCM";
                default: return "codec " + codec;
            }
        }

        byte[] toWav() {
            int bits, fmt = 1;
            switch (codec) {
                case 1: bits = 8; break;
                case 2: bits = 16; break;
                case 3: bits = 24; break;
                case 4: bits = 32; break;
                case 5: bits = 32; fmt = 3; break;
                default: return null;
            }
            byte[] pcm = java.util.Arrays.copyOfRange(d, dataStart, dataStart + dataLen);
            if (bits == 8) for (int i = 0; i < pcm.length; i++) pcm[i] = (byte) (pcm[i] + 128); // WAV 8-bit is unsigned
            ByteArrayOutputStream o = new ByteArrayOutputStream(pcm.length + 44);
            int blockAlign = channels * bits / 8;
            writeStr(o, "RIFF");
            le32(o, 36 + pcm.length);
            writeStr(o, "WAVEfmt ");
            le32(o, 16);
            le16(o, fmt);
            le16(o, channels);
            le32(o, frequency);
            le32(o, frequency * blockAlign);
            le16(o, blockAlign);
            le16(o, bits);
            writeStr(o, "data");
            le32(o, pcm.length);
            o.write(pcm, 0, pcm.length);
            return o.toByteArray();
        }

        private static void writeStr(ByteArrayOutputStream o, String s) {
            for (int i = 0; i < s.length(); i++) o.write(s.charAt(i));
        }

        private static void le16(ByteArrayOutputStream o, int v) {
            o.write(v & 0xFF);
            o.write((v >> 8) & 0xFF);
        }

        private static void le32(ByteArrayOutputStream o, int v) {
            le16(o, v & 0xFFFF);
            le16(o, (v >>> 16) & 0xFFFF);
        }
    }
}
