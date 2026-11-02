package io.github.jageobyong358.limbusarchive.unity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads an object by its type tree into plain Java values: Map (fields in order), List, Long / Integer /
 * Float / Double / Boolean, {@link UStr} for strings and byte[] for byte arrays.
 */
public final class TypeTreeReader {
    private final SerializedFile file;
    private final BinReader r;
    private boolean inRegistry;
    /** Stop once this top-level field has been read (null: read everything). */
    private final String stopAfter;
    private boolean stopped;

    private TypeTreeReader(SerializedFile file, BinReader r, String stopAfter) {
        this.file = file;
        this.r = r;
        this.stopAfter = stopAfter;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> read(SerializedFile f, SerializedFile.Obj o) {
        TypeTreeNode node = f.nodeOf(o);
        if (node == null) throw new IllegalStateException("no type tree for " + o.typeName);
        return (Map<String, Object>) new TypeTreeReader(f, f.readerOf(o), null).value(node, 0);
    }

    /** Reads only up to the field named {@code field} (cheap name lookups). Null if there is no such field. */
    @SuppressWarnings("unchecked")
    public static Object peek(SerializedFile f, SerializedFile.Obj o, String field) {
        TypeTreeNode node = f.nodeOf(o);
        if (node == null || node.child(field) == null) return null;
        Map<String, Object> m = (Map<String, Object>) new TypeTreeReader(f, f.readerOf(o), field).value(node, 0);
        return m.get(field);
    }

    private Object prim(String type) {
        switch (type) {
            case "SInt8": return (int) r.s8();
            case "UInt8":
            case "char": return r.u8();
            case "short":
            case "SInt16": return (int) r.s16();
            case "unsigned short":
            case "UInt16": return r.u16();
            case "int":
            case "SInt32": return r.s32();
            case "unsigned int":
            case "UInt32":
            case "Type*": return r.u32();
            case "long long":
            case "SInt64":
            case "unsigned long long":
            case "UInt64":
            case "FileSize": return r.s64();
            case "float": return r.f32();
            case "double": return r.f64();
            case "bool": return r.bool();
            case "string": {
                byte[] b = r.byteArray();
                r.align(4);
                return new UStr(b);
            }
            case "TypelessData": return r.byteArray();
            default: return NONE;
        }
    }

    private static final Object NONE = new Object();

    private Object value(TypeTreeNode node, int depth) {
        return value(node, depth, false);
    }

    private Object value(TypeTreeNode node, int depth, boolean noOwnAlign) {
        boolean align = node.aligned() && !noOwnAlign;
        Object v = prim(node.type);
        if (v == NONE) {
            if (node.type.equals("pair") && node.children.size() == 2) {
                List<Object> p = new ArrayList<>(2);
                p.add(value(node.children.get(0), depth + 1));
                p.add(value(node.children.get(1), depth + 1));
                v = p;
            } else if (node.type.equals("ReferencedObject")) {
                v = referenced(node, depth);
            } else if (!node.children.isEmpty() && node.children.get(0).type.equals("Array")) {
                TypeTreeNode arr = node.children.get(0);
                if (arr.aligned()) align = true;
                int size = r.s32();
                if (size < 0) throw new IllegalStateException("negative array size");
                v = array(arr.children.get(1), size, depth);
            } else {
                Map<String, Object> m = new LinkedHashMap<>();
                boolean wasInRegistry = inRegistry;
                for (TypeTreeNode c : node.children) {
                    if (c.type.equals("ManagedReferencesRegistry")) {
                        if (inRegistry) continue;
                        inRegistry = true;
                    }
                    m.put(c.name, value(c, depth + 1));
                    if (depth == 0 && stopAfter != null && c.name.equals(stopAfter)) {
                        stopped = true;
                        break;
                    }
                }
                inRegistry = wasInRegistry;
                v = m;
            }
        }
        if (align && !stopped) r.align(4);
        return v;
    }

    private Map<String, Object> referenced(TypeTreeNode node, int depth) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (TypeTreeNode c : node.children) {
            if (c.type.equals("ReferencedObjectData")) {
                Object t = m.get("type");
                if (!(t instanceof Map)) continue;
                Map<?, ?> tm = (Map<?, ?>) t;
                String cls = String.valueOf(tm.get("class")), ns = String.valueOf(tm.get("ns")), asm = String.valueOf(tm.get("asm"));
                if (cls.isEmpty()) continue;
                TypeTreeNode rn = file.refTypeNode(cls, ns, asm);
                if (rn == null) throw new IllegalStateException("referenced type not found: " + cls);
                m.put(c.name, value(rn, depth + 1));
            } else {
                m.put(c.name, value(c, depth + 1));
            }
        }
        return m;
    }

    private Object array(TypeTreeNode item, int size, int depth) {
        switch (item.type) {
            case "UInt8":
            case "SInt8":
            case "char": {
                byte[] b = r.bytes(size);
                if (item.aligned()) r.align(4);
                return b;
            }
            default:
        }
        // nearly every item takes at least a byte: a larger count is corrupt data
        if (size > r.remaining() && item.byteSize != 0) throw new IllegalStateException("array larger than the object");
        // an aligned item type is aligned once after the whole array (as Unity writes it), not after each item
        boolean once = item.aligned();
        List<Object> l = new ArrayList<>(Math.min(size, 1 << 16));
        for (int i = 0; i < size; i++) l.add(value(item, depth + 1, once));
        if (once) r.align(4);
        return l;
    }
}
