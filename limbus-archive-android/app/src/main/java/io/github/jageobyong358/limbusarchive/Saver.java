package io.github.jageobyong358.limbusarchive;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Writes files to Downloads/LimbusArchive/… (MediaStore on Android 10+, the folder itself before). */
final class Saver {
    private Saver() { }

    /** @param sub a folder under Downloads/LimbusArchive ("" for none). Returns the path shown to the user. */
    static String save(Context c, byte[] bytes, String mime, String name, String sub) throws IOException {
        if (name == null || name.isEmpty()) name = "limbus-archive";
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!name.contains(".") && mime != null) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime.split(";")[0].trim());
            if (ext != null) name += "." + ext;
        }
        String folder = "LimbusArchive" + (sub == null || sub.isEmpty() ? "" : "/" + sub.replaceAll("[\\\\:*?\"<>|]", "_"));
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            if (mime != null && !mime.isEmpty()) v.put(MediaStore.Downloads.MIME_TYPE, mime.split(";")[0].trim());
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + folder);
            Uri uri = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IOException("no storage");
            try (OutputStream o = c.getContentResolver().openOutputStream(uri)) {
                if (o == null) throw new IOException("no storage");
                o.write(bytes);
            }
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), folder);
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            try (OutputStream o = new FileOutputStream(new File(dir, name))) {
                o.write(bytes);
            }
        }
        return "Downloads/" + folder + "/" + name;
    }
}
