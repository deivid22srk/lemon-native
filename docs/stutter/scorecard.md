# Scorecard — correção de stuttering do Hogwarts Legacy (perf/hogwarts-legacy-stutter)

Ciclo Criador + Crítico (rubrica da seção 6.3 do briefing). Cada rodada: um run do
`build-apk.yml` (APK `LemonNative-hogwarts-stutter-rNN-<sha>.apk`) + o relatório do
Crítico com o formato obrigatório. Linha de base medida em `triage.md`/`timeline.md`.

| Rodada | Run do CI | APK | Nota geral | Δ | Status |
|---|---|---|---|---|---|
| 1 | (a preencher após o run) | LemonNative-hogwarts-stutter-rNN-<sha>.apk | (a preencher) | — | em andamento |

## Métricas da linha de base (dos logs e do vídeo; ver triage.md)

- Frame time (janelas de 300 frames do log B): p50 33,4–34,2 ms · p95 41–79 ms ·
  p99 46–931 ms · max 5742 ms
- Cadência de atualização da tela (vídeo, 141 s): p50 32,8 ms · p95 67,6 ms ·
  p99 253 ms · max 1002 ms · CV 1,33
- Congelamentos > 100 ms: 89 no vídeo (38/min) · > 250 ms: 39 · > 500 ms: 13
- Over-target (33,3 ms) por janela de 300 frames: 269–295 de 300
- Piores stalls individuais (reprodutíveis nos dois drivers): 5,45–5,74 s
  (compilação de pipeline 3,1–4,8 s + gravação do replay 3,3–5,4 s + condvar
  RenderThread 3,3–5,4 s) e 2,4–3,8 s (majflt 758–1266)

## Rodada 1 — o que foi entregue pelo Criador

1. **Fase 0 completa** (`timeline.md`, `triage.md`): vídeo original do aparelho
   analisado por frames; dois logs inventariados e correlacionados; taxonomia
   H1–H11 aplicada com confiança e ranking por impacto.
2. **Telemetria nova** (flag-gated, default off): `DM_PERF_REPORT=1` →
   `stutter_report.json` (janelas de 300 frames + histograma completo + hitches
   com atribuição); `DM_ATRACE=1` → marcadores em compilação de pipeline,
   gravação do replay e leitura de arquivo grande.
3. **Correções candidatas** (flag-gated, default off): `DM_FS_FADV=1` (H2:
   readahead nos pacotes), `DM_MEM_FB_PREFETCH=1` (H5: prefetch de pools
   file-backed). Teste unitário do fileback passa (`t_android_fileback: ok`).
4. **CI**: `perf/**` dispara o build-apk.yml; APK nomeado por branch/rodada/SHA.

Hipóteses H1/H9 (seed de prewarm ausente; worker de replay serializado) ficam
para as rodadas 2+ com os dados de E4 (trace em dispositivo) — mudanças nessas
áreas sem medição violam a regra 5 (medir antes de corrigir).

## Conflitos esperados com a branch fix/hogwarts-legacy-gpu-artifacts (regra 1)

Essa branch (não mergeada) tocou `dm/gfx/vk_android/vka_ctl.c`, `vka_sampdbg.h`,
`vka_tex.c`, `dm/host/tests/Makefile`, `t_android_sampdbg.c`,
`docs/advanced_settings.tsv`, `docs/env_switches.tsv`, `docs/hogwarts/*`.
Esta branch evitou todos esses arquivos (flags documentadas só em
`docs/stutter/experiments.md`; sem linhas nos TSVs do usuário). Conflito
provável se as duas forem mergeadas: nenhum por arquivo; semântico apenas se
as duas alterarem `hle_nvn.c` (a outra branch não tocou).

## Relatórios do Crítico

(a cada rodada, no formato obrigatório da seção 7.2, colado abaixo)
