package io.github.morganhowe.uzdoomdroid;

import android.app.Presentation;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
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

    // ---- Second screen (dual-screen handhelds): shows the automap ----

    private static native void nativeSetSecondScreen(Surface surface);

    private DisplayManager displayManager;
    private MapPresentation mapPresentation;
    private boolean resumed;

    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int displayId) { updateSecondScreen(); }
        @Override public void onDisplayRemoved(int displayId) { updateSecondScreen(); }
        @Override public void onDisplayChanged(int displayId) { updateSecondScreen(); }
    };

    /** A non-focusable window on the second display, holding the surface the engine draws the map into. */
    private static final class MapPresentation extends Presentation {
        MapPresentation(Context context, Display display) {
            super(context, display);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Window w = getWindow();
            // Never take input focus or touches away from the game on the main screen.
            w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            SurfaceView view = new SurfaceView(getContext());
            view.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override public void surfaceCreated(SurfaceHolder holder) { }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    setSecondScreen(holder.getSurface());
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    setSecondScreen(null);
                }
            });
            setContentView(view);
            hideBars(w);
        }

        @SuppressWarnings("deprecation")
        private static void hideBars(Window w) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                w.setDecorFitsSystemWindows(false);
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
    }

    private static void setSecondScreen(Surface surface) {
        try {
            nativeSetSecondScreen(surface);
        } catch (UnsatisfiedLinkError e) {
            // The engine library failed to load; SDL shows its own error for that.
        }
    }

    /** The display to show the map on: any display other than the one the game is on. */
    private Display findSecondDisplay() {
        if (displayManager == null) return null;
        int own = getWindowManager().getDefaultDisplay().getDisplayId();
        Display fallback = null;
        for (Display d : displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
            if (d.getDisplayId() != own) return d;
        }
        for (Display d : displayManager.getDisplays()) {
            if (d.getDisplayId() != own && d.getState() != Display.STATE_OFF) {
                fallback = d;
                break;
            }
        }
        return fallback;
    }

    private void updateSecondScreen() {
        Display target = resumed ? findSecondDisplay() : null;
        if (mapPresentation != null
                && (target == null || mapPresentation.getDisplay().getDisplayId() != target.getDisplayId())) {
            mapPresentation.dismiss();
            mapPresentation = null;
        }
        if (target != null && mapPresentation == null) {
            MapPresentation p = new MapPresentation(this, target);
            try {
                p.show();
                mapPresentation = p;
                Log.i("UZDoom", "Showing the map on display " + target.getDisplayId() + " (" + target.getName() + ")");
            } catch (WindowManager.InvalidDisplayException | IllegalArgumentException e) {
                Log.w("UZDoom", "Can't use display " + target.getDisplayId() + " for the map", e);
            }
        }
    }

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

        displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager != null) {
            displayManager.registerDisplayListener(displayListener, ui);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        scheduleHideBars();
        resumed = true;
        updateSecondScreen();
    }

    @Override
    protected void onPause() {
        resumed = false;
        updateSecondScreen(); // takes the map off the second screen while we're away
        super.onPause();
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

    private static native void nativeExtraButton(int joyButton, int keyCode, boolean down);

    /**
     * Engine JoyN number for a controller button that SDL doesn't pass on for game
     * controllers (extra shoulder/back buttons on handhelds), or 0 for a button SDL
     * handles itself.
     */
    private static int extraButtonNumber(KeyEvent ev) {
        int code = ev.getKeyCode();
        if (code >= KeyEvent.KEYCODE_BUTTON_1 && code <= KeyEvent.KEYCODE_BUTTON_16) {
            return 1 + code - KeyEvent.KEYCODE_BUTTON_1;   // Joy1..Joy16
        }
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_C: return 17;
            case KeyEvent.KEYCODE_BUTTON_Z: return 18;
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_BUTTON_R2: {
                // Analog triggers already work through their axes; only digital
                // L2/R2 (no trigger axis) need passing on.
                InputDevice dev = ev.getDevice();
                if (dev != null && (dev.getMotionRange(MotionEvent.AXIS_LTRIGGER) != null
                        || dev.getMotionRange(MotionEvent.AXIS_BRAKE) != null)) {
                    return 0;
                }
                return code == KeyEvent.KEYCODE_BUTTON_L2 ? 19 : 20;
            }
            default: return 0;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        int src = ev.getSource();
        boolean fromController = (src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        int joy = fromController ? extraButtonNumber(ev) : 0;
        if (joy != 0 && sendExtraButton(joy, ev)) {
            return true;
        }
        return super.dispatchKeyEvent(ev);
    }

    /** Hands the button to the engine; false if the engine library isn't loaded. */
    private static boolean sendExtraButton(int joy, KeyEvent ev) {
        try {
            int action = ev.getAction();
            if (action == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) {
                nativeExtraButton(joy, ev.getKeyCode(), true);
            } else if (action == KeyEvent.ACTION_UP) {
                nativeExtraButton(joy, ev.getKeyCode(), false);
            }
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
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
        if (displayManager != null) {
            displayManager.unregisterDisplayListener(displayListener);
        }
        resumed = false;
        updateSecondScreen();
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
