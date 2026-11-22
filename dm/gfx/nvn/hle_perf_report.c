// stutter_report.json exporter (DM_PERF_REPORT=1, default off). Aggregates the [nvn] timing summaries
// (hle_nvn.c tsummary, one window per 300 frames) and the [longframe] lines' full attribution into one
// JSON file under $DM_TEMP, rewritten atomically after each window, on each hitch (rate-limited) and at
// the graceful exit, so one device run hands back one evidence file for the validation plan
// (docs/stutter/validation-plan.md). Off by default: when off the only cost is one branch per call.
//
// Threading (round-1 Crítico fixes): the callers (the game's presenting thread and the SIGTERM handler)
// only update the accumulators under one mutex and signal a dedicated writer thread; all serialization
// and file I/O happen off the frame path (the caller's cost is a memcpy of the bounded snapshot, tens of
// microseconds per 300-frame window). The writer copies the snapshot under the mutex and writes it
// unlocked, so the presenting thread never waits on flash. The frame's histogram covers the frames the
// summaries saw (every 300th frame's window); the last partial window is not included. The 1024-hitch
// ring keeps the newest and the heaviest; the aggregate counters below keep every hitch's count and sum.
#include "hle_perf_report.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <time.h>
#include <unistd.h>

// A window: what tsummary computed for its 300 frames (frame interval times).
typedef struct { unsigned long long frame; unsigned n; unsigned over; unsigned pres_n, drop, discard;
                 double tgt, p50, p95, p99, max_ms, pres_p99, fp_mib; int thermal; } rpt_win;
enum { RPT_WIN_MAX = 240 };          // ~72000 frames; coarsened past that (keep every 2nd, then 4th...)
enum { RPT_HITCH_MAX = 1024 };       // the [longframe] lines, newest and heaviest kept; totals below keep every one
enum { RPT_BUCKETS = 40 };           // 25 ms buckets (0-25, ..., 975-1000, 1000+)

typedef struct { unsigned long long frame; double ms; char attr[560]; } rpt_hitch;
// The live state, everything under g_mu (the snapshot the writer takes is the same shape)
typedef struct { rpt_win w[RPT_WIN_MAX]; unsigned wn, stride;
                 rpt_hitch h[RPT_HITCH_MAX]; unsigned hn, hnext, hdrop;
                 unsigned long long hist[RPT_BUCKETS + 1], frames;
                 unsigned long long hitches_all, hitches_over100, hitches_over250, hitches_over1000;
                 double hitches_sum_ms; } rpt_state;
static rpt_state g_live;
static pthread_mutex_t g_mu = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t g_cv = PTHREAD_COND_INITIALIZER;
static int g_req, g_final, g_thread_on;   // all under g_mu (g_req: 0 = none pending, 1 = window write, 2 = hitch write, 3 = final write)
static pthread_t g_thr;
static double g_last_write;   // rate limit of the hitch-triggered writes (under g_mu)

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

// The writer thread's only job: serialize the snapshot it was handed and rename it over the old report.
static void rpt_write(const rpt_state *s, const char *why) {
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
            (unsigned long long)s->frames);
    fprintf(f, "\"hitches_logged\": %u,\n\"hitches_dropped_small_past_cap\": %u,\n"
               "\"hitches_total\": %llu,\n\"hitches_sum_ms\": %.1f,\n"
               "\"hitches_over_100ms\": %llu,\n\"hitches_over_250ms\": %llu,\n\"hitches_over_1000ms\": %llu,\n",
            s->hn, s->hdrop, (unsigned long long)s->hitches_all, s->hitches_sum_ms,
            (unsigned long long)s->hitches_over100, (unsigned long long)s->hitches_over250,
            (unsigned long long)s->hitches_over1000);
    // frame-time histogram (whole frames the summaries saw)
    fprintf(f, "\"frame_ms_histogram\": [");
    for (unsigned b = 0; b <= RPT_BUCKETS; b++) fprintf(f, "%s%llu", b ? ", " : "", (unsigned long long)s->hist[b]);
    fprintf(f, "],\n\"windows\": [\n");
    for (unsigned i = 0; i < s->wn; i++) {
        const rpt_win *w = &s->w[i];
        fprintf(f, "{\"frame\": %llu, \"n\": %u, \"over_target\": %u, \"target_ms\": %.2f, "
                   "\"p50_ms\": %.2f, \"p95_ms\": %.2f, \"p99_ms\": %.2f, \"max_ms\": %.2f, "
                   "\"on_screen\": %u, \"dropped\": %u, \"discarded\": %u, \"on_screen_p99_ms\": %.2f, "
                   "\"footprint_mib\": %.0f, \"thermal\": %d}%s\n",
                w->frame, w->n, w->over, w->tgt, w->p50, w->p95, w->p99, w->max_ms,
                w->pres_n, w->drop, w->discard, w->pres_p99, w->fp_mib, w->thermal,
                i + 1 < s->wn ? "," : "]");
    }
    if (!s->wn) fprintf(f, "],\n\"hitches\": [\n");
    else fprintf(f, ",\n\"hitches\": [\n");
    for (unsigned i = 0; i < s->hn; i++) {
        fprintf(f, "{\"frame\": %llu, \"whole_ms\": %.1f, \"attr\": \"", s->h[i].frame, s->h[i].ms);
        for (const unsigned char *p = (const unsigned char *)s->h[i].attr; *p; p++) {
            if (*p == '"' || *p == '\\') fprintf(f, "\\%c", *p);
            else if (*p < 0x20) fputc(' ', f);
            else fputc(*p, f);
        }
        fprintf(f, "\"}%s\n", i + 1 < s->hn ? "," : "]");
    }
    if (!s->hn) fprintf(f, "],\n\"note\": \"empty report: nothing long enough to log\"\n}\n");
    else fprintf(f, "]\n}\n");
    fclose(f);
    rename(tmp, path);   // atomic on the same directory
}

static void *rpt_main(void *arg) {
    (void)arg;
    rpt_state *snap = malloc(sizeof *snap);
    if (!snap) { fprintf(stderr, "[RED] perf report writer: the %zu-byte snapshot could not be allocated; the report stops updating\n", sizeof(rpt_state)); return NULL; }
    for (;;) {
        pthread_mutex_lock(&g_mu);
        while (!g_req && !g_final) pthread_cond_wait(&g_cv, &g_mu);
        int req = g_req, final = g_final; g_req = 0;
        memcpy(snap, &g_live, sizeof *snap);   // ~620 KB, microseconds, under the mutex; I/O happens unlocked
        pthread_mutex_unlock(&g_mu);
        rpt_write(snap, final ? "graceful exit" : req == 2 ? "hitch" : "window");
        if (final) break;
    }
    free(snap);
    return NULL;
}

static void rpt_kick(int req) {    // called with g_mu held after an update: the writer serializes when it can
    if (!g_thread_on) {
        if (pthread_create(&g_thr, NULL, rpt_main, NULL) != 0) { fprintf(stderr, "[RED] perf report writer: pthread_create failed, the report stays in memory only\n"); return; }
        g_thread_on = 1;
    }
    if (req > g_req) g_req = req;   // the final write outranks a window write, which outranks a hitch's
    pthread_cond_signal(&g_cv);
}

void dm_perf_report_window(unsigned long long frame, const double *ft, unsigned n, double tgt, unsigned over,
                           unsigned pres_n, unsigned drop, unsigned discard, double pres_p99, double fp_mib, int thermal) {
    if (!dm_perf_report_on() || !n) return;
    rpt_win w = {0};
    double s[300]; unsigned m = n < 300 ? n : 300; memcpy(s, ft, m * sizeof *s);
    for (unsigned i = 0; i < m; i++) for (unsigned j = i + 1; j < m; j++) if (s[j] < s[i]) { double x = s[i]; s[i] = s[j]; s[j] = x; }
    w.frame = frame; w.n = n; w.over = over; w.tgt = tgt;
    w.p50 = s[m / 2]; w.p95 = s[(m * 95) / 100 < m ? (m * 95) / 100 : m - 1];
    w.p99 = s[(m * 99) / 100 < m ? (m * 99) / 100 : m - 1]; w.max_ms = s[m - 1];
    w.pres_n = pres_n; w.drop = drop; w.discard = discard; w.pres_p99 = pres_p99; w.fp_mib = fp_mib; w.thermal = thermal;
    pthread_mutex_lock(&g_mu);
    for (unsigned i = 0; i < n; i++) {
        double v = ft[i]; if (v < 0) continue;
        unsigned b = (unsigned)(v / 25.0); if (b > RPT_BUCKETS) b = RPT_BUCKETS;
        g_live.hist[b]++; g_live.frames++;
    }
    if (g_live.wn == RPT_WIN_MAX) {   // coarsen: keep every 2nd row, then every 4th (bounded memory, the history's shape kept)
        g_live.stride = g_live.stride ? g_live.stride * 2 : 2;   // (round-1 Crítico: stride is zero-initialized, start it at 2)
        unsigned k = 0;
        for (unsigned i = 0; i < g_live.wn; i += g_live.stride) g_live.w[k++] = g_live.w[i];
        g_live.wn = k;
    }
    g_live.w[g_live.wn++] = w;
    rpt_kick(1);
    pthread_mutex_unlock(&g_mu);
}

void dm_perf_report_hitch(unsigned long long frame, double whole_ms, const char *attr) {
    if (!dm_perf_report_on() || !attr) return;
    pthread_mutex_lock(&g_mu);
    g_live.hitches_all++;
    g_live.hitches_sum_ms += whole_ms;
    if (whole_ms > 100) g_live.hitches_over100++;
    if (whole_ms > 250) g_live.hitches_over250++;
    if (whole_ms > 1000) g_live.hitches_over1000++;
    if (g_live.hn >= RPT_HITCH_MAX && whole_ms <= 100.0) { g_live.hdrop++; pthread_mutex_unlock(&g_mu); return; }   // small hitches go first out the ring
    unsigned slot;
    if (g_live.hn < RPT_HITCH_MAX) slot = g_live.hn++;
    else { slot = g_live.hnext; g_live.hnext = (g_live.hnext + 1) % RPT_HITCH_MAX; }   // ring: the newest wins
    g_live.h[slot].frame = frame; g_live.h[slot].ms = whole_ms;
    snprintf(g_live.h[slot].attr, sizeof g_live.h[slot].attr, "%s", attr);
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    double now = ts.tv_sec + ts.tv_nsec / 1e9;
    if (now - g_last_write > 8.0) { g_last_write = now; rpt_kick(2); }   // rate-limited: a burst of hitches writes at most once per 8 s
    pthread_mutex_unlock(&g_mu);
}

void dm_perf_report_flush(const char *why) {
    (void)why;
    if (!dm_perf_report_on()) return;
    pthread_mutex_lock(&g_mu);
    if (!g_thread_on) { pthread_mutex_unlock(&g_mu); return; }   // nothing was reported: no file to write
    g_final = 1;
    pthread_cond_signal(&g_cv);
    pthread_mutex_unlock(&g_mu);
    pthread_join(g_thr, NULL);   // the writer's last write carries "graceful exit" (dm_host_shutdown's 1.5 s fallback bounds it)
    pthread_mutex_lock(&g_mu);   // the flags are reset under the mutex a hitch in the shutdown window might hold
    g_thread_on = 0; g_final = 0; g_req = 0;
    pthread_mutex_unlock(&g_mu);
}
