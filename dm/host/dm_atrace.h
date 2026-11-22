// ATrace/Perfetto markers (DM_ATRACE=1, default off): named sections on the paths the stutter triage
// pointed at (dm/stutter/triage.md), so a device trace shows who held each long frame: the replay
// worker's recording, a pipeline build, a big guest file read. Header-only; every use is one branch
// when the flag is off, and a no-op off Android (the Mac's unit tests and host builds include this).
// Capture with: adb shell perfetto -o /data/misc/perfetto-traces/trace --time 60s -b 32mb \
//   sched freq idle am wm gfx view binder driver long_track  (or the "System Tracing" quick settings tile)
#ifndef DM_ATRACE_H
#define DM_ATRACE_H

#ifdef __ANDROID__
#include <stdlib.h>
#include <string.h>
#include <trace.h>
static inline int dm_atrace_on(void) {
    static int v = -1;
    if (v < 0) { const char *e = getenv("DM_ATRACE"); v = (e && *e && strcmp(e, "0")) ? 1 : 0; }
    return v;
}
#define DM_ATRACE_BEGIN(name) do { if (dm_atrace_on()) atrace_begin_section(name); } while (0)
#define DM_ATRACE_END() do { if (dm_atrace_on()) atrace_end_section(); } while (0)
#else
#define DM_ATRACE_BEGIN(name) do { (void)(name); } while (0)
#define DM_ATRACE_END() do { } while (0)
#endif
#endif
