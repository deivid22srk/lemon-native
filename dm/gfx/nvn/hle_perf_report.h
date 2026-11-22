// stutter_report.json exporter (hle_perf_report.c; DM_PERF_REPORT=1, default off): one device run's
// frame-time windows (tsummary), the full-session histogram and the [longframe] hitches with their
// attribution, written under $DM_TEMP for the validation plan. Called from hle_nvn.c's summary and
// long-frame paths and from the graceful exit (hle_lifecycle.c); every call is one branch when off.
#ifndef HLE_PERF_REPORT_H
#define HLE_PERF_REPORT_H
#include <stddef.h>

int dm_perf_report_on(void);
// One tsummary window (hle_nvn.c): ft = the window's frame interval times (n of them), tgt = the frame
// target the over-count used, over = frames above it, the on-screen present numbers and footprint/thermal
// as tsummary printed them.
void dm_perf_report_window(unsigned long long frame, const double *ft, unsigned n, double tgt, unsigned over,
                           unsigned pres_n, unsigned drop, unsigned discard, double pres_p99, double fp_mib, int thermal);
// One [longframe] line (hle_nvn.c), stored whole: frame number, whole frame ms, the line's text (every
// attribution field travels; the JSON keeps it as one string).
void dm_perf_report_hitch(unsigned long long frame, double whole_ms, const char *attr);
// Force a write (the graceful exit; dm_host_shutdown). why labels the report's "why" field.
void dm_perf_report_flush(const char *why);
#endif
