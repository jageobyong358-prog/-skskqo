package io.github.jageobyong358.limbusarchive;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Android shell for limbus-archive.com, the browser copy of Schirke/limbus-archive.
 * The desktop app reads a local Windows install of the game, which a phone does not have,
 * so the app shows the website (patch reports, database, team builder, games...) full screen.
 */
public class MainActivity extends Activity {

    private static final String HOME = "https://limbus-archive.com/";
    private static final int FILE_CHOOSER = 1;

    private WebView web;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private ValueCallback<Uri[]> fileCallback;
    private String lastFailedUrl;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(18, 18, 22));
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(18, 18, 22));
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " LimbusArchiveAndroid/" + versionName());

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener((url, ua, disposition, mime, length) -> download(url, disposition, mime));

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            Uri data = getIntent() != null ? getIntent().getData() : null;
            web.loadUrl(data != null && isSite(data) ? data.toString() : HOME);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri data = intent.getData();
        if (data != null && isSite(data)) web.loadUrl(data.toString());
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** Android 9 and older need the storage permission to write to Downloads. */
    private boolean canWriteDownloads() {
        if (Build.VERSION.SDK_INT >= 29) return true;
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) return true;
        requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
        toast("Allow storage, then tap download again");
        return false;
    }

    private static boolean isSite(Uri u) {
        String h = u.getHost();
        return h != null && (h.equals("limbus-archive.com") || h.endsWith(".limbus-archive.com"));
    }

    private void openExternal(Uri u) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u).addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app can open " + u, Toast.LENGTH_SHORT).show();
        }
    }

    // ---- page navigation -------------------------------------------------------------------

    private class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            String scheme = u.getScheme();
            if ("http".equals(scheme) || "https".equals(scheme)) {
                if (isSite(u)) return false;
                // Links that leave the site (GitHub, YouTube, the wiki...) open in the browser or their app
                if (req.isForMainFrame()) {
                    openExternal(u);
                    return true;
                }
                return false;
            }
            if ("blob".equals(scheme) || "data".equals(scheme) || "about".equals(scheme)) return false;
            if ("intent".equals(scheme)) {
                try {
                    Intent i = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME);
                    i.addCategory(Intent.CATEGORY_BROWSABLE).setComponent(null).setSelector(null);
                    startActivity(i);
                } catch (Exception ignored) { }
                return true;
            }
            openExternal(u);  // mailto:, tel:, discord:, steam: ...
            return true;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
            if (req.isForMainFrame()) {
                lastFailedUrl = req.getUrl().toString();
                view.loadUrl("file:///android_asset/offline.html");
            }
        }
    }

    private class Bridge {
        @JavascriptInterface
        public void retry() {
            runOnUiThread(() -> web.loadUrl(lastFailedUrl != null ? lastFailedUrl : HOME));
        }

        /** Gets the bytes of a blob: download from the page, which only the page can read. */
        @JavascriptInterface
        public void saveBase64(String base64, String mime, String name) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                saveBytes(bytes, mime, name);
            } catch (Exception e) {
                toast("Download failed: " + e.getMessage());
            }
        }
    }

    // ---- full screen video, file pickers ----------------------------------------------------

    private class Chrome extends WebChromeClient {
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            customView = view;
            customViewCallback = callback;
            root.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            web.setVisibility(View.GONE);
            setFullscreenBars(true);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        }

        @Override
        public void onHideCustomView() {
            hideCustomView();
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            try {
                startActivityForResult(params.createIntent(), FILE_CHOOSER);
            } catch (ActivityNotFoundException e) {
                fileCallback = null;
                return false;
            }
            return true;
        }
    }

    private void hideCustomView() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        web.setVisibility(View.VISIBLE);
        setFullscreenBars(false);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
    }

    @SuppressWarnings("deprecation")
    private void setFullscreenBars(boolean on) {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c == null) return;
            if (on) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                c.show(WindowInsets.Type.systemBars());
            }
        } else {
            w.getDecorView().setSystemUiVisibility(on
                    ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    : 0);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // ---- downloads (card PNGs, frame zips, JSON...) ------------------------------------------

    private void download(String url, String disposition, String mime) {
        if (!canWriteDownloads()) return;
        String name = URLUtil.guessFileName(url, disposition, mime);
        if (url.startsWith("data:")) {
            int comma = url.indexOf(',');
            String meta = url.substring(5, comma);
            String m = meta.split(";")[0];
            byte[] bytes = meta.endsWith(";base64")
                    ? Base64.decode(url.substring(comma + 1), Base64.DEFAULT)
                    : Uri.decode(url.substring(comma + 1)).getBytes();
            saveBytes(bytes, m.isEmpty() ? mime : m, name);
        } else if (url.startsWith("blob:")) {
            // The blob lives in the page: read it there and hand the bytes over
            String js = "(async()=>{try{const r=await fetch(" + jsString(url) + ");const b=await r.blob();"
                    + "const d=await new Promise((ok,no)=>{const f=new FileReader();f.onload=()=>ok(f.result);f.onerror=no;f.readAsDataURL(b);});"
                    + "Android.saveBase64(d.substring(d.indexOf(',')+1),b.type||" + jsString(mime) + ","
                    + "(document.querySelector('a[href=\"'+" + jsString(url) + "+'\"]')||{}).download||" + jsString(name) + ");"
                    + "}catch(e){Android.saveBase64('','', '');}})()";
            web.evaluateJavascript(js, null);
        } else {
            try {
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                r.setMimeType(mime);
                r.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "LimbusArchive/" + name);
                ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(r);
                toast("Downloading " + name);
            } catch (Exception e) {
                openExternal(Uri.parse(url));
            }
        }
    }

    private void saveBytes(byte[] bytes, String mime, String name) {
        if (bytes == null || bytes.length == 0) {
            toast("Download failed");
            return;
        }
        if (name == null || name.isEmpty()) name = "limbus-archive";
        if (!name.contains(".") && mime != null) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (ext != null) name += "." + ext;
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                if (mime != null && !mime.isEmpty()) v.put(MediaStore.Downloads.MIME_TYPE, mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LimbusArchive");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new IllegalStateException("no storage");
                try (OutputStream o = getContentResolver().openOutputStream(uri)) {
                    o.write(bytes);
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "LimbusArchive");
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                try (OutputStream o = new FileOutputStream(new File(dir, name))) {
                    o.write(bytes);
                }
            }
            toast("Saved to Downloads/LimbusArchive/" + name);
        } catch (Exception e) {
            toast("Download failed: " + e.getMessage());
        }
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private static String jsString(String s) {
        if (s == null) return "''";
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n") + "'";
    }
}
