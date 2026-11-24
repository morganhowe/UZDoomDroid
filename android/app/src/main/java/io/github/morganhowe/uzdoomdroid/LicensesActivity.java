package io.github.morganhowe.uzdoomdroid;

import android.app.Activity;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Open-source licenses and the GPL notices for this build.
 *   D-pad  move / scroll     A  open      B  back      L1 / R1  page up / down
 */
public class LicensesActivity extends Activity {
    private static final String ROOT = "licenses";

    private final List<String> titles = new ArrayList<>();
    private final List<String> paths = new ArrayList<>(); // null = the "About" text

    private ListView list;
    private ScrollView scroller;
    private TextView text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_licenses);
        list = findViewById(R.id.license_list);
        scroller = findViewById(R.id.license_scroll);
        text = findViewById(R.id.license_text);

        add(getString(R.string.about_title), null);
        add("UZDoom: license overview", ROOT + "/uzdoom/README.TXT");
        add("UZDoom: main license", ROOT + "/uzdoom-license.txt");
        add("GNU General Public License v3", ROOT + "/uzdoom/gpl.txt");
        add("GNU Lesser General Public License", ROOT + "/uzdoom/lgpl.txt");
        add("Game content: game support", ROOT + "/content-game-support.md");
        add("Game content: brightmaps", ROOT + "/content-brightmaps.md");
        add("Game content: widescreen graphics", ROOT + "/content-widepix.md");
        add("UZDoom branding", ROOT + "/uzdoom-branding.md");
        add("SDL2 (zlib license)", ROOT + "/sdl2.txt");
        add("OpenAL Soft (LGPL)", ROOT + "/openal-soft.txt");
        add("OpenAL Soft: PFFFT", ROOT + "/openal-soft-pffft.txt");
        add("GCEM (Apache 2.0)", ROOT + "/gcem.txt");
        add("libsndfile (LGPL)", ROOT + "/libsndfile.txt");
        add("mpg123 (LGPL)", ROOT + "/mpg123.txt");
        add("libFLAC (BSD)", ROOT + "/libflac.txt");
        add("libogg (BSD)", ROOT + "/libogg.txt");
        add("libvorbis (BSD)", ROOT + "/libvorbis.txt");
        add("Opus (BSD)", ROOT + "/opus.txt");
        addDir("UZDoom component: ", ROOT + "/uzdoom", Arrays.asList("README.TXT", "gpl.txt", "lgpl.txt"));
        addDir("FM instrument bank: ", ROOT + "/fm_banks", new ArrayList<>());

        list.setAdapter(new ArrayAdapter<>(this, R.layout.list_item_plain, titles));
        list.setOnItemClickListener((p, v, pos, id) -> show(pos));
        list.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { show(pos); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        show(0);
        list.requestFocus();
        list.setSelection(0);
    }

    private void add(String title, String path) {
        if (path != null && !exists(path)) return;
        titles.add(title);
        paths.add(path);
    }

    private void addDir(String prefix, String dir, List<String> skip) {
        try {
            String[] files = getAssets().list(dir);
            if (files == null) return;
            Arrays.sort(files, String.CASE_INSENSITIVE_ORDER);
            for (String f : files) {
                if (!skip.contains(f)) add(prefix + f, dir + "/" + f);
            }
        } catch (IOException ignored) {
        }
    }

    private boolean exists(String path) {
        try (InputStream ignored = getAssets().open(path)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void show(int pos) {
        String path = paths.get(pos);
        text.setText(path == null ? getString(R.string.about_text) : read(getAssets(), path));
        scroller.scrollTo(0, 0);
    }

    private static String read(AssetManager am, String path) {
        try (InputStream in = am.open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            return "Could not read " + path + ": " + e.getMessage();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean first = event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        View focus = getCurrentFocus();
        boolean reading = focus == scroller;

        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!reading) {
                    if (first) {
                        int pos = list.getSelectedItemPosition();
                        if (pos >= 0) show(pos);
                        scroller.requestFocus();
                    }
                    return true;
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (reading) list.requestFocus();
                    else finish();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (first && !reading) scroller.requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (first && reading) list.requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (reading) {
                    if (down) scroller.arrowScroll(code == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : View.FOCUS_DOWN);
                    return true;
                }
                return super.dispatchKeyEvent(event);
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_PAGE_UP:
                if (down) scroller.pageScroll(View.FOCUS_UP);
                return true;
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (down) scroller.pageScroll(View.FOCUS_DOWN);
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }
}
