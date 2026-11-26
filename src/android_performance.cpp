//-----------------------------------------------------------------------------
//
// Android port: performance hints (ADPF).
//
// Tells Android how long each frame should take (one display refresh, or the
// frame-rate limit) and how long it really took, so the system can raise CPU
// clocks before frames run late instead of after, and keep the game thread on
// fast cores. Without this, phones in power-saving modes tend to clock down
// between frames, which shows up as dips and hitches.
//
// The NDK API exists from Android 13 (API 33). It's looked up at run time so
// the app still runs on older versions, just without hints.
//
//-----------------------------------------------------------------------------

#ifdef __ANDROID__

#include <dlfcn.h>
#include <stdint.h>
#include <time.h>
#include <unistd.h>

#include <SDL2/SDL.h>

#include "c_cvars.h"
#include "printf.h"

EXTERN_CVAR(Int, vid_maxfps)
EXTERN_CVAR(Bool, cl_capfps)

namespace
{

struct APerformanceHintManager;
struct APerformanceHintSession;

typedef APerformanceHintManager *(*GetManagerFn)();
typedef APerformanceHintSession *(*CreateSessionFn)(APerformanceHintManager *, const int32_t *, size_t, int64_t);
typedef int (*UpdateTargetFn)(APerformanceHintSession *, int64_t);
typedef int (*ReportActualFn)(APerformanceHintSession *, int64_t);

int64_t GpuWaitNs;		// this frame's time presenting / waiting for the GPU (game thread only)
int64_t GpuWaitStart;

UpdateTargetFn UpdateTarget;
ReportActualFn ReportActual;
APerformanceHintSession *Session;

int64_t NowNs()
{
	timespec ts;
	clock_gettime(CLOCK_MONOTONIC, &ts);
	return int64_t(ts.tv_sec) * 1000000000 + ts.tv_nsec;
}

// How long a frame should take, in nanoseconds.
int64_t TargetFrameNs()
{
	int hz = 60;
	SDL_DisplayMode mode;
	if (SDL_GetCurrentDisplayMode(0, &mode) == 0 && mode.refresh_rate > 0)
		hz = mode.refresh_rate;
	if (cl_capfps)
		hz = 35; // the game's tic rate
	else if (vid_maxfps > 0 && vid_maxfps < hz)
		hz = vid_maxfps;
	return 1000000000LL / hz;
}

bool Init(int64_t target)
{
	void *lib = dlopen("libandroid.so", RTLD_NOW | RTLD_LOCAL);
	if (lib == nullptr) return false;
	auto getManager = (GetManagerFn)dlsym(lib, "APerformanceHint_getManager");
	auto createSession = (CreateSessionFn)dlsym(lib, "APerformanceHint_createSession");
	UpdateTarget = (UpdateTargetFn)dlsym(lib, "APerformanceHint_updateTargetWorkDuration");
	ReportActual = (ReportActualFn)dlsym(lib, "APerformanceHint_reportActualWorkDuration");
	if (!getManager || !createSession || !UpdateTarget || !ReportActual) return false;

	APerformanceHintManager *manager = getManager();
	if (manager == nullptr) return false;
	const int32_t tid = gettid(); // the game thread: game logic and rendering
	Session = createSession(manager, &tid, 1, target);
	return Session != nullptr;
}

} // namespace

//==========================================================================
//
// The renderers call these around presenting a frame and waiting for the GPU.
//
//==========================================================================

void AndroidPerformance_BeginGpuWait()
{
	GpuWaitStart = NowNs();
}

void AndroidPerformance_EndGpuWait()
{
	if (GpuWaitStart != 0)
		GpuWaitNs += NowNs() - GpuWaitStart;
	GpuWaitStart = 0;
}

//==========================================================================
//
// Called once per frame by the main loop (D_DoomLoop).
//
//==========================================================================

void AndroidPerformance_FrameDone()
{
	static bool tried = false;
	static int64_t lastFrame = 0, lastTargetCheck = 0, target = 0;

	const int64_t now = NowNs();
	if (!tried)
	{
		tried = true;
		target = TargetFrameNs();
		if (Init(target))
			Printf("Android performance hints on (target %.1f ms per frame)\n", target / 1e6);
		lastFrame = lastTargetCheck = now;
		GpuWaitNs = 0;
		return;
	}
	if (Session == nullptr) return;

	const int64_t frame = now - lastFrame;
	lastFrame = now;
	// Report the CPU's share of the frame: leave out the time spent waiting for
	// the GPU and the display. Otherwise a GPU-bound frame looks like slow CPU
	// work, and Android runs the CPU flat out for nothing, taking power and
	// heat budget away from the GPU.
	int64_t work = frame - GpuWaitNs;
	GpuWaitNs = 0;
	if (work < frame / 10) work = frame / 10;
	// Skip the first frame after a pause (app in the background, loading).
	if (frame > 0 && frame < 500000000)
		ReportActual(Session, work);

	// The refresh rate or frame-rate limit can change while playing.
	if (now - lastTargetCheck > 2000000000)
	{
		lastTargetCheck = now;
		const int64_t t = TargetFrameNs();
		if (t != target)
		{
			target = t;
			UpdateTarget(Session, target);
		}
	}
}

#endif // __ANDROID__
