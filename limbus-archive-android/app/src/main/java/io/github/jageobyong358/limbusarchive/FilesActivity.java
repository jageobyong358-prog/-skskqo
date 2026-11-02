package io.github.jageobyong358.limbusarchive;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.jageobyong358.limbusarchive.unity.AssetFile;
import io.github.jageobyong358.limbusarchive.unity.BundleFile;
import io.github.jageobyong358.limbusarchive.unity.Exporter;
import io.github.jageobyong358.limbusarchive.unity.Json;
import io.github.jageobyong358.limbusarchive.unity.SerializedFile;
import io.github.jageobyong358.limbusarchive.unity.TypeTrees;

/**
 * The Files page of the Windows app, on a phone: pick the folder that holds the game's files (the mobile game's
 * Android/data folder, or files copied from a PC install), list its bundles, look inside one, preview and save
 * its textures, sprites, texts, sounds, videos and fonts, and any object's fields as JSON.
 */
public class FilesActivity extends Activity {

    static final String GAME_PACKAGE = "com.ProjectMoon.LimbusCompany";
    private static final String HOST = "la.files";
    private static final int PICK_TREE = 1, PICK_FILES = 2;

    private WebView web;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private SharedPreferences prefs;

    /** A file found in a picked folder (or opened by itself). */
    private static final class Entry {
        String id, uri, name, display, dir;
        long size;
        boolean unity;
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final LinkedHashMap<String, AssetFile> opened = new LinkedHashMap<>(4, 0.75f, true);
    private final Map<String, String> cabIndex = new HashMap<>();
    private volatile boolean cabIndexed;
    private final LinkedHashMap<String, Exporter.Result> results = new LinkedHashMap<>(8, 0.75f, true);
    private int nextId = 1;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("files", MODE_PRIVATE);
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(18, 18, 22));
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        web.addJavascriptInterface(new Bridge(), "Native");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                return serve(u);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) { }
                return true;
            }
        });
        pool.execute(() -> {
            try (InputStream in = getAssets().open("typetrees.json.gz")) {
                TypeTrees.load(in);
            } catch (IOException e) {
                toast("type trees: " + e.getMessage());
            }
        });
        web.loadUrl("https://" + HOST + "/ui/index.html");
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        web.destroy();
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.LA && LA.back()", v -> {
            if (!"true".equals(v)) finish();
        });
    }

    // ---- the page and the objects' files ------------------------------------------------------

    private WebResourceResponse serve(Uri u) {
        String path = u.getPath() == null ? "" : u.getPath();
        try {
            if (path.startsWith("/ui/")) {
                String asset = "files/" + path.substring(4);
                String mime = asset.endsWith(".html") ? "text/html" : asset.endsWith(".js") ? "application/javascript"
                        : asset.endsWith(".css") ? "text/css" : asset.endsWith(".svg") ? "image/svg+xml" : "application/octet-stream";
                return new WebResourceResponse(mime, "utf-8", getAssets().open(asset));
            }
            if (path.equals("/obj")) {
                Exporter.Result r = result(u.getQueryParameter("f"), Integer.parseInt(u.getQueryParameter("s")),
                        Long.parseLong(u.getQueryParameter("p")), u.getQueryParameter("m"));
                byte[] data = bytesOf(r);
                WebResourceResponse resp = new WebResourceResponse(r.mime.split(";")[0], null, new ByteArrayInputStream(data));
                Map<String, String> h = new HashMap<>();
                h.put("Cache-Control", "no-store");
                h.put("Access-Control-Allow-Origin", "*");
                resp.setResponseHeaders(h);
                return resp;
            }
        } catch (Throwable e) {
            WebResourceResponse resp = new WebResourceResponse("text/plain", "utf-8",
                    new ByteArrayInputStream(String.valueOf(e).getBytes()));
            resp.setStatusCodeAndReasonPhrase(500, "Error");
            return resp;
        }
        WebResourceResponse resp = new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
        resp.setStatusCodeAndReasonPhrase(404, "Not found");
        return resp;
    }

    private static byte[] bytesOf(Exporter.Result r) {
        if (r.pixels == null) return r.data;
        if (r.data == null) {
            Bitmap b = Bitmap.createBitmap(r.pixels, r.width, r.height, Bitmap.Config.ARGB_8888);
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
            b.recycle();
            r.data = o.toByteArray();
        }
        return r.data;
    }

    /** mode: "export" (the natural form), "json" or "raw". */
    private Exporter.Result result(String fileId, int sf, long pid, String mode) throws IOException {
        String key = fileId + "|" + sf + "|" + pid + "|" + mode;
        synchronized (results) {
            Exporter.Result r = results.get(key);
            if (r != null) return r;
        }
        SerializedFile.Obj o = object(fileId, sf, pid);
        Exporter.Result r = "json".equals(mode) ? Exporter.json(o) : "raw".equals(mode) ? Exporter.raw(o) : Exporter.export(o);
        synchronized (results) {
            results.put(key, r);
            while (results.size() > 6) results.remove(results.keySet().iterator().next());
        }
        return r;
    }

    private SerializedFile.Obj object(String fileId, int sf, long pid) throws IOException {
        AssetFile a = open(fileId);
        if (sf < 0 || sf >= a.files.size()) throw new IOException("no such file");
        SerializedFile.Obj o = a.files.get(sf).objects.get(pid);
        if (o == null) throw new IOException("no such object");
        return o;
    }

    // ---- reading files ---------------------------------------------------------------------

    private byte[] readAll(String uri, long sizeHint) throws IOException {
        ContentResolver cr = getContentResolver();
        try (InputStream in = cr.openInputStream(Uri.parse(uri))) {
            if (in == null) throw new IOException("cannot open");
            ByteArrayOutputStream o = new ByteArrayOutputStream(sizeHint > 0 && sizeHint < Integer.MAX_VALUE ? (int) sizeHint : 1 << 16);
            byte[] b = new byte[1 << 16];
            int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            return o.toByteArray();
        }
    }

    private byte[] readHead(String uri, int max) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(Uri.parse(uri))) {
            if (in == null) throw new IOException("cannot open");
            byte[] b = new byte[max];
            int n = 0, k;
            while (n < max && (k = in.read(b, n, max - n)) > 0) n += k;
            return n == max ? b : java.util.Arrays.copyOf(b, n);
        }
    }

    private final AssetFile.Finder finder = new AssetFile.Finder() {
        @Override
        public byte[] sibling(AssetFile from, String name) {
            Entry self = entryByName(from.name);
            if (self == null) return null;
            for (Entry e : entries.values()) {
                if (e.dir.equals(self.dir) && e.name.equalsIgnoreCase(name)) {
                    try {
                        return readAll(e.uri, e.size);
                    } catch (IOException ex) {
                        return null;
                    }
                }
            }
            return null;
        }

        @Override
        public AssetFile bundleWithCab(String cab) {
            buildCabIndex();
            String id = cabIndex.get(cab.toLowerCase(Locale.ROOT));
            if (id == null) return null;
            try {
                return open(id);
            } catch (IOException e) {
                return null;
            }
        }
    };

    private Entry entryByName(String fileId) {
        return entries.get(fileId);
    }

    private AssetFile open(String fileId) throws IOException {
        synchronized (opened) {
            AssetFile a = opened.get(fileId);
            if (a != null) return a;
        }
        Entry e = entries.get(fileId);
        if (e == null) throw new IOException("unknown file (scan the folder again)");
        AssetFile a = new AssetFile(fileId, readAll(e.uri, e.size), finder);
        synchronized (opened) {
            opened.put(fileId, a);
            // bundles take a lot of memory once unpacked: keep a few
            while (opened.size() > 3) opened.remove(opened.keySet().iterator().next());
        }
        synchronized (results) {
            results.clear();
        }
        return a;
    }

    /** Which bundle holds which CAB: lets sprites find a texture kept in another bundle. */
    private void buildCabIndex() {
        if (cabIndexed) return;
        synchronized (cabIndex) {
            if (cabIndexed) return;
            List<Entry> list = new ArrayList<>(entries.values());
            int done = 0;
            for (Entry e : list) {
                done++;
                if (!e.unity) continue;
                if (done % 50 == 0) event("indexing", "{\"done\":" + done + ",\"total\":" + list.size() + "}");
                try {
                    byte[] head = readHead(e.uri, 1 << 16);
                    if (BundleFile.isBundle(head)) {
                        BundleFile b;
                        try {
                            b = BundleFile.read(head, true);
                        } catch (Exception ex) {
                            b = BundleFile.read(readAll(e.uri, e.size), true);
                        }
                        for (BundleFile.Node n : b.nodes) cabIndex.put(n.name().toLowerCase(Locale.ROOT), e.id);
                    } else {
                        cabIndex.put(e.name.toLowerCase(Locale.ROOT), e.id);
                    }
                } catch (Exception ignored) { }
            }
            cabIndexed = true;
            event("indexing", "{\"done\":" + list.size() + ",\"total\":" + list.size() + "}");
        }
    }

    // ---- folders ----------------------------------------------------------------------------

    private List<Map<String, Object>> sources() {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            Object v = Json.parse(prefs.getString("sources", "[]"));
            if (v instanceof List) for (Object o : (List<?>) v) {
                @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o;
                out.add(m);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private void saveSources(List<Map<String, Object>> list) {
        prefs.edit().putString("sources", Json.write(list)).apply();
    }

    private static String label(Uri tree) {
        String id = DocumentsContract.getTreeDocumentId(tree);
        int c = id.indexOf(':');
        String path = c >= 0 ? id.substring(c + 1) : id;
        String vol = c >= 0 ? id.substring(0, c) : "";
        return (vol.equals("primary") ? "내장 메모리" : vol) + "/" + path;
    }

    private static boolean likelyUnity(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals("__info") || n.endsWith(".ress") || n.endsWith(".resource") || n.endsWith(".json") || n.endsWith(".txt")
                || n.endsWith(".hash") || n.endsWith(".xml") || n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".mp4")
                || n.endsWith(".ogg") || n.endsWith(".wav") || n.endsWith(".mp3") || n.endsWith(".bank") || n.endsWith(".dll")
                || n.endsWith(".so") || n.endsWith(".log") || n.endsWith(".db") || n.endsWith(".ini") || n.endsWith(".cfg")
                || n.endsWith(".exe") || n.endsWith(".pdb") || n.endsWith(".ttf") || n.endsWith(".otf") || n.endsWith(".dat.bak")
                || n.endsWith(".tmp") || n.endsWith(".lock") || n.endsWith(".manifest") || n.equals("catalog.bin") || n.endsWith(".bin"))
            return false;
        return true;
    }

    /** Lists every file under a picked folder. */
    private List<Entry> scan(Uri tree) {
        ContentResolver cr = getContentResolver();
        List<Entry> out = new ArrayList<>();
        ArrayList<String[]> stack = new ArrayList<>(); // {documentId, relative dir}
        stack.add(new String[]{DocumentsContract.getTreeDocumentId(tree), ""});
        String[] cols = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE};
        int seen = 0;
        while (!stack.isEmpty()) {
            String[] top = stack.remove(stack.size() - 1);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, top[0]);
            try (Cursor c = cr.query(children, cols, null, null, null)) {
                if (c == null) continue;
                while (c.moveToNext()) {
                    String id = c.getString(0), name = c.getString(1), mime = c.getString(2);
                    long size = c.isNull(3) ? 0 : c.getLong(3);
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        stack.add(new String[]{id, top[1].isEmpty() ? name : top[1] + "/" + name});
                    } else {
                        Entry e = new Entry();
                        e.uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString();
                        e.id = e.uri;
                        e.name = name;
                        e.dir = top[1];
                        e.size = size;
                        e.unity = likelyUnity(name) && size > 0;
                        // the Unity cache keeps a bundle as <bundle>/<hash>/__data: show it by its bundle's name
                        if (name.equals("__data")) {
                            String[] parts = top[1].split("/");
                            e.display = parts.length >= 2 ? parts[parts.length - 2] : top[1];
                        } else {
                            e.display = name;
                        }
                        out.add(e);
                        if (++seen % 500 == 0) event("scanning", "{\"files\":" + seen + "}");
                    }
                }
            } catch (Exception ignored) { }
        }
        return out;
    }

    private Uri gameDataUri() {
        return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents",
                "primary:Android/data/" + GAME_PACKAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            event("picked", "null");
            return;
        }
        if (requestCode == PICK_TREE) {
            Uri tree = data.getData();
            if (tree == null) return;
            try {
                getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) { }
            List<Map<String, Object>> list = sources();
            boolean have = false;
            for (Map<String, Object> m : list) if (tree.toString().equals(m.get("uri"))) have = true;
            if (!have) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("uri", tree.toString());
                m.put("name", label(tree));
                list.add(m);
                saveSources(list);
            }
            event("picked", Json.write(tree.toString()));
        } else if (requestCode == PICK_FILES) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            List<Map<String, Object>> files = new ArrayList<>();
            for (Uri u : uris) {
                Entry e = new Entry();
                e.uri = u.toString();
                e.id = e.uri;
                e.dir = "(opened)";
                e.name = u.getLastPathSegment();
                try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        e.name = c.getString(0);
                        e.size = c.isNull(1) ? 0 : c.getLong(1);
                    }
                } catch (Exception ignored) { }
                e.display = e.name;
                e.unity = true;
                entries.put(e.id, e);
                files.add(describe(e));
            }
            event("pickedFiles", Json.write(files));
        }
    }

    private static Map<String, Object> describe(Entry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.id);
        m.put("name", e.name);
        m.put("display", e.display);
        m.put("dir", e.dir);
        m.put("size", e.size);
        m.put("unity", e.unity);
        return m;
    }

    // ---- talking to the page ------------------------------------------------------------------

    private void event(String name, String json) {
        main.post(() -> web.evaluateJavascript("window.LA && LA.event(" + Json.write(name) + "," + json + ")", null));
    }

    private void reply(int cb, String json) {
        main.post(() -> web.evaluateJavascript("LA.done(" + cb + ",null," + json + ")", null));
    }

    private void fail(int cb, Throwable e) {
        String msg = e instanceof OutOfMemoryError ? "메모리가 부족합니다 (파일이 너무 큽니다)" : String.valueOf(e.getMessage() != null ? e.getMessage() : e);
        main.post(() -> web.evaluateJavascript("LA.done(" + cb + "," + Json.write(msg) + ",null)", null));
    }

    /** Android 9 and older need the storage permission to write to Downloads. */
    private void needStorage() throws IOException {
        if (Build.VERSION.SDK_INT >= 29) return;
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        main.post(() -> requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 3));
        throw new IOException("저장 권한을 허용한 뒤 다시 눌러 주세요");
    }

    private interface Job {
        String run() throws Exception;
    }

    private void async(int cb, Job job) {
        pool.execute(() -> {
            try {
                reply(cb, job.run());
            } catch (Throwable e) {
                fail(cb, e);
            }
        });
    }

    private void toast(String s) {
        main.post(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show());
    }

    private class Bridge {
        @JavascriptInterface
        public String info() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sdk", Build.VERSION.SDK_INT);
            m.put("release", Build.VERSION.RELEASE);
            m.put("game", GAME_PACKAGE);
            boolean installed;
            try {
                getPackageManager().getPackageInfo(GAME_PACKAGE, 0);
                installed = true;
            } catch (Exception e) {
                installed = false;
            }
            m.put("gameInstalled", installed);
            return Json.write(m);
        }

        @JavascriptInterface
        public String sources() {
            return Json.write(FilesActivity.this.sources());
        }

        @JavascriptInterface
        public void addFolder(boolean game) {
            main.post(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                if (game && Build.VERSION.SDK_INT >= 26) i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, gameDataUri());
                try {
                    startActivityForResult(i, PICK_TREE);
                } catch (Exception e) {
                    toast("폴더 선택기를 열 수 없습니다: " + e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void pickFiles() {
            main.post(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(i, PICK_FILES);
                } catch (Exception e) {
                    toast("파일 선택기를 열 수 없습니다: " + e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void removeSource(String uri) {
            List<Map<String, Object>> list = FilesActivity.this.sources();
            list.removeIf(m -> uri.equals(m.get("uri")));
            saveSources(list);
            try {
                getContentResolver().releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public void scan(int cb, String uri) {
            async(cb, () -> {
                List<Entry> found = FilesActivity.this.scan(Uri.parse(uri));
                synchronized (cabIndex) {
                    entries.clear();
                    for (Entry e : found) entries.put(e.id, e);
                    cabIndex.clear();
                    cabIndexed = false;
                }
                synchronized (opened) {
                    opened.clear();
                }
                List<Map<String, Object>> out = new ArrayList<>();
                for (Entry e : found) out.add(describe(e));
                return Json.write(out);
            });
        }

        @JavascriptInterface
        public void open(int cb, String fileId) {
            async(cb, () -> {
                AssetFile a = FilesActivity.this.open(fileId);
                List<Object> objs = new ArrayList<>();
                for (int s = 0; s < a.files.size(); s++) {
                    for (SerializedFile.Obj o : a.files.get(s).objects.values()) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("s", s);
                        m.put("p", String.valueOf(o.pathId));
                        m.put("t", o.typeName);
                        m.put("n", o.name);
                        m.put("c", o.container);
                        m.put("z", o.byteSize);
                        objs.add(m);
                    }
                }
                Map<String, Object> m = new LinkedHashMap<>();
                List<Object> files = new ArrayList<>();
                for (SerializedFile sf : a.files) {
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("name", sf.name);
                    f.put("version", sf.version);
                    f.put("unity", sf.unityVersion);
                    f.put("typeTree", sf.typeTreeEnabled);
                    files.add(f);
                }
                m.put("files", files);
                m.put("objects", objs);
                m.put("errors", a.errors);
                m.put("size", a.size());
                return Json.write(m);
            });
        }

        @JavascriptInterface
        public void preview(int cb, String fileId, int sf, String pid) {
            async(cb, () -> {
                Exporter.Result r = result(fileId, sf, Long.parseLong(pid), "export");
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("kind", r.kind);
                m.put("fileName", r.fileName);
                m.put("mime", r.mime);
                m.put("note", r.note);
                if (r.pixels != null) {
                    m.put("width", r.width);
                    m.put("height", r.height);
                }
                if ("text".equals(r.kind) || "json".equals(r.kind)) {
                    int n = Math.min(r.data.length, 400_000);
                    m.put("text", new String(r.data, 0, n, java.nio.charset.StandardCharsets.UTF_8));
                    m.put("cut", r.data.length > n);
                }
                m.put("size", r.data != null ? r.data.length : 0);
                return Json.write(m);
            });
        }

        @JavascriptInterface
        public void save(int cb, String fileId, int sf, String pid, String mode, String folder) {
            async(cb, () -> {
                needStorage();
                Exporter.Result r = result(fileId, sf, Long.parseLong(pid), mode);
                return Json.write(Saver.save(FilesActivity.this, bytesOf(r), r.mime, r.fileName, folder));
            });
        }

        /** Saves the natural export of each listed object ("s:pid" items); progress comes as events. */
        @JavascriptInterface
        public void saveMany(int cb, String fileId, String itemsJson, String folder) {
            async(cb, () -> {
                needStorage();
                List<?> items = (List<?>) Json.parse(itemsJson);
                int ok = 0, failed = 0, i = 0;
                Map<String, Integer> names = new HashMap<>();
                for (Object it : items) {
                    String[] p = it.toString().split(":");
                    i++;
                    try {
                        SerializedFile.Obj o = object(fileId, Integer.parseInt(p[0]), Long.parseLong(p[1]));
                        Exporter.Result r = Exporter.export(o);
                        String name = r.fileName;
                        int k = names.merge(name.toLowerCase(Locale.ROOT), 1, Integer::sum);
                        if (k > 1) {
                            int dot = name.lastIndexOf('.');
                            name = dot > 0 ? name.substring(0, dot) + " (" + k + ")" + name.substring(dot) : name + " (" + k + ")";
                        }
                        Saver.save(FilesActivity.this, bytesOf(r), r.mime, name, folder);
                        ok++;
                    } catch (Throwable e) {
                        failed++;
                    }
                    if (i % 5 == 0 || i == items.size())
                        event("saving", "{\"done\":" + i + ",\"total\":" + items.size() + "}");
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ok", ok);
                m.put("failed", failed);
                m.put("folder", "Downloads/LimbusArchive/" + folder);
                return Json.write(m);
            });
        }

        @JavascriptInterface
        public void openGameApp() {
            main.post(() -> {
                Intent i = getPackageManager().getLaunchIntentForPackage(GAME_PACKAGE);
                if (i != null) startActivity(i);
            });
        }

        @JavascriptInterface
        public void close() {
            main.post(FilesActivity.this::finish);
        }

        @JavascriptInterface
        public int nextId() {
            return nextId++;
        }
    }
}
