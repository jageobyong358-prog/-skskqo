package io.github.jageobyong358.limbusarchive.unity;

import java.nio.charset.StandardCharsets;

/** A Unity string: kept as its bytes (TextAsset scripts can be binary), decoded as UTF-8 when shown. */
public final class UStr {
    public final byte[] bytes;
    private String text;

    public UStr(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public String toString() {
        if (text == null) text = new String(bytes, StandardCharsets.UTF_8);
        return text;
    }
}
