// stutter_report.json exporter (DM_PERF_REPORT=1, default off). Aggregates the [nvn] timing summaries
// (hle_nvn.c tsummary, one window per 300 frames) and the [longframe] lines' full attribution into one
// JSON file under $DM_TEMP, rewritten atomically after each window, on each hitch (rate-limited) and at
// the graceful exit, so one device run hands back one evidence file for the validation plan
// (docs/stutter/validation-plan.md). Off by default: when off the only cost is one branch per call.
//
// The file is diagnostics, not a save: it never blocks the frame path (plain fprintf to a temp file on
// the reporting thread, rename on top of the old report). The frame's histogram covers the frames the
// summaries saw (every 300th frame's window); the last partial window is not included.
#include "hle_perf_report.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdatomic.h>
#include <time.h>
#include <unistd.h>

// A window: what tsummary computed for its 300 frames (frame interval times).
typedef struct { unsigned long long frame; unsigned n; unsigned over; unsigned pres_n, drop, discard;
                 double tgt, p50, p95, p99, max_ms, pres_p99, fp_mib; int thermal; } rpt_win;
enum { RPT_WIN_MAX = 240 };          // ~72000 frames; coarsened past that (keep every 2nd, then 4th...)
enum { RPT_HITCH_MAX = 1024 };       // the [longframe] lines, newest kept (past the cap only > 100 ms)
enum { RPT_BUCKETS = 40 };           // 25 ms buckets (0-25, ..., 975-1000, 1000+)

static struct { rpt_win w[RPT_WIN_MAX]; unsigned n, stride; } g_w;
static struct { unsigned long long frame; double ms; char attr[560]; } g_h[RPT_HITCH_MAX];
static unsigned g_hn, g_hnext, g_hdrop;   // g_hn capped at RPT_HITCH_MAX; g_hnext = the ring's write cursor
static unsigned long long g_hist[RPT_BUCKETS + 1], g_frames;
static double g_last_write;

int dm_perf_report_on(void) {
    static int v = -1;
    if (v < 0) { const char *e = getenv("DM_PERF_REPORT"); v = (e && *e && strcmp(e, "0")) ? 1 : 0; }
    return v;
}

static const char *rpt_base(void) {   // $DM_TEMP (the launcher sets it); NULL = nowhere to write
    const char *e = getenv("DM_REPORT_PATH");
    if (e && *e) return e;
    e = getenv("DM_TEMP");
    return (e && *e) ? e : NULL;
}

static void rpt_json_field_str(FILE *f, const char *name, const char *v) {
    if (!v || !*v) { fprintf(f, "\"%s\": null,\n", name); return; }
    fprintf(f, "\"%s\": \"", name);
    for (const unsigned char *p = (const unsigned char *)v; *p; p++) {
        if (*p == '"' || *p == '\\') fprintf(f, "\\%c", *p);
        else if (*p < 0x20) fprintf(f, "\\u%04x", *p);
        else fputc(*p, f);
    }
    fprintf(f, "\",\n");
}

static void rpt_write(const char *why) {
    const char *base = rpt_base();
    if (!base) return;
    char path[512], tmp[560];
    snprintf(path, sizeof path, "%s/stutter_report.json", base);
    snprintf(tmp, sizeof tmp, "%s.tmp", path);
    FILE *f = fopen(tmp, "w");
    if (!f) return;
    fprintf(f, "{\n\"kind\": \"lemon-native stutter report\",\n\"why\": \"%s\",\n", why);
    char ts[64]; time_t t = time(NULL); struct tm tm; localtime_r(&t, &tm);
    strftime(ts, sizeof ts, "%Y-%m-%d %H:%M:%S %z", &tm);
    rpt_json_field_str(f, "written_at", ts);
    rpt_json_field_str(f, "build", getenv("NX_BUILD_TAG"));
    rpt_json_field_str(f, "build_commit", getenv("DM_BUILD_COMMIT"));
    rpt_json_field_str(f, "driver", getenv("DM_VK_DRIVER"));
    rpt_json_field_str(f, "save_dir", getenv("DM_SAVE"));
    fprintf(f, "\"perf_report_flag\": 1,\n\"histogram_bucket_ms\": 25,\n\"frames_histogrammed\": %llu,\n",
            (unsigned long long)g_frames);
    fprintf(f, "\"hitches_logged\": %u,\n\"hitches_dropped_small_past_cap\": %u,\n", g_hn, g_hdrop);
    // frame-time histogram (whole frames the summaries saw)
    fprintf(f, "\"frame_ms_histogram\": [");
    for (unsigned b = 0; b <= RPT_BUCKETS; b++) fprintf(f, "%s%llu", b ? ", " : "", (unsigned long long)g_hist[b]);
    fprintf(f, "],\n\"windows\": [\n");
    for (unsigned i = 0; i < g_w.n; i++) {
        rpt_win *w = &g_w.w[i];
        fprintf(f, "{\"frame\": %llu, \"n\": %u, \"over_target\": %u, \"target_ms\": %.2f, "
                   "\"p50_ms\": %.2f, \"p95_ms\": %.2f, \"p99_ms\": %.2f, \"max_ms\": %.2f, "
                   "\"on_screen\": %u, \"dropped\": %u, \"discarded\": %u, \"on_screen_p99_ms\": %.2f, "
                   "\"footprint_mib\": %.0f, \"thermal\": %d}%s\n",
                w->frame, w->n, w->over, w->tgt, w->p50, w->p95, w->p99, w->max_ms,
                w->pres_n, w->drop, w->discard, w->pres_p99, w->fp_mib, w->thermal,
                i + 1 < g_w.n ? "," : "]");
    }
    if (!g_w.n) fprintf(f, "],\n\"hitches\": [\n");
    else fprintf(f, ",\n\"hitches\": [\n");
    for (unsigned i = 0; i < g_hn; i++) {
        fprintf(f, "{\"frame\": %llu, \"whole_ms\": %.1f, \"attr\": \"", g_h[i].frame, g_h[i].ms);
        for (const unsigned char *p = (const unsigned char *)g_h[i].attr; *p; p++) {
            if (*p == '"' || *p == '\\') fprintf(f, "\\%c", *p);
            else if (*p < 0x20) fputc(' ', f);
            else fputc(*p, f);
        }
        fprintf(f, "\"}%s\n", i + 1 < g_hn ? "," : "]");
    }
    if (!g_hn) fprintf(f, "],\n\"note\": \"empty report: nothing long enough to log\"\n}\n");
    else fprintf(f, "]\n}\n");
    fclose(f);
    rename(tmp, path);   // atomic on the same directory
}

void dm_perf_report_window(unsigned long long frame, const double *ft, unsigned n, double tgt, unsigned over,
                           unsigned pres_n, unsigned drop, unsigned discard, double pres_p99, double fp_mib, int thermal) {
    if (!dm_perf_report_on() || !n) return;
    for (unsigned i = 0; i < n; i++) {
        double v = ft[i]; if (v < 0) continue;
        unsigned b = (unsigned)(v / 25.0); if (b > RPT_BUCKETS) b = RPT_BUCKETS;
        g_hist[b]++; g_frames++;
    }
    rpt_win w = {0};
    double s[300]; unsigned m = n < 300 ? n : 300; memcpy(s, ft, m * sizeof *s);
    for (unsigned i = 0; i < m; i++) for (unsigned j = i + 1; j < m; j++) if (s[j] < s[i]) { double x = s[i]; s[i] = s[j]; s[j] = x; }
    w.frame = frame; w.n = n; w.over = over; w.tgt = tgt;
    w.p50 = s[m / 2]; w.p95 = s[(m * 95) / 100 < m ? (m * 95) / 100 : m - 1];
    w.p99 = s[(m * 99) / 100 < m ? (m * 99) / 100 : m - 1]; w.max_ms = s[m - 1];
    w.pres_n = pres_n; w.drop = drop; w.discard = discard; w.pres_p99 = pres_p99; w.fp_mib = fp_mib; w.thermal = thermal;
    if (g_w.n == RPT_WIN_MAX) {   // coarsen: keep every 2nd row, then every 4th (bounded memory, whole history shape kept)
        g_w.stride *= 2;
        unsigned k = 0;
        for (unsigned i = 0; i < g_w.n; i += g_w.stride) g_w.w[k++] = g_w.w[i];
        g_w.n = k;
    }
    g_w.w[g_w.n++] = w;
    rpt_write("window");
}

void dm_perf_report_hitch(unsigned long long frame, double whole_ms, const char *attr) {
    if (!dm_perf_report_on() || !attr) return;
    if (g_hn >= RPT_HITCH_MAX && whole_ms <= 100.0) { g_hdrop++; return; }   // small hitches go first
    unsigned slot;
    if (g_hn < RPT_HITCH_MAX) slot = g_hn++;
    else { slot = g_hnext; g_hnext = (g_hnext + 1) % RPT_HITCH_MAX; }        // ring: the newest wins
    g_h[slot].frame = frame; g_h[slot].ms = whole_ms;
    snprintf(g_h[slot].attr, sizeof g_h[slot].attr, "%s", attr);
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    double now = ts.tv_sec + ts.tv_nsec / 1e9;
    if (now - g_last_write > 8.0) { g_last_write = now; rpt_write("hitch"); }   // rate-limited: a burst of hitches writes at most once per 8 s
}

void dm_perf_report_flush(const char *why) {
    if (!dm_perf_report_on()) return;
    rpt_write(why);
}
