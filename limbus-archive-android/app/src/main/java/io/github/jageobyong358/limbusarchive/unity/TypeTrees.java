package io.github.jageobyong358.limbusarchive.unity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Built-in type trees for files saved without them, the class names, and Unity's common strings.
 * Generated from UnityPy's type tree package for Unity 6000.3.12f1 (the game's version) into typetrees.json.gz.
 */
public final class TypeTrees {
    private static final Map<Integer, String> COMMON = new HashMap<>();
    private static final Map<Integer, String> NAMES = new HashMap<>();
    private static final Map<Integer, List<Object>> RAW = new HashMap<>();
    private static final Map<Integer, TypeTreeNode> BUILT = new HashMap<>();
    private static List<Object> strings = new ArrayList<>();
    private static boolean loaded;

    private TypeTrees() { }

    @SuppressWarnings("unchecked")
    public static synchronized void load(InputStream gz) throws IOException {
        if (loaded) return;
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (InputStream in = new GZIPInputStream(gz)) {
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        }
        Map<String, Object> db = (Map<String, Object>) Json.parse(new String(bo.toByteArray(), StandardCharsets.UTF_8));
        strings = (List<Object>) db.get("strings");
        for (Map.Entry<String, Object> e : ((Map<String, Object>) db.get("common")).entrySet())
            COMMON.put(Integer.parseInt(e.getKey()), (String) e.getValue());
        for (Map.Entry<String, Object> e : ((Map<String, Object>) db.get("names")).entrySet())
            NAMES.put(Integer.parseInt(e.getKey()), (String) e.getValue());
        for (Map.Entry<String, Object> e : ((Map<String, Object>) db.get("classes")).entrySet())
            RAW.put(Integer.parseInt(e.getKey()), (List<Object>) e.getValue());
        loaded = true;
    }

    public static String commonString(int offset) {
        return COMMON.get(offset);
    }

    public static String className(int classId) {
        String n = NAMES.get(classId);
        return n != null ? n : "Class" + classId;
    }

    /** The built-in tree of a class, or null. */
    @SuppressWarnings("unchecked")
    public static synchronized TypeTreeNode node(int classId) {
        if (BUILT.containsKey(classId)) return BUILT.get(classId);
        List<Object> rows = RAW.get(classId);
        TypeTreeNode root = null;
        if (rows != null) {
            List<TypeTreeNode> list = new ArrayList<>(rows.size());
            for (Object o : rows) {
                List<Object> row = (List<Object>) o;
                TypeTreeNode n = new TypeTreeNode();
                n.level = ((Double) row.get(0)).intValue();
                n.type = (String) strings.get(((Double) row.get(1)).intValue());
                n.name = (String) strings.get(((Double) row.get(2)).intValue());
                n.byteSize = ((Double) row.get(3)).intValue();
                n.metaFlag = ((Double) row.get(4)).intValue();
                n.version = ((Double) row.get(5)).intValue();
                list.add(n);
            }
            root = TypeTreeNode.fromList(list);
        }
        BUILT.put(classId, root);
        return root;
    }
}
