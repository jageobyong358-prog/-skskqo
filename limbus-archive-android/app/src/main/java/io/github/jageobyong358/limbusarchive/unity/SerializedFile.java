package io.github.jageobyong358.limbusarchive.unity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A Unity serialized file (CAB-…, resources.assets, sharedassets0.assets…): its types and objects. */
public final class SerializedFile {

    public static final class Type {
        public int classId;
        public int scriptTypeIndex = -1;
        public TypeTreeNode node;
        public String className, nameSpace, assemblyName;
        HashInfo info;
    }

    public static final class Obj {
        public SerializedFile file;
        public long pathId;
        public long byteStart;
        public int byteSize;
        public int classId;
        public Type type;
        public String typeName;
        public String name;
        public String container;

        public byte[] raw() {
            return java.util.Arrays.copyOfRange(file.buf, (int) (file.base + byteStart), (int) (file.base + byteStart + byteSize));
        }
    }

    public static final class External {
        public String path;

        public String name() {
            int i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            return (i >= 0 ? path.substring(i + 1) : path);
        }
    }

    /** A type tree saved once and shared (serialized files 23+). */
    static final class HashInfo {
        byte[] hash;
        TypeTreeNode node;
        List<byte[]> refHashes = new ArrayList<>();
    }

    public final String name;
    final byte[] buf;
    final int base;
    final int length;
    public int version;
    public boolean big;
    public long dataOffset;
    public String unityVersion = "";
    public int platform;
    public boolean typeTreeEnabled = true;
    public final List<Type> types = new ArrayList<>();
    public final List<Type> refTypes = new ArrayList<>();
    public final List<External> externals = new ArrayList<>();
    public final Map<Long, Obj> objects = new LinkedHashMap<>();
    /** The bundle (or folder) this file came from: resolves .resS data and other CABs. */
    public Container owner;

    public interface Container {
        /** Bytes of a data file such as "archive:/CAB-x/CAB-x.resS" or "sharedassets0.assets.resS" (null if absent). */
        byte[] resource(SerializedFile from, String path);

        /** Another serialized file referenced by this one (null if not loaded). */
        SerializedFile external(SerializedFile from, String path);
    }

    /** True when the bytes look like a serialized file header. */
    public static boolean looks(byte[] b, int off, int len) {
        if (len < 48) return false;
        BinReader r = new BinReader(b, off, len);
        r.big = true;
        long metaSize = r.u32(), fileSize = r.u32();
        int v = r.s32();
        long dataOff = r.u32();
        if (v < 5 || v > 40) return false;
        if (v >= 22) {
            r.skip(4);
            metaSize = r.u32();
            fileSize = r.s64();
            dataOff = r.s64();
        }
        return fileSize == len && dataOff <= fileSize && metaSize < fileSize;
    }

    public SerializedFile(String name, byte[] buf, int off, int len) {
        this.name = name;
        this.buf = buf;
        this.base = off;
        this.length = len;
        BinReader r = new BinReader(buf, off, len);
        r.big = true;
        long metaSize = r.u32();
        long fileSize = r.u32();
        version = r.s32();
        dataOffset = r.u32();
        if (version >= 9) {
            big = r.bool();
            r.skip(3);
            if (version >= 22) {
                metaSize = r.u32();
                fileSize = r.s64();
                dataOffset = r.s64();
                r.s64();
            }
        } else {
            r.seekRel(fileSize - metaSize);
            big = r.bool();
        }
        r.big = big;
        if (version >= 7) unityVersion = r.cstr();
        if (version >= 8) platform = r.s32();
        if (version >= 13) typeTreeEnabled = r.bool();
        int typeCount = r.s32();
        for (int i = 0; i < typeCount; i++) types.add(readType(r, false));
        boolean bigId = false;
        if (version >= 7 && version < 14) bigId = r.s32() != 0;
        int objCount = r.s32();
        for (int i = 0; i < objCount; i++) {
            Obj o = new Obj();
            o.file = this;
            if (bigId) o.pathId = r.s64();
            else if (version < 14) o.pathId = r.s32();
            else {
                r.align(4);
                o.pathId = r.s64();
            }
            o.byteStart = (version >= 22 ? r.s64() : r.u32()) + dataOffset;
            o.byteSize = r.s32();
            int typeId = r.s32();
            if (version < 16) {
                o.classId = r.u16();
                for (Type t : types) if (t.classId == typeId) { o.type = t; break; }
            } else {
                o.type = types.get(typeId);
                o.classId = o.type.classId;
            }
            if (version < 11) r.u16();
            if (version >= 11 && version < 17) {
                int sti = r.s16();
                if (o.type != null) o.type.scriptTypeIndex = sti;
            }
            if (version == 15 || version == 16) r.u8();
            o.typeName = o.type != null && o.type.node != null ? o.type.node.type : TypeTrees.className(o.classId);
            objects.put(o.pathId, o);
        }
        if (version >= 11) {
            int n = r.s32();
            for (int i = 0; i < n; i++) {
                r.s32();
                if (version < 14) r.s32();
                else {
                    r.align(4);
                    r.s64();
                }
            }
        }
        int extCount = r.s32();
        for (int i = 0; i < extCount; i++) {
            External e = new External();
            if (version >= 6) r.cstr();
            if (version >= 5) {
                r.skip(16);
                r.s32();
            }
            e.path = r.cstr();
            externals.add(e);
        }
        if (version >= 20) {
            int n = r.s32();
            for (int i = 0; i < n; i++) refTypes.add(readType(r, true));
        }
        if (version >= 26) {
            Map<String, HashInfo> shared = new HashMap<>();
            int n = r.s32();
            for (int i = 0; i < n; i++) {
                HashInfo h = readHashInfo(r);
                shared.put(hex(h.hash), h);
            }
            for (Type t : types) if (t.info != null) t.node = resolve(t.info.node, t.info.refHashes, shared, new HashMap<>());
            for (Type t : refTypes) if (t.info != null) t.node = resolve(t.info.node, t.info.refHashes, shared, new HashMap<>());
            for (Obj o : objects.values()) if (o.type != null && o.type.node != null) o.typeName = o.type.node.type;
        }
    }

    private Type readType(BinReader r, boolean isRef) {
        Type t = new Type();
        t.classId = r.s32();
        if (version >= 16) r.bool();
        if (version >= 17) t.scriptTypeIndex = r.s16();
        if (version >= 13) {
            if ((isRef && t.scriptTypeIndex >= 0) || (version < 16 && t.classId < 0) || (version >= 16 && t.classId == 114))
                r.skip(16);
            r.skip(16);
        }
        if (typeTreeEnabled) {
            if (version >= 23) {
                t.info = readHashInfo(r);
                t.node = t.info.node;
            } else if (version >= 12 || version == 10) {
                t.node = TypeTreeNode.parseBlob(r, version);
            } else {
                t.node = TypeTreeNode.parseOld(r, version, 0);
            }
            if (version >= 21) {
                if (isRef) {
                    t.className = r.cstr();
                    t.nameSpace = r.cstr();
                    t.assemblyName = r.cstr();
                } else {
                    int n = r.s32();
                    r.skip(4 * n);
                }
            }
        }
        return t;
    }

    private HashInfo readHashInfo(BinReader r) {
        HashInfo h = new HashInfo();
        h.hash = r.bytes(16);
        long size = r.u32();
        if (size >= 8) {
            int end = r.pos + (int) size;
            byte[] magic = r.bytes(4);
            if (magic[0] != 'm' || magic[1] != 'h' || magic[2] != 't' || magic[3] != 't')
                throw new IllegalStateException("bad type tree blob");
            int v = r.s32();
            h.node = TypeTreeNode.parseBlob(r, v);
            long refs = r.u32();
            for (long i = 0; i < refs; i++) h.refHashes.add(r.bytes(16));
            r.pos = end;
        }
        return h;
    }

    private static TypeTreeNode resolve(TypeTreeNode node, List<byte[]> hashes, Map<String, HashInfo> shared,
                                        Map<String, List<TypeTreeNode>> done) {
        if (node == null) return null;
        for (TypeTreeNode c : node.children) resolve(c, hashes, shared, done);
        if ((node.typeFlags & 32) == 0) return node;
        int idx = (int) node.refTypeHash;
        if (idx < 0 || idx >= hashes.size()) return node;
        String key = hex(hashes.get(idx));
        List<TypeTreeNode> kids = done.get(key);
        if (kids == null) {
            HashInfo h = shared.get(key);
            kids = new ArrayList<>();
            done.put(key, kids);
            if (h != null && h.node != null) kids.addAll(resolve(h.node, h.refHashes, shared, done).children);
        }
        node.children = kids;
        return node;
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }

    /** The tree to read an object with: its own, else the built-in one for its class. */
    public TypeTreeNode nodeOf(Obj o) {
        if (o.type != null && o.type.node != null) return o.type.node;
        return TypeTrees.node(o.classId);
    }

    public BinReader readerOf(Obj o) {
        BinReader r = new BinReader(buf, (int) (base + o.byteStart), o.byteSize);
        r.big = big;
        return r;
    }

    public TypeTreeNode refTypeNode(String cls, String ns, String asm) {
        for (Type t : refTypes)
            if (cls.equals(t.className) && ns.equals(t.nameSpace) && asm.equals(t.assemblyName)) return t.node;
        return null;
    }
}
