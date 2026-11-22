#!/usr/bin/env python3
"""Fase 0 triage analyzer: parse [longframe] telemetry + video presentation gaps.

Longframe semantics (dm/gfx/nvn/hle_nvn.c ~2515): a line prints only when
whole frame > 20 ms AND > 1.4x the sliding mean of the last 16 frames, or
GPU > 16.7 ms AND > 1.3x its sliding mean; decided 8 frames later once GPU
time arrived. So it is the adaptive tail + change detector of the frame-time
distribution, not the full distribution.

Outputs (to .work/analysis/): longframes_*.json, log_stats.json,
video_gaps.json, summary.md
"""
import json
import re
import statistics
from pathlib import Path

WORK = Path('/home/z/my-project/lemon-native/.work')
LOGS = WORK / 'logs'
OUT = WORK / 'analysis'
OUT.mkdir(exist_ok=True)

# tolerant sub-regexes: [longframe] lines are assembled from optional sections
SUBS = [
    ('frame',     r'\[longframe\] frame (\d+) \(([\d.]+) s\) whole frame ([\d.]+) ms, submit\+present ([\d.]+) ms, GPU ([\d.]+) ms(?: \(busy; span ([\d.]+) ms, ([\d.]+) waiting for the recording\))?'),
    ('busiest',   r'busiest threads over ([\d.]+) ms: (.*?);'),
    ('pipelines', r'new pipelines (\d+) \+ shader libraries (\d+), ([\d.]+) ms in all'),
    ('uploads',   r'texture uploads (\d+) first \+ (\d+) re-uploads, ([\d.]+) MB / ([\d.]+) ms'),
    ('hybrid',    r'draws with a hybrid pipeline (\d+)'),
    ('waits',     r'waits: GPU fences ([\d.]+), acquire ([\d.]+)'),
    ('cpu',       r"this thread's CPU (-?[\d.]+), process CPU ([\d.]+)"),
    ('faults',    r'page faults (\d+) major / (\d+) minor'),
    ('files',     r'files: read ([\d.]+) MB ([\d.]+) ms, opens ([\d.]+) ms'),
    ('wfaults',   r'write faults (\d+)'),
    ('guest',     r'guest waits ([\d.]+) \(longest ([\d.]+)(?:: ([a-z]+), ended by ([^)]*))?\), sleeps ([\d.]+), service waits ([\d.]+)'),
    ('replay',    r'game waited for the replay worker ([\d.]+), replay recording ([\d.]+) \(its CPU ([\d.]+)\), replay waited for the encoder ([\d.]+), cache save ([\d.]+), waited for prewarm ([\d.]+)'),
    ('pool',      r"pool placement ([\d.]+) ms \((\d+) pools, ([\d.]+) MB\), give-back ([\d.]+) ms, the pool table's lock waited for: replay ([\d.]+), others ([\d.]+)"),
    ('images',    r'new images (\d+), ([\d.]+) ms with their first content'),
]
SUBS = [(k, re.compile(v)) for k, v in SUBS]
KEYMAP = {
    'frame': ['frame', 't_s', 'whole_ms', 'submit_present_ms', 'gpu_ms', 'gpu_span_ms', 'gpu_wait_ms'],
    'busiest': ['busiest_span_ms', 'busiest_threads'],
    'pipelines': ['new_pipelines', 'new_shaderlibs', 'pipeline_ms'],
    'uploads': ['tex_first', 'tex_reup', 'tex_mb', 'tex_ms'],
    'hybrid': ['hybrid_draws'],
    'waits': ['fence_ms', 'acquire_ms'],
    'cpu': ['thread_cpu', 'process_cpu'],
    'faults': ['majflt', 'minflt'],
    'files': ['read_mb', 'read_ms', 'opens_ms'],
    'wfaults': ['write_faults'],
    'guest': ['guest_waits_ms', 'guest_longest_ms', 'guest_longest_kind', 'guest_longest_by', 'sleeps_ms', 'service_waits_ms'],
    'replay': ['replay_worker_ms', 'replay_rec_ms', 'replay_cpu_ms', 'replay_enc_ms', 'cache_save_ms', 'prewarm_ms'],
    'pool': ['pool_place_ms', 'pool_n', 'pool_mb', 'giveback_ms', 'lock_replay_ms', 'lock_other_ms'],
    'images': ['new_images', 'new_images_ms'],
}
INT_FIELDS = {'frame', 'new_pipelines', 'new_shaderlibs', 'tex_first', 'tex_reup', 'hybrid_draws',
              'majflt', 'minflt', 'write_faults', 'pool_n', 'new_images'}


def parse_log(path):
    recs = []
    for line in path.read_text(errors='replace').splitlines():
        if '[longframe]' not in line:
            continue
        r = {}
        for key, rx in SUBS:
            m = rx.search(line)
            if not m:
                continue
            keys = KEYMAP[key]
            for kk, vv in zip(keys, m.groups()):
                if kk in ('busiest_threads', 'guest_longest_kind', 'guest_longest_by'):
                    r[kk] = vv
                    continue
                try:
                    r[kk] = int(vv) if kk in INT_FIELDS else float(vv)
                except (ValueError, TypeError):
                    r[kk] = None
        if 'frame' not in r:
            recs.append({'raw': line[:160], 'unparsed': True})
            continue
        recs.append(r)
    return recs


def pct(sorted_vals, p):
    if not sorted_vals:
        return None
    idx = min(int(len(sorted_vals) * p / 100.0), len(sorted_vals) - 1)
    return sorted_vals[idx]


def classify(r):
    """dominant cause tags for one long frame"""
    tags = []
    if r.get('pipeline_ms', 0) > 5:
        tags.append(('H1_pipeline_compile', r['pipeline_ms']))
    if r.get('tex_ms', 0) > 5:
        tags.append(('H3_texture_upload', r['tex_ms']))
    if r.get('read_ms', 0) > 10:
        tags.append(('H2_file_read', r['read_ms']))
    if r.get('majflt', 0) > 10:
        tags.append(('H5_page_faults', r['majflt']))
    if r.get('acquire_ms', 0) > 10:
        tags.append(('H8_swapchain_acquire', r['acquire_ms']))
    if r.get('fence_ms', 0) > 10:
        tags.append(('H8_fence', r['fence_ms']))
    if r.get('guest_waits_ms', 0) > 50:
        tags.append(('H8_guest_wait:' + (r.get('guest_longest_kind') or '?'), r['guest_waits_ms']))
    if r.get('pool_place_ms', 0) > 10:
        tags.append(('H4_pool_placement', r['pool_place_ms']))
    if r.get('process_cpu', 0) > 400:
        tags.append(('H7_cpu_load', r['process_cpu']))
    if r.get('gpu_span_ms', 0) > r.get('whole_ms', 0) * 0.7 and r.get('gpu_span_ms', 0) > 25:
        tags.append(('GPU_bound', r['gpu_span_ms']))
    if r.get('replay_rec_ms', 0) > 20:
        tags.append(('H9_replay_recording', r['replay_rec_ms']))
    tags.sort(key=lambda x: -x[1])
    return tags


def analyze(recs, label):
    ok = [r for r in recs if 'unparsed' not in r]
    unparsed = [r for r in recs if 'unparsed' in r]
    wholes = sorted(float(r['whole_ms']) for r in ok)
    n = len(ok)
    session_end = ok[-1]['t_s'] if ok else 0
    frame_max = ok[-1]['frame'] if ok else 0
    stats = {
        'label': label,
        'longframe_count': n,
        'unparsed': len(unparsed),
        'session_s': session_end,
        'last_frame': int(frame_max),
        'avg_fps_all_frames': round(frame_max / session_end, 1) if session_end else None,
        'longframe_p50': pct(wholes, 50), 'longframe_p90': pct(wholes, 90),
        'longframe_p95': pct(wholes, 95), 'longframe_p99': pct(wholes, 99),
        'longframe_max': wholes[-1] if wholes else None,
        'longframe_per_min': round(n / (session_end / 60.0), 1) if session_end else None,
        'over100ms': sum(1 for w in wholes if w > 100),
        'over250ms': sum(1 for w in wholes if w > 250),
        'over1000ms': sum(1 for w in wholes if w > 1000),
    }
    causes = {}
    worst = sorted(ok, key=lambda r: -float(r['whole_ms']))[:15]
    for r in ok:
        for tag, val in classify(r):
            c = causes.setdefault(tag, {'count': 0, 'sum_ms': 0.0, 'max_ms': 0.0})
            c['count'] += 1
            c['sum_ms'] += float(val)
            c['max_ms'] = max(c['max_ms'], float(val))
    stats['causes'] = {k: {'count': v['count'], 'sum_ms': round(v['sum_ms'], 1), 'max_ms': round(v['max_ms'], 1)}
                       for k, v in sorted(causes.items(), key=lambda kv: -kv[1]['sum_ms'])}
    stats['worst15'] = [{k: r.get(k) for k in ('frame', 't_s', 'whole_ms', 'gpu_ms', 'pipeline_ms',
                                               'tex_ms', 'read_mb', 'read_ms', 'majflt', 'acquire_ms',
                                               'guest_waits_ms', 'guest_longest_ms', 'guest_longest_kind',
                                               'guest_longest_by', 'pool_place_ms', 'process_cpu', 'minflt',
                                               'replay_rec_ms', 'giveback_ms')}
                        for r in worst]
    buckets = {}
    for w in wholes:
        b = int(min(w // 25, 15))
        buckets[b] = buckets.get(b, 0) + 1
    stats['whole_ms_buckets'] = {f'{k*25}-{k*25+25}ms': v for k, v in sorted(buckets.items())}
    return stats


def video_gaps(path):
    """Presentation gaps from the recording's packet timestamps (VFR capture:
    a packet per screen content change; gaps > 100 ms = visible picture freeze)."""
    pts = []
    for ln in path.read_text().splitlines():
        parts = ln.strip().split(',')
        if parts and parts[0]:
            pts.append(float(parts[0]))
    gaps = [b - a for a, b in zip(pts, pts[1:])]
    med = statistics.median(gaps)
    upd = [g for g in gaps if g > 0.005]
    freezes = [{'t': round(pts[i], 3), 'gap_ms': round(g * 1000, 1)}
               for i, g in enumerate(gaps) if g > 0.100]
    return {
        'packets': len(pts), 'duration_s': pts[-1],
        'median_gap_ms': round(med * 1000, 2),
        'update_rate_hz': round(len(upd) / pts[-1], 1),
        'update_gap_p50_ms': round(pct(sorted(upd), 50) * 1000, 1),
        'update_gap_p95_ms': round(pct(sorted(upd), 95) * 1000, 1),
        'update_gap_p99_ms': round(pct(sorted(upd), 99) * 1000, 1),
        'update_gap_max_ms': round(max(upd) * 1000, 1),
        'update_gap_cv': round(statistics.pstdev(upd) / statistics.mean(upd), 2),
        'freezes_gt100ms': len(freezes),
        'freezes_gt250ms': sum(1 for f in freezes if f['gap_ms'] > 250),
        'freezes_gt500ms': sum(1 for f in freezes if f['gap_ms'] > 500),
        'freezes_per_min_gt100ms': round(len(freezes) / (pts[-1] / 60.0), 1),
        'freezes': freezes,
    }


def main():
    logs = {
        'log1_172921': LOGS / '0100F7E00C70E000-20261121-172921.log',
        'log2_203320': LOGS / '0100F7E00C70E000-20261121-203320.log',
    }
    stats_all = {}
    for label, path in logs.items():
        recs = parse_log(path)
        stats = analyze(recs, label)
        stats_all[label] = stats
        (OUT / f'{label}_longframes.json').write_text(json.dumps(recs, indent=1, default=str))
    (OUT / 'log_stats.json').write_text(json.dumps(stats_all, indent=1, default=str))
    vg = video_gaps(WORK / 'video' / 'pts.txt')
    (OUT / 'video_gaps.json').write_text(json.dumps(vg, indent=1))
    lines = ['# Fase 0 analysis summary', '',
             '## Video (screen-20261121-173341.mp4, 1600x720 VFR, YouTube re-upload also exists)', '']
    for k, v in vg.items():
        if k != 'freezes':
            lines.append(f'- {k}: {v}')
    lines += ['', '### Video freezes (screen update gap > 100 ms)', '']
    for h in vg['freezes']:
        lines.append(f"- t={h['t']}s gap={h['gap_ms']}ms")
    for label, s in stats_all.items():
        lines += ['', f'## {label}', '']
        for k, v in s.items():
            if k in ('worst15', 'whole_ms_buckets', 'causes'):
                continue
            lines.append(f'- {k}: {v}')
        lines += ['', '### whole_ms buckets', '']
        lines += [f'- {k}: {v}' for k, v in s.get('whole_ms_buckets', {}).items()]
        lines += ['', '### causes (sum over longframes)', '']
        lines += [f'- {k}: {v}' for k, v in s.get('causes', {}).items()]
        lines += ['', '### worst 15 longframes', '']
        for w in s.get('worst15', []):
            lines.append(f"- frame {w['frame']} t={w['t_s']}s whole={w['whole_ms']}ms gpu={w['gpu_ms']} "
                         f"pipe={w['pipeline_ms']}ms tex={w['tex_ms']}ms read={w['read_mb']}MB/{w['read_ms']}ms "
                         f"majflt={w['majflt']} acq={w['acquire_ms']}ms guest={w['guest_waits_ms']}ms "
                         f"(longest {w['guest_longest_ms']}ms {w['guest_longest_kind']} by {w['guest_longest_by']}) "
                         f"pool={w['pool_place_ms']}ms giveback={w['giveback_ms']}ms cpu={w['process_cpu']} "
                         f"minflt={w['minflt']} replayrec={w['replay_rec_ms']}ms")
    (OUT / 'summary.md').write_text('\n'.join(lines))
    print('unparsed longframe lines:', {l: s['unparsed'] for l, s in stats_all.items()})
    print(json.dumps({l: {k: s[k] for k in ('longframe_count', 'session_s', 'avg_fps_all_frames',
                                            'longframe_p50', 'longframe_p95', 'longframe_p99',
                                            'longframe_max', 'over100ms', 'over250ms', 'over1000ms')}
                      for l, s in stats_all.items()}, indent=1))
    print(json.dumps({k: v for k, v in vg.items() if k != 'freezes'}, indent=1))


if __name__ == '__main__':
    main()
