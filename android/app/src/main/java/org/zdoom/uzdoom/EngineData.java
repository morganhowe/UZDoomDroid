package org.zdoom.uzdoom;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Engine files (uzdoom.pk3, soundfonts, FM banks) ship as APK assets under
 * "engine/". The engine needs real files, so they are copied to
 * {@code <filesDir>/engine/}, which native code uses as its program directory.
 * They are re-copied whenever the APK is installed or updated.
 */
final class EngineData {
    private static final String ASSET_ROOT = "engine";
    private static final String PREF_STAMP = "engineDataStamp";

    private EngineData() {}

    static File engineDir(Context ctx) {
        return new File(ctx.getFilesDir(), ASSET_ROOT);
    }

    /** User-visible data folder: Android/data/&lt;package&gt;/files (reachable over USB). */
    static File userDir(Context ctx) {
        File ext = ctx.getExternalFilesDir(null);
        return ext != null ? ext : ctx.getFilesDir();
    }

    static File wadDir(Context ctx) {
        File dir = new File(userDir(ctx), "wads");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    static boolean isCurrent(Context ctx) {
        return stamp(ctx) == prefs(ctx).getLong(PREF_STAMP, -1)
                && new File(engineDir(ctx), "uzdoom.pk3").isFile();
    }

    /** Copies the engine files if needed. Call off the UI thread. */
    static void ensureExtracted(Context ctx) throws IOException {
        if (isCurrent(ctx)) return;
        File dir = engineDir(ctx);
        deleteRecursive(dir);
        copyAssetTree(ctx.getAssets(), ASSET_ROOT, dir);
        prefs(ctx).edit().putLong(PREF_STAMP, stamp(ctx)).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("engine", Context.MODE_PRIVATE);
    }

    private static long stamp(Context ctx) {
        try {
            PackageInfo info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return info.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    private static void copyAssetTree(AssetManager am, String path, File dest) throws IOException {
        String[] children = am.list(path);
        if (children == null || children.length == 0) {
            // A file (AssetManager.list returns nothing for files).
            File parent = dest.getParentFile();
            if (parent != null) //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            try (InputStream in = am.open(path); OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        dest.mkdirs();
        for (String child : children) {
            copyAssetTree(am, path + "/" + child, new File(dest, child));
        }
    }

    private static void deleteRecursive(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
