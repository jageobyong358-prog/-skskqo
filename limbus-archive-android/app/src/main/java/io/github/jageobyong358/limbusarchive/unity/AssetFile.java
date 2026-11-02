package io.github.jageobyong358.limbusarchive.unity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** One opened game file: a bundle (with the serialized files and data inside) or a plain serialized file. */
public final class AssetFile implements SerializedFile.Container {

    /** Finds files outside this one: a .resS next to a player file, or the bundle holding another CAB. */
    public interface Finder {
        /** Bytes of the file named {@code name} next to this one (null if none). */
        byte[] sibling(AssetFile from, String name);

        /** An opened file containing the serialized file (CAB) named {@code cab} (null if unknown). */
        AssetFile bundleWithCab(String cab);
    }

    public final String name;
    public BundleFile bundle;
    public final List<SerializedFile> files = new ArrayList<>();
    public final List<String> errors = new ArrayList<>();
    private final Finder finder;
    private final int size;

    public AssetFile(String name, byte[] data, Finder finder) throws java.io.IOException {
        this.name = name;
        this.finder = finder;
        this.size = data.length;
        if (BundleFile.isBundle(data)) {
            bundle = BundleFile.read(data, false);
            for (BundleFile.Node n : bundle.nodes) {
                if (n.offset + n.size > bundle.data.length) continue;
                if (SerializedFile.looks(bundle.data, (int) n.offset, (int) n.size)) {
                    try {
                        SerializedFile sf = new SerializedFile(n.name(), bundle.data, (int) n.offset, (int) n.size);
                        sf.owner = this;
                        files.add(sf);
                    } catch (RuntimeException e) {
                        errors.add(n.name() + ": " + e);
                    }
                }
            }
        } else if (SerializedFile.looks(data, 0, data.length)) {
            SerializedFile sf = new SerializedFile(name, data, 0, data.length);
            sf.owner = this;
            files.add(sf);
        } else {
            throw new java.io.IOException("not a Unity file");
        }
        for (SerializedFile sf : files) describe(sf);
    }

    /** Names, and container paths from the AssetBundle object. */
    @SuppressWarnings("unchecked")
    private void describe(SerializedFile sf) {
        for (SerializedFile.Obj o : sf.objects.values()) {
            try {
                Object n = TypeTreeReader.peek(sf, o, "m_Name");
                if (n != null && !n.toString().isEmpty()) o.name = n.toString();
            } catch (RuntimeException ignored) { }
        }
        for (SerializedFile.Obj o : sf.objects.values()) {
            if (o.classId != 142) continue; // AssetBundle
            try {
                Map<String, Object> ab = TypeTreeReader.read(sf, o);
                Object c = ab.get("m_Container");
                if (!(c instanceof List)) continue;
                for (Object e : (List<Object>) c) {
                    List<Object> pair = (List<Object>) e;
                    String path = pair.get(0).toString();
                    Map<String, Object> info = (Map<String, Object>) pair.get(1);
                    Map<String, Object> asset = (Map<String, Object>) info.get("asset");
                    long pid = ((Number) asset.get("m_PathID")).longValue();
                    SerializedFile.Obj t = sf.objects.get(pid);
                    if (t != null && t.container == null) t.container = path;
                }
            } catch (RuntimeException e) {
                errors.add("AssetBundle: " + e);
            }
        }
    }

    public List<SerializedFile.Obj> objects() {
        List<SerializedFile.Obj> all = new ArrayList<>();
        for (SerializedFile sf : files) all.addAll(sf.objects.values());
        return all;
    }

    public SerializedFile.Obj find(String fileName, long pathId) {
        for (SerializedFile sf : files) if (fileName == null || sf.name.equals(fileName)) {
            SerializedFile.Obj o = sf.objects.get(pathId);
            if (o != null) return o;
        }
        return null;
    }

    /** The names of the serialized files inside (CAB-…), for references from other bundles. */
    public List<String> cabNames() {
        List<String> l = new ArrayList<>();
        for (SerializedFile sf : files) l.add(sf.name);
        return l;
    }

    private static String base(String p) {
        int i = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
        return i >= 0 ? p.substring(i + 1) : p;
    }

    @Override
    public byte[] resource(SerializedFile from, String path) {
        String b = base(path);
        if (bundle != null) {
            for (BundleFile.Node n : bundle.nodes) {
                if (n.name().equalsIgnoreCase(b)) {
                    return java.util.Arrays.copyOfRange(bundle.data, (int) n.offset, (int) (n.offset + n.size));
                }
            }
        }
        if (finder != null) {
            byte[] s = finder.sibling(this, b);
            if (s != null) return s;
            // the data file of a CAB in another bundle
            String cab = b.replaceAll("\\.(resS|resource)$", "");
            AssetFile other = finder.bundleWithCab(cab);
            if (other != null && other != this) return other.resource(from, path);
        }
        return null;
    }

    /** Raw bytes of a data file starting at offset (only what is needed is copied). */
    public byte[] resourceRange(SerializedFile from, String path, long offset, long size) {
        String b = base(path);
        if (bundle != null) {
            for (BundleFile.Node n : bundle.nodes) {
                if (n.name().equalsIgnoreCase(b) && offset + size <= n.size) {
                    int s = (int) (n.offset + offset);
                    return java.util.Arrays.copyOfRange(bundle.data, s, s + (int) size);
                }
            }
        }
        byte[] all = resource(from, path);
        if (all == null || offset + size > all.length) return null;
        return java.util.Arrays.copyOfRange(all, (int) offset, (int) (offset + size));
    }

    @Override
    public SerializedFile external(SerializedFile from, String path) {
        String b = base(path);
        for (SerializedFile sf : files) if (sf.name.equalsIgnoreCase(b)) return sf;
        if (finder != null) {
            AssetFile other = finder.bundleWithCab(b);
            if (other != null) for (SerializedFile sf : other.files) if (sf.name.equalsIgnoreCase(b)) return sf;
        }
        return null;
    }

    public int size() {
        return size;
    }
}
