# Scorecard — correção de stuttering do Hogwarts Legacy (perf/hogwarts-legacy-stutter)

Ciclo Criador + Crítico (rubrica da seção 6.3 do briefing). Cada rodada: um run do
`build-apk.yml` (APK `LemonNative-hogwarts-stutter-rNN-<sha>.apk`) + o relatório do
Crítico com o formato obrigatório. Linha de base medida em `triage.md`/`timeline.md`.

| Rodada | Run do CI | APK | Nota geral | Δ | Status |
|---|---|---|---|---|---|
| 1 | r29 = run 44966959699 (26ae007), success | `LemonNative-hogwarts-stutter-r29-26ae007.apk` (33,2 MB) | 5.9 | — | avaliada |
| 2 | r30 = run 44971206327 (bb1f9a0), success | `LemonNative-hogwarts-stutter-r30-bb1f9a0.apk` | 6.0 | +0.1 | avaliada |

## Métricas da linha de base (dos logs e do vídeo; fontes por linha)

Frame time: das linhas `[nvn] timing` do log B (**todas as 29 janelas** de 300 frames —
faixas mín–máx honestas): p50 **32,09–57,02 ms** (15 de 29 janelas acima de 33,4;
drift dentro da sessão: 33,4 nas primeiras janelas → 34,8–39,2 nas janelas 5100–6300
→ **57,0 na janela 8400** — degradação sustentada, evidência do cluster GPU/H7)
· p95 **42,02–187,63 ms** · p99 por janela **46–1497,6 ms** (máx na janela do frame 2100,
1497,57 ms) · máx da sessão **5741,6 ms**. Over-16,7 ms por janela: **267–300 de 300
frames** (linhas `[nvn] frame interval`; o limiar do log é 16,7 ms, não 33,3 — a média de
30 fps está toda acima de 16,7). Cadência da tela (vídeo, gravação original):
p50 32,8 ms · p95 67,6 ms · p99 253 ms · max 1002 ms · CV 1,33.
Congelamentos > 100 ms: 89 no vídeo (38/min) · > 250 ms: 39 · > 500 ms: 13.
Piores stalls individuais (reprodutíveis nos dois drivers): 5,45–5,74 s
(compilação de pipeline 3,1–4,8 s + gravação do replay 3,3–5,4 s + condvar
RenderThread 3,3–5,4 s) e 2,4–3,8 s (majflt 758–1266).

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
