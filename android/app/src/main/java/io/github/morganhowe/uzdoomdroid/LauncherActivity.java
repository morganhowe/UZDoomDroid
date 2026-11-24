package io.github.morganhowe.uzdoomdroid;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Game/mod picker. Entirely gamepad driven:
 *   D-pad / left stick  move        A  select/toggle      B  quit
 *   START               play        Y  import files       L1/R1  switch list
 *   SELECT              licenses
 */
public class LauncherActivity extends Activity {
    static final String EXTRA_RELAUNCH_ARGS = "io.github.morganhowe.uzdoomdroid.RELAUNCH_ARGS";

    private static final int REQUEST_IMPORT = 1;
    private static final String PREF_IWAD = "iwad";
    private static final String PREF_MODS = "mods";

    private static final Set<String> MOD_EXTENSIONS = new HashSet<>(Arrays.asList(
            "wad", "pk3", "pk7", "pkz", "zip", "7z", "deh", "bex", "ipk3", "ipk7", "iwad"));
    private static final Set<String> IWAD_ONLY_EXTENSIONS = new HashSet<>(Arrays.asList(
            "ipk3", "ipk7", "iwad"));

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private ListView iwadList;
    private ListView modList;
    private Button playButton;
    private TextView status;

    private final List<File> iwads = new ArrayList<>();
    private final List<File> mods = new ArrayList<>();
    private boolean engineReady = false;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);
        hideSystemBars();
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);

        iwadList = findViewById(R.id.iwad_list);
        modList = findViewById(R.id.mod_list);
        playButton = findViewById(R.id.play_button);
        status = findViewById(R.id.status);

        iwadList.setEmptyView(findViewById(R.id.iwad_empty));
        modList.setEmptyView(findViewById(R.id.mod_empty));
        iwadList.setOnItemClickListener((p, v, pos, id) -> saveSelection());
        modList.setOnItemClickListener((p, v, pos, id) -> saveSelection());

        playButton.setOnClickListener(v -> play());
        findViewById(R.id.import_button).setOnClickListener(v -> importFiles());
        findViewById(R.id.refresh_button).setOnClickListener(v -> refresh());
        findViewById(R.id.licenses_button).setOnClickListener(v -> showLicenses());

        String folder = getString(R.string.wad_folder_hint, displayPath(EngineData.wadDir(this)));
        ((TextView) findViewById(R.id.folder_hint)).setText(folder);
        ((TextView) findViewById(R.id.iwad_empty)).setText(getString(R.string.no_iwads, displayPath(EngineData.wadDir(this))));

        refresh();
        prepareEngine(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (engineReady) maybeRelaunch(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        refresh();
        io.execute(() -> EngineData.scanUserFiles(this));
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // Gamepad handling
    // ------------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean first = down && event.getRepeatCount() == 0;

        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A:
                // Act exactly like D-pad center / OK on the focused view.
                return super.dispatchKeyEvent(new KeyEvent(event.getDownTime(), event.getEventTime(),
                        event.getAction(), KeyEvent.KEYCODE_DPAD_CENTER, event.getRepeatCount(),
                        event.getMetaState(), event.getDeviceId(), event.getScanCode(),
                        event.getFlags(), event.getSource()));
            case KeyEvent.KEYCODE_BUTTON_B:
                if (event.getAction() == KeyEvent.ACTION_UP) finish();
                return true;
            case KeyEvent.KEYCODE_BUTTON_START:
                if (first) play();
                return true;
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                if (first) showLicenses();
                return true;
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (first) importFiles();
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1:
                if (first) focusList(iwadList);
                return true;
            case KeyEvent.KEYCODE_BUTTON_R1:
                if (first) focusList(modList);
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private void focusList(ListView list) {
        if (list.getCount() == 0) return;
        list.requestFocus();
        int pos = list.getCheckedItemPosition();
        if (pos == ListView.INVALID_POSITION) pos = Math.max(0, list.getSelectedItemPosition());
        list.setSelection(pos);
    }

    // ------------------------------------------------------------------
    // Engine data / launching
    // ------------------------------------------------------------------

    private void prepareEngine(Intent intent) {
        playButton.setEnabled(false);
        if (EngineData.isCurrent(this)) {
            engineReady = true;
            playButton.setEnabled(true);
            maybeRelaunch(intent);
            return;
        }
        status.setText(R.string.preparing);
        io.execute(() -> {
            IOException error = null;
            try {
                EngineData.ensureExtracted(this);
            } catch (IOException e) {
                error = e;
            }
            final IOException err = error;
            ui.post(() -> {
                if (err != null) {
                    status.setText(getString(R.string.extract_failed, err.getMessage()));
                    return;
                }
                engineReady = true;
                playButton.setEnabled(true);
                status.setText("");
                maybeRelaunch(getIntent());
            });
        });
    }

    private void maybeRelaunch(Intent intent) {
        String[] args = intent.getStringArrayExtra(EXTRA_RELAUNCH_ARGS);
        if (args == null) return;
        intent.removeExtra(EXTRA_RELAUNCH_ARGS);
        startGame(args);
    }

    private void play() {
        if (!engineReady) return;
        int pos = iwadList.getCheckedItemPosition();
        if (pos == ListView.INVALID_POSITION || pos >= iwads.size()) {
            Toast.makeText(this, iwads.isEmpty() ? R.string.need_iwad_import : R.string.need_iwad_select,
                    Toast.LENGTH_LONG).show();
            focusList(iwadList);
            return;
        }

        List<String> args = new ArrayList<>();
        args.add("-iwad");
        args.add(iwads.get(pos).getAbsolutePath());

        List<String> files = new ArrayList<>();
        SparseBooleanArray checked = modList.getCheckedItemPositions();
        for (int i = 0; i < mods.size(); i++) {
            if (checked != null && checked.get(i)) files.add(mods.get(i).getAbsolutePath());
        }
        if (!files.isEmpty()) {
            args.add("-file");
            args.addAll(files);
        }
        args.addAll(readExtraArgs());

        saveSelection();
        startGame(args.toArray(new String[0]));
    }

    private void showLicenses() {
        startActivity(new Intent(this, LicensesActivity.class));
    }

    private void startGame(String[] args) {
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameActivity.EXTRA_ARGS, args);
        startActivity(intent);
    }

    /** Optional extra command line, one or more per line, in &lt;data folder&gt;/args.txt. */
    private List<String> readExtraArgs() {
        List<String> out = new ArrayList<>();
        File f = new File(EngineData.userDir(this), "args.txt");
        if (!f.isFile()) return out;
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                out.addAll(splitArgs(line));
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    private static List<String> splitArgs(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false, any = false;
        for (char c : line.toCharArray()) {
            if (c == '"') { quoted = !quoted; any = true; }
            else if (Character.isWhitespace(c) && !quoted) {
                if (any) { out.add(cur.toString()); cur.setLength(0); any = false; }
            } else { cur.append(c); any = true; }
        }
        if (any) out.add(cur.toString());
        return out;
    }

    // ------------------------------------------------------------------
    // File lists
    // ------------------------------------------------------------------

    private void refresh() {
        String keepIwad = currentIwadName();
        Set<String> keepMods = currentModNames();

        iwads.clear();
        mods.clear();
        File[] files = EngineData.wadDir(this).listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File f : files) {
                if (!f.isFile()) continue;
                String ext = extension(f.getName());
                if (isIwad(f, ext)) iwads.add(f);
                else if (MOD_EXTENSIONS.contains(ext)) mods.add(f);
            }
        }

        iwadList.setAdapter(new ArrayAdapter<>(this, R.layout.list_item_single, names(iwads)));
        modList.setAdapter(new ArrayAdapter<>(this, R.layout.list_item_multi, names(mods)));

        if (keepIwad == null) keepIwad = prefs.getString(PREF_IWAD, null);
        if (keepMods == null) keepMods = prefs.getStringSet(PREF_MODS, new HashSet<>());

        int iwadPos = iwads.isEmpty() ? -1 : 0;
        for (int i = 0; i < iwads.size(); i++) {
            if (iwads.get(i).getName().equals(keepIwad)) iwadPos = i;
        }
        if (iwadPos >= 0) iwadList.setItemChecked(iwadPos, true);
        for (int i = 0; i < mods.size(); i++) {
            modList.setItemChecked(i, keepMods.contains(mods.get(i).getName()));
        }

        View focused = getCurrentFocus();
        if (focused == null || focused == iwadList || focused == modList) {
            if (!iwads.isEmpty()) focusList(iwadList);
            else findViewById(R.id.import_button).requestFocus();
        }
    }

    private String currentIwadName() {
        if (iwadList.getAdapter() == null) return null;
        int pos = iwadList.getCheckedItemPosition();
        return pos >= 0 && pos < iwads.size() ? iwads.get(pos).getName() : null;
    }

    private Set<String> currentModNames() {
        if (modList.getAdapter() == null) return null;
        Set<String> out = new HashSet<>();
        SparseBooleanArray checked = modList.getCheckedItemPositions();
        for (int i = 0; i < mods.size(); i++) {
            if (checked != null && checked.get(i)) out.add(mods.get(i).getName());
        }
        return out;
    }

    private void saveSelection() {
        SharedPreferences.Editor e = prefs.edit();
        String iwad = currentIwadName();
        if (iwad != null) e.putString(PREF_IWAD, iwad);
        Set<String> sel = currentModNames();
        if (sel != null) e.putStringSet(PREF_MODS, sel);
        e.apply();
    }

    private static boolean isIwad(File f, String ext) {
        if (IWAD_ONLY_EXTENSIONS.contains(ext)) return true;
        if (!ext.equals("wad")) return false;
        byte[] magic = new byte[4];
        try (InputStream in = new FileInputStream(f)) {
            if (in.read(magic) != 4) return false;
        } catch (IOException e) {
            return false;
        }
        return magic[0] == 'I' && magic[1] == 'W' && magic[2] == 'A' && magic[3] == 'D';
    }

    private static List<String> names(List<File> files) {
        List<String> out = new ArrayList<>();
        for (File f : files) out.add(f.getName());
        return out;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String displayPath(File f) {
        String p = f.getAbsolutePath();
        int i = p.indexOf("/Android/data/");
        return i >= 0 ? p.substring(i + 1) : p;
    }

    // ------------------------------------------------------------------
    // Import through the system file picker
    // ------------------------------------------------------------------

    private void importFiles() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(intent, REQUEST_IMPORT);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.no_picker, displayPath(EngineData.wadDir(this))),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK || data == null) return;

        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) return;

        status.setText(R.string.importing);
        io.execute(() -> {
            int ok = 0;
            for (Uri uri : uris) {
                if (copyIn(uri)) ok++;
            }
            final int copied = ok;
            ui.post(() -> {
                status.setText(getResources().getQuantityString(R.plurals.imported, copied, copied));
                refresh();
            });
        });
    }

    private boolean copyIn(Uri uri) {
        String name = queryName(uri);
        if (name == null) return false;
        name = name.replace('/', '_');
        File dest = new File(EngineData.wadDir(this), name);
        File tmp = new File(dest.getPath() + ".part");
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) return false;
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (IOException | SecurityException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        //noinspection ResultOfMethodCallIgnored
        dest.delete();
        return tmp.renameTo(dest);
    }

    private String queryName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[] { OpenableColumns.DISPLAY_NAME },
                null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (SecurityException ignored) {
        }
        return uri.getLastPathSegment();
    }

    // ------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }
}
