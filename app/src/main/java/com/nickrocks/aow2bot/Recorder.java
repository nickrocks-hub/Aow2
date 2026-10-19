package com.nickrocks.aow2bot;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * One log file per Start, kept on the phone only:
 * Download/AoW2Bot/aow2_YYYYMMDD_HHMMSS.txt, one JSON object per line.
 * Share these files to improve the bot.
 */
final class Recorder {
    private static final String TAG = "AoW2Bot.Rec";
    private final Context ctx;
    private final String name;
    private Uri uri;
    private File file;
    private boolean failed = false;

    Recorder(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.name = "aow2_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".txt";
    }

    String where() { return "Download/AoW2Bot/" + name; }

    /** Writes {"t":now, ...body}. body is JSON members without braces. */
    void event(String body) { line("{\"t\":" + System.currentTimeMillis() + "," + body + "}"); }

    /** Writes a JSON object produced elsewhere, adding the time. */
    void json(String obj) {
        if (obj.startsWith("{")) line("{\"t\":" + System.currentTimeMillis() + "," + obj.substring(1));
        else line(obj);
    }

    synchronized void line(String json) {
        if (failed) return;
        try {
            byte[] bytes = (json + "\n").getBytes(StandardCharsets.UTF_8);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (uri == null) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    v.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                    v.put(MediaStore.Downloads.RELATIVE_PATH, "Download/AoW2Bot");
                    uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) throw new IllegalStateException("could not create log file");
                }
                try (OutputStream os = ctx.getContentResolver().openOutputStream(uri, "wa")) {
                    os.write(bytes);
                }
            } else {
                if (file == null) file = new File(ctx.getExternalFilesDir("logs"), name);
                try (FileOutputStream os = new FileOutputStream(file, true)) { os.write(bytes); }
            }
        } catch (Exception e) {
            Log.e(TAG, "log write failed", e);
            failed = true;
        }
    }

    static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\"";
    }
}
