/*
** i_main.cpp
**
** System-specific startup code. Eventually calls D_DoomMain.
**
**---------------------------------------------------------------------------
**
** Copyright 1998-2016 Marisa Heit
** Copyright 2007-2016 Christoph Oelckers
** Copyright 2017-2025 GZDoom Maintainers and Contributors
** Copyright 2025-2026 UZDoom Maintainers and Contributors
**
** SPDX-License-Identifier: GPL-3.0-or-later
**
**---------------------------------------------------------------------------
**
** Code written prior to 2026 is also licensed under:
**
** SPDX-License-Identifier: BSD-3-Clause
**
**---------------------------------------------------------------------------
**
*/

// HEADER FILES ------------------------------------------------------------

#include <SDL2/SDL.h>
#include <csignal>
#include <fcntl.h>
#include <locale.h>
#include <new>
#include <signal.h>
#include <sys/param.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/utsname.h>
#include <unistd.h>

#include "c_console.h"
#include "cmdlib.h"
#include "engineerrors.h"
#include "i_interface.h"
#include "i_system.h"
#include "m_argv.h"
#include "printf.h"
#include "version.h"
#include "zstring.h"

#ifdef __ANDROID__
#include <SDL2/SDL_system.h>
#include <android/log.h>
#include <jni.h>
#include <pthread.h>
#endif

// MACROS ------------------------------------------------------------------

// TYPES -------------------------------------------------------------------

// EXTERNAL FUNCTION PROTOTYPES --------------------------------------------

extern "C" int cc_install_handlers(int, char**, int, int*, const char*, int(*)(char*, char*));

#ifdef __APPLE__
void Mac_I_FatalError(const char* errortext);
#endif

#if defined(__linux__) && !defined(__ANDROID__)
void Linux_I_FatalError(const char* errortext);

static void Linux_I_TryRestart(char **argv)
{
	// TODO: Check how Flatpak interacts with this, too

	const char *appimage = getenv("APPIMAGE");
	if (appimage)
	{
		int appimage_file = open(appimage, O_RDONLY);
		fexecve(appimage_file, argv, environ);
		return;
	}

	int self_file = open("/proc/self/exe", O_RDONLY);
	fexecve(self_file, argv, environ);
}
#endif

#ifdef __ANDROID__
//==========================================================================
//
// Android support
//
// The Java side (io.github.morganhowe.uzdoomdroid.GameActivity) runs the engine in its own
// process, so all engine state is discarded when the game exits.
//
//==========================================================================

// Forward stdout/stderr to logcat; Android discards them otherwise.
static int android_log_pipe[2];

static void *Android_LogThread(void *)
{
	char buf[1024];
	ssize_t len;
	FString line;
	while ((len = read(android_log_pipe[0], buf, sizeof(buf) - 1)) > 0)
	{
		buf[len] = 0;
		line += buf;
		ptrdiff_t nl;
		while ((nl = line.IndexOf('\n')) >= 0)
		{
			__android_log_write(ANDROID_LOG_INFO, "UZDoom", FString(line.GetChars(), nl).GetChars());
			line = line.Mid(nl + 1);
		}
	}
	return nullptr;
}

static void Android_RedirectOutput()
{
	setvbuf(stdout, nullptr, _IOLBF, 0);
	setvbuf(stderr, nullptr, _IONBF, 0);
	if (pipe(android_log_pipe) != 0) return;
	dup2(android_log_pipe[1], STDOUT_FILENO);
	dup2(android_log_pipe[1], STDERR_FILENO);
	pthread_t thread;
	if (pthread_create(&thread, nullptr, Android_LogThread, nullptr) == 0)
		pthread_detach(thread);
}

// Move a file or folder from an old location unless the new one already exists.
static void Android_Migrate(const FString &from, const FString &to)
{
	struct stat st;
	if (stat(from.GetChars(), &st) != 0 || stat(to.GetChars(), &st) == 0) return;
	if (rename(from.GetChars(), to.GetChars()) == 0)
		fprintf(stderr, "Moved %s to %s\n", from.GetChars(), to.GetChars());
}

// Engine data (uzdoom.pk3, soundfonts, ...) is extracted by the launcher into
// <internal storage>/engine/. User data lives in the app's external files
// directory, which is reachable over USB/MTP:
//   wads/          games and mods
//   savegames/     saved games
//   screenshots/   screenshots
//   config/        uzdoom.ini and other settings
// Android does not show folders whose names start with a dot over USB, so
// none of these may be hidden.
static void Android_SetupPaths()
{
	const char *internal = SDL_AndroidGetInternalStoragePath();
	const char *external = SDL_AndroidGetExternalStoragePath();
	if (external == nullptr) external = internal;

	FString home = external ? external : ".";
	FString internalDir = internal ? internal : home.GetChars();
	FString wads = home + "/wads";
	mkdir(wads.GetChars(), 0755);
	mkdir((home + "/config").GetChars(), 0755);

	// Builds up to 0.0.2 used the desktop Linux layout, in hidden folders.
	Android_Migrate(home + "/.config/" GAMENAMELOWERCASE, home + "/config/" GAMENAMELOWERCASE);
	Android_Migrate(home + "/.local/share/games/" GAMENAMELOWERCASE "/savegames", home + "/savegames");
	Android_Migrate(home + "/.local/share/games/" GAMENAMELOWERCASE "/screenshots", home + "/screenshots");

	setenv("HOME", home.GetChars(), 1);
	setenv("XDG_CONFIG_HOME", (home + "/config").GetChars(), 1);
	setenv("XDG_DATA_HOME", (home + "/config/data").GetChars(), 1);
	setenv("XDG_CACHE_HOME", (internalDir + "/cache").GetChars(), 1);
	setenv("DOOMWADDIR", wads.GetChars(), 1);
	if (chdir(home.GetChars()) != 0)
		fprintf(stderr, "Could not change directory to %s\n", home.GetChars());

	progdir = internalDir + "/engine/";
}

// Ask the Java side to start the game again once this process has exited.
static void Android_RequestRestart()
{
	JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
	jobject activity = (jobject)SDL_AndroidGetActivity();
	if (env == nullptr || activity == nullptr) return;

	jclass cls = env->GetObjectClass(activity);
	jmethodID mid = env->GetMethodID(cls, "requestRestart", "()V");
	if (mid != nullptr)
		env->CallVoidMethod(activity, mid);
	if (env->ExceptionCheck())
		env->ExceptionClear();
	env->DeleteLocalRef(cls);
	env->DeleteLocalRef(activity);
}
#endif // __ANDROID__

static void I_TryRestart(char **argv)
{
	// TODO: Mac support

#if defined(__ANDROID__)
	Android_RequestRestart();
#elif defined(__linux__)
	Linux_I_TryRestart(argv);
#endif
}

bool SDL_I_CheckForRestart(void);

// PUBLIC FUNCTION PROTOTYPES ----------------------------------------------
int GameMain();
void SignalHandler(int signal);

// PRIVATE FUNCTION PROTOTYPES ---------------------------------------------

// EXTERNAL DATA DECLARATIONS ----------------------------------------------

extern const char * const BACKEND = "SDL2";

// PUBLIC DATA DEFINITIONS -------------------------------------------------
FString sys_ostype;

// The command line arguments.
FArgs *Args;

// PRIVATE DATA DEFINITIONS ------------------------------------------------


// CODE --------------------------------------------------------------------



static int GetCrashInfo (char *buffer, char *end)
{
	if (sysCallbacks.CrashInfo) sysCallbacks.CrashInfo(buffer, end - buffer, "\n");
	return strlen(buffer);
}

FString I_DetectOS()
{
	FString operatingSystem;

	const char *paths[] = {"/etc/os-release", "/usr/lib/os-release"};

	for (const char *path : paths)
	{
		struct stat dummy;

		if (stat(path, &dummy) != 0)
			continue;

		char cmdline[256];
		snprintf(cmdline, sizeof cmdline, ". %s && echo ${PRETTY_NAME}", path);

		FILE *proc = popen(cmdline, "r");

		if (proc == nullptr)
			continue;

		char distribution[256] = {};
		fread(distribution, sizeof distribution - 1, 1, proc);

		const size_t length = strlen(distribution);

		if (length > 1)
		{
			distribution[length - 1] = '\0';
			operatingSystem = distribution;
		}

		pclose(proc);
		break;
	}

	struct utsname unameInfo;

	if (uname(&unameInfo) == 0)
	{
		const char* const separator = operatingSystem.Len() > 0 ? ", " : "";
		operatingSystem.AppendFormat("%s%s %s on %s", separator, unameInfo.sysname, unameInfo.release, unameInfo.machine);
		sys_ostype.Format("%s %s on %s", unameInfo.sysname, unameInfo.release, unameInfo.machine);
	}

	if (operatingSystem.Len() == 0)
		operatingSystem = "Unknown";

	return operatingSystem;
}

void I_StartupJoysticks();

#define SDL_SETENV(k, v)                                           \
	do {                                                           \
		auto old = SDL_getenv(k);                                  \
		if (old) DEBUG_LOG("%s already set as '%s'", k, old);      \
		if (SDL_setenv(k, v, 0)) DEBUG_LOG("Failed to set %s", k); \
	} while (0);

int main (int argc, char **argv)
{
#if defined(__ANDROID__)
	Android_RedirectOutput();
#elif !defined (__APPLE__)
	{
		int s[4] = { SIGSEGV, SIGILL, SIGFPE, SIGBUS };
		cc_install_handlers(argc, argv, 4, s, GAMENAMELOWERCASE "-crash.log", GetCrashInfo);
	}
#endif // !__APPLE__

	signal(SIGINT, SignalHandler);
	signal(SIGTERM, SignalHandler);
	// signal(SIGHUP, SignalHandler);
	// signal(SIGQUIT, SignalHandler);

	seteuid (getuid ());
	// Set LC_NUMERIC environment variable in case some library decides to
	// clear the setlocale call at least this will be correct.
	// Note that the LANG environment variable is overridden by LC_*
	setenv ("LC_NUMERIC", "C", 1);

	setlocale (LC_ALL, "C");

	// despite the name, this also sets the wayland app_id
	SDL_SETENV("SDL_VIDEO_X11_WMCLASS", APPID);
#ifdef __ANDROID__
	// Controller-only port: stray screen touches must not move the menu cursor,
	// and the Back key is delivered to the engine instead of closing the app.
	SDL_SetHint(SDL_HINT_TOUCH_MOUSE_EVENTS, "0");
	SDL_SetHint(SDL_HINT_ANDROID_TRAP_BACK_BUTTON, "1");
	SDL_SetHint(SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS, "0");
	// SDL otherwise lists the phone's accelerometer as a 3-axis joystick. Its
	// axes go through UZDoom's generic-joystick menu navigation, so holding the
	// device at an angle scrolled menus by itself.
	SDL_SetHint(SDL_HINT_ACCELEROMETER_AS_JOYSTICK, "0");
#endif
#ifdef __ANDROID__
	void I_AndroidInstallLifecycleWatch();
	I_AndroidInstallLifecycleWatch();
#endif
	if (SDL_Init (0) < 0)
	{
		fprintf (stderr, "Could not initialize SDL:\n%s\n", SDL_GetError());
		return -1;
	}

	Args = new FArgs(argc, argv);

#if defined(__ANDROID__)
	Android_SetupPaths();
#elif defined(PROGDIR)
	progdir = PROGDIR;
#else
	char program[PATH_MAX];
	if (realpath (argv[0], program) == NULL)
		strcpy (program, argv[0]);
	char *slash = strrchr (program, '/');
	if (slash != NULL)
	{
		*(slash + 1) = '\0';
		progdir = program;
	}
	else
	{
		progdir = "./";
	}
#endif

	I_StartupJoysticks();

	const int result = GameMain();

	if (SDL_I_CheckForRestart())
	{
		I_TryRestart(argv);
	}

	SDL_SetRelativeMouseMode(SDL_FALSE);
	SDL_Quit();

	return result;
}
