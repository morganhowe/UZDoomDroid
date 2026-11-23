package io.github.morganhowe.uzdoomdroid;

import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected String[] getLibraries() {
        return new String[] {
            "SDL2",
            "openal", // dlopen()ed by the engine; preloading keeps the lookup trivial
            "main",
        };
    }

    @Override
    protected String[] getArguments() {
        String[] args = getIntent().getStringArrayExtra(EXTRA_ARGS);
        return args != null ? args : new String[0];
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
