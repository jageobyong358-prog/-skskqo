package io.github.jageobyong358.limbusarchive.unity;

import java.util.ArrayList;
import java.util.List;

/** One field of a type tree: its type, name and layout flags, and its children. */
public final class TypeTreeNode {
    public static final int ALIGN = 0x4000;

    public int level;
    public String type = "", name = "";
    public int byteSize, version, metaFlag, typeFlags;
    public long refTypeHash;
    public List<TypeTreeNode> children = new ArrayList<>();

    public boolean aligned() {
        return (metaFlag & ALIGN) != 0;
    }

    public TypeTreeNode child(String n) {
        for (TypeTreeNode c : children) if (c.name.equals(n)) return c;
        return null;
    }

    /** Builds the tree from nodes listed depth first with their levels. */
    public static TypeTreeNode fromList(List<TypeTreeNode> nodes) {
        TypeTreeNode fake = new TypeTreeNode();
        fake.level = -1;
        ArrayList<TypeTreeNode> stack = new ArrayList<>();
        TypeTreeNode parent = fake, prev = fake;
        for (TypeTreeNode n : nodes) {
            if (n.level > prev.level) {
                stack.add(parent);
                parent = prev;
            } else if (n.level < prev.level) {
                while (n.level <= parent.level) parent = stack.remove(stack.size() - 1);
            }
            parent.children.add(n);
            prev = n;
        }
        return fake.children.isEmpty() ? null : fake.children.get(0);
    }

    /** The blob form (serialized files 10, 12+): fixed-size records and a string buffer. */
    public static TypeTreeNode parseBlob(BinReader r, int version) {
        int count = r.s32();
        int strSize = r.s32();
        int recSize = version >= 19 ? 32 : 24;
        int recStart = r.pos;
        r.skip(recSize * count);
        int strStart = r.pos;
        r.skip(strSize);
        int after = r.pos;
        List<TypeTreeNode> list = new ArrayList<>(count);
        BinReader rr = new BinReader(r.buf, recStart, recSize * count);
        rr.big = r.big;
        for (int i = 0; i < count; i++) {
            TypeTreeNode n = new TypeTreeNode();
            n.version = rr.u16();
            n.level = rr.u8();
            n.typeFlags = rr.u8();
            int typeOff = rr.s32();
            int nameOff = rr.s32();
            n.byteSize = rr.s32();
            rr.s32(); // index
            n.metaFlag = rr.s32();
            if (version >= 19) n.refTypeHash = rr.s64();
            n.type = blobString(r.buf, strStart, strSize, typeOff);
            n.name = blobString(r.buf, strStart, strSize, nameOff);
            list.add(n);
        }
        r.pos = after;
        return fromList(list);
    }

    private static String blobString(byte[] buf, int start, int size, int value) {
        if ((value & 0x80000000) == 0) {
            int p = start + value, e = p;
            while (e < start + size && buf[e] != 0) e++;
            return new String(buf, p, e - p, java.nio.charset.StandardCharsets.UTF_8);
        }
        String s = TypeTrees.commonString(value & 0x7FFFFFFF);
        return s != null ? s : String.valueOf(value & 0x7FFFFFFF);
    }

    /** The old form (serialized files before 10, and 11): nodes written recursively. */
    public static TypeTreeNode parseOld(BinReader r, int version, int level) {
        TypeTreeNode n = new TypeTreeNode();
        n.level = level;
        n.type = r.cstr();
        n.name = r.cstr();
        n.byteSize = r.s32();
        if (version == 2) r.s32();
        if (version != 3) r.s32();
        n.typeFlags = r.s32();
        n.version = r.s32();
        if (version != 3) n.metaFlag = r.s32();
        int children = r.s32();
        for (int i = 0; i < children; i++) n.children.add(parseOld(r, version, level + 1));
        return n;
    }
}
