//-----------------------------------------------------------------------------
//
// Android port: shows the automap on a second display (dual-screen handhelds
// such as the AYN Thor).
//
// GameActivity puts an Android Presentation with a SurfaceView on the second
// display and hands its Surface to nativeSetSecondScreen(). Each frame, the game
// thread draws the map into a private 2D drawer (AM_DrawSecondary), flattens the
// result into simple shapes, and a worker thread rasterizes those straight into
// the Surface's buffer. That keeps the second screen independent of the GPU
// backend (Vulkan, OpenGL ES or software), and the game thread never waits on
// the second display.
//
// Only untextured shapes are drawn: map lines, the player arrow, keys, the
// crosshair and background. Textured extras (mark numbers, thing sprites,
// textured floors, background images) are skipped.
//
//-----------------------------------------------------------------------------

#ifdef __ANDROID__

#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>

#include <algorithm>
#include <cmath>
#include <condition_variable>
#include <mutex>
#include <thread>
#include <vector>

#include "c_cvars.h"
#include "gamestate.h"
#include "i_time.h"
#include "printf.h"
#include "v_2ddrawer.h"

EXTERN_CVAR(Bool, am_secondscreen)

bool AM_DrawSecondary(F2DDrawer *drawer, int w, int h);

namespace
{

struct Prim
{
	enum Type : uint8_t { Triangle, Line, Point };
	Type type;
	uint8_t r, g, b, a;
	bool scissor;
	float x[3], y[3];
	int clip[4]; // left, top, right, bottom (exclusive)
};

std::mutex Lock;
std::condition_variable Wake;
ANativeWindow *Window = nullptr;	// guarded by Lock
int WindowW = 0, WindowH = 0;		// guarded by Lock
bool Drawing = false;				// worker is drawing into Window
bool HavePending = false;			// a new frame is waiting for the worker
std::vector<Prim> Pending;			// guarded by Lock
bool WorkerStarted = false;
unsigned Generation = 0;			// bumped for every new surface; guarded by Lock

//==========================================================================
//
// Pixel helpers (RGBX_8888: bytes R, G, B, X)
//
//==========================================================================

inline void Blend(uint32_t &dst, const Prim &p)
{
	if (p.a == 255)
	{
		dst = 0xff000000u | (uint32_t(p.b) << 16) | (uint32_t(p.g) << 8) | p.r;
		return;
	}
	const uint32_t a = p.a, ia = 255 - a;
	const uint32_t dr = dst & 0xff, dg = (dst >> 8) & 0xff, db = (dst >> 16) & 0xff;
	const uint32_t r = (p.r * a + dr * ia) / 255;
	const uint32_t g = (p.g * a + dg * ia) / 255;
	const uint32_t b = (p.b * a + db * ia) / 255;
	dst = 0xff000000u | (b << 16) | (g << 8) | r;
}

struct Target
{
	uint32_t *bits;
	int stride, w, h;
};

void ClipBounds(const Target &t, const Prim &p, int &l, int &tp, int &r, int &b)
{
	l = 0; tp = 0; r = t.w; b = t.h;
	if (p.scissor)
	{
		l = std::max(l, p.clip[0]); tp = std::max(tp, p.clip[1]);
		r = std::min(r, p.clip[2]); b = std::min(b, p.clip[3]);
	}
}

void DrawPoint(const Target &t, const Prim &p)
{
	int l, tp, r, b;
	ClipBounds(t, p, l, tp, r, b);
	const int x = int(std::floor(p.x[0])), y = int(std::floor(p.y[0]));
	if (x >= l && x < r && y >= tp && y < b)
		Blend(t.bits[y * t.stride + x], p);
}

void DrawLine(const Target &t, const Prim &p)
{
	int l, tp, r, b;
	ClipBounds(t, p, l, tp, r, b);
	if (l >= r || tp >= b) return;

	int x0 = int(std::floor(p.x[0])), y0 = int(std::floor(p.y[0]));
	int x1 = int(std::floor(p.x[1])), y1 = int(std::floor(p.y[1]));
	// Lines the automap draws are already clipped to the map window, but guard
	// against anything huge.
	if (std::abs(x1 - x0) > 65536 || std::abs(y1 - y0) > 65536) return;

	const int dx = std::abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
	const int dy = -std::abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
	int err = dx + dy;
	for (;;)
	{
		if (x0 >= l && x0 < r && y0 >= tp && y0 < b)
			Blend(t.bits[y0 * t.stride + x0], p);
		if (x0 == x1 && y0 == y1) break;
		const int e2 = 2 * err;
		if (e2 >= dy) { err += dy; x0 += sx; }
		if (e2 <= dx) { err += dx; y0 += sy; }
	}
}

void DrawTriangle(const Target &t, const Prim &p)
{
	int l, tp, r, b;
	ClipBounds(t, p, l, tp, r, b);

	float x0 = p.x[0], y0 = p.y[0], x1 = p.x[1], y1 = p.y[1], x2 = p.x[2], y2 = p.y[2];
	float area = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
	if (area == 0.f || !std::isfinite(area)) return;
	if (area < 0.f)
	{
		std::swap(x1, x2);
		std::swap(y1, y2);
	}

	const int minx = std::max(l, int(std::floor(std::min({ x0, x1, x2 }))));
	const int maxx = std::min(r - 1, int(std::ceil(std::max({ x0, x1, x2 }))));
	const int miny = std::max(tp, int(std::floor(std::min({ y0, y1, y2 }))));
	const int maxy = std::min(b - 1, int(std::ceil(std::max({ y0, y1, y2 }))));
	if (minx > maxx || miny > maxy) return;

	// Edge functions, sampled at pixel centres. Top-left fill rule, so the two
	// triangles of a quad don't both cover their shared edge (that would show
	// up as a darker diagonal with translucent colours).
	auto edge = [](float ax, float ay, float bx, float by, float px, float py)
	{
		return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
	};
	auto topLeft = [](float ax, float ay, float bx, float by)
	{
		// Y points down. With this winding, a "top" edge goes left and a "left" edge goes down.
		return (ay == by && bx < ax) || (by > ay);
	};
	const bool tl0 = topLeft(x1, y1, x2, y2);
	const bool tl1 = topLeft(x2, y2, x0, y0);
	const bool tl2 = topLeft(x0, y0, x1, y1);

	for (int y = miny; y <= maxy; y++)
	{
		const float py = y + 0.5f;
		uint32_t *row = t.bits + y * t.stride;
		for (int x = minx; x <= maxx; x++)
		{
			const float px = x + 0.5f;
			const float w0 = edge(x1, y1, x2, y2, px, py);
			const float w1 = edge(x2, y2, x0, y0, px, py);
			const float w2 = edge(x0, y0, x1, y1, px, py);
			if ((w0 > 0 || (w0 == 0 && tl0)) && (w1 > 0 || (w1 == 0 && tl1)) && (w2 > 0 || (w2 == 0 && tl2)))
				Blend(row[x], p);
		}
	}
}

//==========================================================================
//
// Worker thread: rasterizes each new frame into the window.
//
//==========================================================================

void WorkerMain()
{
	std::vector<Prim> prims;
	for (;;)
	{
		ANativeWindow *window;
		{
			std::unique_lock<std::mutex> lock(Lock);
			Wake.wait(lock, [] { return HavePending && Window != nullptr; });
			prims.swap(Pending);
			HavePending = false;
			window = Window;
			ANativeWindow_acquire(window);
			Drawing = true;
		}

		ANativeWindow_Buffer buffer;
		if (ANativeWindow_lock(window, &buffer, nullptr) == 0)
		{
			if (buffer.format == WINDOW_FORMAT_RGBA_8888 || buffer.format == WINDOW_FORMAT_RGBX_8888)
			{
				Target t = { static_cast<uint32_t *>(buffer.bits), buffer.stride, buffer.width, buffer.height };
				for (int y = 0; y < t.h; y++)
					std::fill_n(t.bits + y * t.stride, t.w, 0xff000000u);
				for (const Prim &p : prims)
				{
					switch (p.type)
					{
					case Prim::Triangle: DrawTriangle(t, p); break;
					case Prim::Line: DrawLine(t, p); break;
					case Prim::Point: DrawPoint(t, p); break;
					}
				}
			}
			ANativeWindow_unlockAndPost(window);
		}
		ANativeWindow_release(window);

		{
			std::lock_guard<std::mutex> lock(Lock);
			Drawing = false;
		}
		Wake.notify_all();
	}
}

//==========================================================================
//
// Flattens the 2D drawer's untextured commands into Prims.
//
//==========================================================================

void Flatten(F2DDrawer &drawer, std::vector<Prim> &out)
{
	out.clear();
	for (const auto &cmd : drawer.mData)
	{
		if (cmd.isSpecial != SpecialDrawCommand::NotSpecial || cmd.mTexture != nullptr || cmd.shape2DBufInfo != (DShape2DBufferInfo *)nullptr)
			continue;

		auto place = [&cmd](const F2DDrawer::TwoDVertex &v, float &x, float &y)
		{
			if (cmd.useTransform)
			{
				const auto &m = cmd.transform;
				x = float(m.Cells[0][0] * v.x + m.Cells[0][1] * v.y + m.Cells[0][2]);
				y = float(m.Cells[1][0] * v.x + m.Cells[1][1] * v.y + m.Cells[1][2]);
			}
			else
			{
				x = v.x;
				y = v.y;
			}
		};
		auto start = [&cmd](Prim &p, const F2DDrawer::TwoDVertex &v, Prim::Type type)
		{
			p.type = type;
			p.r = v.color0.r; p.g = v.color0.g; p.b = v.color0.b; p.a = v.color0.a;
			p.scissor = (cmd.mFlags & F2DDrawer::DTF_Scissor) != 0;
			for (int i = 0; i < 4; i++) p.clip[i] = cmd.mScissor[i];
		};

		const int nverts = (int)drawer.mVertices.Size();
		switch (cmd.mType)
		{
		case F2DDrawer::DrawTypeLines:
			for (int i = 0; i + 1 < cmd.mVertCount; i += 2)
			{
				const int vi = cmd.mVertIndex + i;
				if (vi + 1 >= nverts) break;
				Prim p;
				start(p, drawer.mVertices[vi], Prim::Line);
				if (p.a == 0) continue;
				place(drawer.mVertices[vi], p.x[0], p.y[0]);
				place(drawer.mVertices[vi + 1], p.x[1], p.y[1]);
				out.push_back(p);
			}
			break;

		case F2DDrawer::DrawTypePoints:
			for (int i = 0; i < cmd.mVertCount; i++)
			{
				const int vi = cmd.mVertIndex + i;
				if (vi >= nverts) break;
				Prim p;
				start(p, drawer.mVertices[vi], Prim::Point);
				if (p.a == 0) continue;
				place(drawer.mVertices[vi], p.x[0], p.y[0]);
				out.push_back(p);
			}
			break;

		case F2DDrawer::DrawTypeTriangles:
			for (int i = 0; i + 2 < cmd.mIndexCount; i += 3)
			{
				const int ii = cmd.mIndexIndex + i;
				if (ii + 2 >= (int)drawer.mIndices.Size()) break;
				const int a = drawer.mIndices[ii], b = drawer.mIndices[ii + 1], c = drawer.mIndices[ii + 2];
				if (a < 0 || b < 0 || c < 0 || a >= nverts || b >= nverts || c >= nverts) continue;
				Prim p;
				start(p, drawer.mVertices[a], Prim::Triangle);
				if (p.a == 0) continue;
				place(drawer.mVertices[a], p.x[0], p.y[0]);
				place(drawer.mVertices[b], p.x[1], p.y[1]);
				place(drawer.mVertices[c], p.x[2], p.y[2]);
				out.push_back(p);
			}
			break;

		default:
			break;
		}
	}
}

} // namespace

//==========================================================================
//
// Called from GameActivity (UI thread) when the second screen's surface
// appears, changes or goes away (surface == null).
//
//==========================================================================

extern "C" JNIEXPORT void JNICALL
Java_io_github_morganhowe_uzdoomdroid_GameActivity_nativeSetSecondScreen(JNIEnv *env, jclass, jobject surface)
{
	ANativeWindow *window = surface != nullptr ? ANativeWindow_fromSurface(env, surface) : nullptr;
	if (window != nullptr)
		ANativeWindow_setBuffersGeometry(window, 0, 0, WINDOW_FORMAT_RGBX_8888);

	std::unique_lock<std::mutex> lock(Lock);
	// Android may destroy the surface as soon as this returns, so wait until the
	// worker is done with the old one.
	Wake.wait(lock, [] { return !Drawing; });
	if (Window != nullptr)
		ANativeWindow_release(Window);
	Window = window;
	WindowW = window ? ANativeWindow_getWidth(window) : 0;
	WindowH = window ? ANativeWindow_getHeight(window) : 0;
	HavePending = false;
	Pending.clear();
	Generation++;

	if (window != nullptr && !WorkerStarted)
	{
		WorkerStarted = true;
		std::thread(WorkerMain).detach();
	}
}

//==========================================================================
//
// Called by the game thread once per frame (D_Display).
//
//==========================================================================

void AndroidSecondScreen_Frame()
{
	static F2DDrawer drawer;
	static std::vector<Prim> prims;
	static uint64_t lastFrame = 0;
	static bool lastBlank = false;
	static unsigned lastGeneration = 0;

	int w, h;
	unsigned generation;
	{
		std::lock_guard<std::mutex> lock(Lock);
		if (Window == nullptr || Drawing || HavePending)
			return; // no second screen, or it's still busy with the last frame
		w = WindowW;
		h = WindowH;
		generation = Generation;
	}
	if (w <= 0 || h <= 0)
		return;

	// The second display usually refreshes at 60 Hz; don't do more work than that.
	const uint64_t now = I_msTime();
	if (now - lastFrame < 15)
		return;

	drawer.Clear();
	drawer.Begin(w, h);
	drawer.SetClipRect(0, 0, w, h);
	bool drew = false;
	if (am_secondscreen)
	{
		try
		{
			drew = AM_DrawSecondary(&drawer, w, h);
		}
		catch (const std::exception &e)
		{
			Printf("Second screen map: %s\n", e.what());
			am_secondscreen = false;
		}
	}
	drawer.End();

	if (!drew)
	{
		// Nothing to show (menus, intermission, map turned off): blank it once.
		if (lastBlank && generation == lastGeneration)
			return;
		prims.clear();
		lastBlank = true;
	}
	else
	{
		Flatten(drawer, prims);
		lastBlank = false;
	}
	lastGeneration = generation;
	lastFrame = now;

	{
		std::lock_guard<std::mutex> lock(Lock);
		if (Window == nullptr || Generation != generation)
		{
			lastBlank = false; // the surface changed meanwhile; redraw next frame
			return;
		}
		Pending.swap(prims);
		HavePending = true;
	}
	Wake.notify_all();
}

#endif // __ANDROID__
