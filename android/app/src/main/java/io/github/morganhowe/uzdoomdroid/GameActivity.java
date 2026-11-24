package io.github.morganhowe.uzdoomdroid;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import org.libsdl.app.SDLActivity;

/**
 * Hosts the engine. Runs in its own ":game" process (see AndroidManifest.xml):
 * when the engine quits, the process is killed so the next launch starts from
 * a clean slate.
 */
public class GameActivity extends SDLActivity {
    public static final String EXTRA_ARGS = "io.github.morganhowe.uzdoomdroid.ARGS";

    private static volatile boolean sRestartRequested = false;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable hideBars = this::hideSystemBars;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Lay the game out edge to edge, under the (hidden) system bars and any
        // display cutout. Otherwise, after sleep or Home, Android can leave the
        // window laid out around a status bar that is no longer there, showing a
        // grey band at the top.
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        w.setAttributes(lp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.setDecorFitsSystemWindows(false);
        }
        w.setStatusBarColor(0xFF000000);
        w.setNavigationBarColor(0xFF000000);
        hideSystemBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        scheduleHideBars();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) scheduleHideBars();
    }

    // SDL re-applies its own (older) fullscreen flags around focus changes, so
    // hide the bars now and again shortly after, to win any race with it.
    private void scheduleHideBars() {
        ui.removeCallbacks(hideBars);
        hideSystemBars();
        ui.postDelayed(hideBars, 300);
        ui.postDelayed(hideBars, 1000);
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override
    protected String[] getLibraries() {
        return new String[] {
            "SDL2",
            // dlopen()ed by the engine/ZMusic; preloading keeps the lookup trivial
            "openal",
            "sndfile",
            "mpg123",
            "main",
        };
    }

    @Override
    protected String[] getArguments() {
        String[] args = getIntent().getStringArrayExtra(EXTRA_ARGS);
        return args != null ? args : new String[0];
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        boolean handled = super.dispatchGenericMotionEvent(ev); // SDL reads it here
        // Android turns controller stick/hat motion that nobody handled (e.g. while SDL
        // is still detecting a newly connected controller) into synthetic, auto-repeating
        // D-pad key presses. Those can get stuck and scroll menus forever, so always
        // report controller motion as handled.
        if (ev.isFromSource(InputDevice.SOURCE_CLASS_JOYSTICK)) {
            return true;
        }
        return handled;
    }

    /** Called from native code (i_main.cpp) when the engine asks to restart itself. */
    @SuppressWarnings("unused")
    public void requestRestart() {
        sRestartRequested = true;
    }

    @Override
    protected void onDestroy() {
        boolean restart = sRestartRequested;
        String[] args = getArguments();
        super.onDestroy();

        if (isFinishing()) {
            Intent launcher = new Intent(this, LauncherActivity.class);
            launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (restart) {
                launcher.putExtra(LauncherActivity.EXTRA_RELAUNCH_ARGS, args);
            }
            startActivity(launcher);
            // Throw away all native state; the launcher lives in the main process.
            Process.killProcess(Process.myPid());
        }
    }
}
