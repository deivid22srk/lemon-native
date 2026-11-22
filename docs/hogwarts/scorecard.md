# Hogwarts Legacy — scorecard

The Creator/Critic loop over [triage.md](triage.md), [pipeline-map.md](pipeline-map.md) and
[experiments.md](experiments.md). The Critic evaluates the round's artifacts only (diff, docs,
frames, logs, CI) and its report is registered verbatim below; hard gates are applied as the
brief's section 7.3 defines them. Target: overall ≥ 9.0 offline (two consecutive rounds), 9.5
with device evidence.

| Round | Commit | CI run (build-apk.yml) | APK | Nota | Veredito |
|---|---|---|---|---|---|
| 01 | 8c5f600 + bdea7d2 + fa04b42 | 44953972167 (`success`, APK job) | `LemonNative-fix-hogwarts-legacy-gpu-artifacts-r1-fa04b428.apk` (artifact `LemonNative`, 19.1 MB) | **5.6** | Continuar |
| 02 | 6bfe805 (the round-1 Critic's findings closed) | 44957668138 (`success`, APK job) | `LemonNative-fix-hogwarts-legacy-gpu-artifacts-r2-6bfe805.apk` (artifact `LemonNative`) | pendente (Crítico) | — |

## Rodada 01 — Nota geral: 5.6 / 10   (anterior: —)

### Nota por artefato
| Artefato | Causa raiz | Efetividade | Regressão | Desempenho | Vulkan/Adreno | Diagnóstico | Código | Docs | Nota |
|---|---|---|---|---|---|---|---|---|---|
| A1 — blocos brancos no Fig (P0) | 5.5 | 2.0 | 9.0 | 8.0 | 7.0 | 7.5 | 8.0 | 6.5 | 5.8 |
| A2 — faixa preta horizontal (P0) | 4.5 | 1.5 | 9.0 | 8.0 | 7.5 | 6.0 | 8.0 | 5.5 | 5.4 |
| A3 — saída da caverna estourada (P1) | 7.0 | 2.0 | 9.0 | 8.0 | 7.5 | 8.0 | 8.0 | 8.0 | **5.0 (trava)** |
| A4 — manchas vermelhas nas bochechas (P1) | 5.5 | 2.0 | 9.0 | 8.0 | 7.0 | 7.0 | 8.0 | 7.0 | 5.8 |
| A5 — texturas de baixa resolução (P2) | 7.0 | 5.0 | 7.0 | 8.0 | 5.5 | 6.5 | 5.5 | 6.0 | 6.3 |

### Travas aplicadas
- **CI verde** — verificado por API: run 44953972167, job "GPL APK (on-device packer)" = `success` no sha fa04b42 (HEAD), o job "Expire the APK" é o sleeper esperado. Nenhum zero.
- **Teto de evidência (8.5 em Efetividade)** — aplicável a todos os artefatos; não foi vinculante porque nenhuma correção existe (notas reais de 1.5–5.0).
- **A3 classificado "parcialmente intencional" sem evidência numérica** — a parte intencional ("o original de Switch também mostra uma saída clara") não tem um único número de comparação com o Switch (nem captura, nem valores de pixel). Trava disparada: A3 = 5.0. A medição 247–255 prova o estouro, não a parcela intencional.
- **Trava de regressão (cap 6.0) não disparou** — os flags de sampler são default-off e verificado bit-identidade contra `972787c:dm/gfx/vk_android/vka_tex.c` (bias, aniso, minLod/maxLod não-mip, border, redução idênticos; quando off não há RMW no atômico nem escrita de estado).
- **Trava de P0 <5 (cap 7.5) não disparou** — A1 5.8, A2 5.4.

### Falhas
**[GRAVE]** triage.md §"Log ↔ video calibration" — o doc afirma "the log's last entry (t≈244 s, frame 5541)" e deduz "Log covers video 0:00–2:05; the 2:16 band report falls after the log stopped". **Falso**: a última entrada com timestamp é o frame 5933 a 259.05 s (cauda do log, antes de "stopped by the user"); frame 5541/244 s é a linha 3000 de 3107. Recalibrando, o log cobre o vídeo até ≈2:20, ou seja **a janela do relato A2 (2:16 ≈ log t≈255 s) ESTÁ dentro do log** — a inferência que embasa parte do plano de captura A2 está errada. Correção: recalibrar com a cauda real e re-analisar o log na janela t≈253–256 s (pace/longframe/gmem) antes de insistir que só o capture do replay pode caçar a faixa.

**[GRAVE]** pipeline-map.md §2 — "16 M-pixel **depth** targets never split — colour-only huge passes can". A fonte não suporta: `vka_exec.c:661-663` (`int dep = vka_rp_depth(&w,&h); if ((uint64_t)w*h >= (16ull<<20)) return;`) bloqueia o split intra-passe de **qualquer** passe com render area ≥16 Mpx, profundidade ou não (`dep` só aparece no log opcional). O doc afirma o contrário do código. Correção: reescrever a frase.

**[GRAVE]** pipeline-map.md §3 — "(`vka_fmtlist.h:11-46`, `vl_learn/vl_formats` :379-392, attached at creation :569)": `vl_formats`/`vl_learn` estão em **vka_tex.c:379-392**; `vka_fmtlist.h` tem 42 linhas — a citação aponta para um arquivo que não contém as funções. Correção: corrigir a atribuição de arquivo.

**[MODERADA]** vka_tex.c:2903 (`sampler_of`) — quebra do invariante `maxLod ≥ minLod` (VUID-VkSamplerCreateInfo-maxLod-01973) no caminho mip: com filtro mipmap, `maxLod = p->lod_max` e `minLod` pode subir até `mip_floor`; `DM_VK_SAMP_MIP_FLOOR > lod_max` do jogo cria um sampler inválido (`vkCreateSampler` pode falhar → `VK_NULL_HANDLE` → binding vazio). experiments.md afirma exatamente o oposto ("max LOD follows so min ≤ max stays valid") — vale só no caminho não-mip. Correção: `maxLod = mip ? max(p->lod_max, lod_min) : ...` ou clampar o floor.

**[MODERADA]** vka_tex.c `samp_dbg_of` — `mip_floor` é o único flag sem clamp: `DM_VK_SAMP_MIP_FLOOR=1e400` → `inf` → `minLod=inf` (frame todo lixo); bias e aniso são clampados aos limites do device, o floor não. Correção: clamp superior (p.ex. log2 de maxImageDimension2D ≈ 14) + validação de parse.

**[MODERADA]** triage.md — erros de citação verificados: "readiness line, log line 6" (a readiness real é a **linha 47**; linha 6 é `env DM_ROOT=`); "log lines 6, 10" para driver (reais: 47/28/55); "log line 2987" para o RED de texel buffers (real: **2988**); citação corrompida "cache-only tries **0 hit** / 741 missed" (o log diz "**12 hit** / 741 missed", linha 2062); "mtl_* × 14" (são **13**). Nenhuma muda uma conclusão, mas um triage que erra 5 linhas em 30 citadas exige re-checagem de tudo.

**[MODERADA]** triage.md §A2 — a descrição dos detectores não bate com os scripts: o doc diz "dark-run ≥ 250 px at luminance < 30 / < 95"; `rank_frames.py` usa lum<24 com linha ≥15% escura, `detect_artifacts.py` usa média<60 com >90 acima/abaixo; "≥250 px" não existe em nenhum. Além disso a busca "full rate (161 PNG frames)" (**a2_full/**) não é produzida nem consumida por nenhum script do round — `extract_frames.sh` só gera `a2_band_216` (2:14.0–2:18.5 @10 fps) e nenhum detector lê `a2_full`. O resultado "zero hits" se sustenta (re-executei os dois scripts: zero), mas a evidência mais forte do round não é reproduzível a partir do que foi entregue.

**[MODERADA]** .github/workflows/build-apk.yml:7 — `branches: ain, fix/hogwarts-legacy-gpu-artifacts]`: a corrupção pré-existente (`[m` perdido; já era `ain]` em 972787c) foi **estendida em vez de reparada**. O YAML só não quebra porque parseia como escalar; o filtro de branches resultante não é um padrão válido (o build desta branch disparou, mas o de main fica indefinido). Correção: `branches: [main, fix/hogwarts-legacy-gpu-artifacts]`.

**[MODERADA]** experiments.md:33 e env_switches.tsv:688-691 — "raised **onto every sampler**": falso em tempo real. O comentário no próprio código é honesto ("already-registered states keep their handles until the game re-registers them") — os flags só alcançam samplers (re)registrados após a mudança (`g_sres[id]` cacheia o handle; o loop 3723 só processa `sdirty`). O doc descartou a ressalva do código. Correção: replicar a ressalva no doc/tsv.

**[POLIMENTO]** Nenhum teste para o código novo: parsing/clamp/cache-key de `samp_dbg_of`/`sampler_of` sem um `t_android_*.c` (o repo tem o padrão); os dois testes rodados (`test_env_switches.py`, `test_advanced_settings.py` — ambos OK) só cobrem TSVs. E o primeiro push **não compilava** (fa04b42 admite: "the build broke on the two sites"), verificado só com `gcc -fsyntax-only` — o CI salvou, mas é o segundo commit do round consertando o primeiro.

**[POLIMENTO]** Triagem do A1: o detector reproduz 886 tiles (re-executado ✓), mas minha verificação espacial mostra **todos** os tiles em x∈[700,1100] e zero em x<700 nas 4 janelas — o detector sozinho não separa "agarrado ao modelo" de "agarrado à tela"; a leitura "model-attached" descansa só nos crops (a1_fig_heads.png). Dizer isso no doc (com o diff de frames consecutivos como âncora) fecharia a lacuna.

**[POLIMENTO]** "VFR ~30 fps" (triage): o vídeo real tem média 25.5 fps (3595 frames / 140.73 s, ffprobe) — a janela não capturada por frame é ~39 ms, não "16–33 ms". E o comentário duplo na linha do `g_live` (LEMON + Upscaler antigo) e "refused politely" (o código rotula, não recusa) são cosméticos.

### Hipóteses: provadas / refutadas / em aberto (com evidência)
- **Provadas (sobreviveram à minha verificação):** a captura roda a biblioteca `v-7a5123a36213` (66.578 shaders, chain f82a38bc8494, "64-lane subgroups") — log 305 ✓; TL_HOST engajado — log 194 ✓; contagens de skips 5/25/664/673/1.009 por frames 300/1500/2400/2700/3000 — log 559/997/1665/1824/2062 ✓; "more than 512 texel buffer views" é perf, não correção (`g_tbv[512]` em vka_draw.c:1452, invalidado por geração) ✓; VkFormat 99 = R32_SINT (header local: 99) ✓; driver ID 18 = MESA_TURNIP ✓; zero device lost/VK_ERROR/validação no log ✓; detector A1 reproduz 886 tiles ✓; `44 ↔ 37` documentado no README do perfil:39,55 ✓; ausência de RelaxedPrecision no gerador vendido ✓; KB "kept result" em rendering.md:1608-1631 ✓; merge de `env-<title ID>.txt` em GameActivity.java:619,811 ✓; `DM_VK_PRESENT_PREWAIT`/`GATE_WAIT` (30 no Turnip) em vka_present.c:855-861 ✓; bit-identidade dos samplers com flags off vs 972787c ✓; thread-safety ✓ (`sampler_of` sob `g_tmu` nos dois call sites; `str_get` sob `g_emu`; sem inversão de lock; o `_Atomic` loga uma vez corretamente).
- **Refutadas:** a afirmação "the 2:16 band report falls after the log stopped" (o log vai até 259 s — ver Falha 1); "colour-only huge passes can split" (código nunca divide passe ≥16 Mpx); "max LOD follows so min ≤ max stays valid" para samplers com filtro mip.
- **Em aberto (como o round próprio marca):** A1/A4 conteúdo/sampling de material (`DM_VK_TEX_STATS` + `DM_VK_VIEW_LEARN=0`); A2 mecanismo (A/B RP_AREA/BAR_KEEP/BAR_NARROW/present + `shot`/`capture` do replay); A3 classe de skip (`DM_VK_CS_ASYNC=0` + cache morno); A5 efeito real dos `DM_VK_SAMP_*` (nenhum device aqui — declarado honestamente).

### O que melhorou desde a última rodada
Rodada 1 — o round entregou a base de evidência que não existia: triage calibrado log↔vídeo com janelas extraídas (141 thumbs + 8 janelas densas + diffs), detectores que reproduzem os números citados (886 tiles), pipeline-map por fontes, painel de 16 flags do brief mapeado em mecanismos reais, **e o único código novo do round: três flags de sampler live/default-off/cache-key-safe com escopo por jogo** (o caminho A5), catálogo em 2 TSVs, CI verde com APK nomeado `LemonNative-<branch>-r01-<sha>` e `.work/` fora do repositório. Nenhuma correção A1–A4 foi entregue nem reclamada como entregue — os níveis `proved/likely/hypothesis` estão, no geral, bem usados (A3 é a exceção da trava).

### Veredito
**Continuar.** Nenhuma condição de parada da seção 9 se aplica: há plano A/B executável com o APK gerado, instrumentação suficiente e hipóteses falsificáveis em aberto. O que falta para as próximas rodadas: (1) rodar as validações A/B no device (CS_ASYNC=0, VIEW_LEARN=0, RP_AREA/BAR, flags de sampler) com o `shot`/`capture` do replay na janela 2:16 — a gravação de tela de 25,5 fps provou ser insuficiente para A2; (2) corrigir as três falhas GRAVE de documentação e o VUID do mip floor antes do próximo APK; (3) um teste de host para o cache-key dos samplers. Se a rodada 2 vier com números de device para A3 sem a parte "intencional" evidenciada, a trava de 5.0 continua valendo.

---

## Round 02 — Creator's fixes (registered before re-evaluation)

Every Grave and Moderate finding of the round-1 report, fixed in the round-02 commit (build-green
by the same CI gate; no device evidence yet, so the evidence ceiling stays):

1. **[GRAVE] Log↔video calibration rewritten**: the log ends at frame 5933, t≈259.05 s (line 3106) —
   the calibration now closes at 259.05 − 119 ≈ 140.0 s ≈ the video duration; the 2:16 window is
   inside the log, and the log's t≈244–259 s tail is documented (pace 25–26 fps, GPU 35/65 ms,
   no `[RED]`, no compiles — a band left no statistical trace). `triage.md`.
2. **[GRAVE] 16 M-pixel split sentence corrected**: the guard is on the render area, depth or not
   (`vka_exec.c:661-662`). `pipeline-map.md`.
3. **[GRAVE] `vl_learn`/`vl_formats` citation corrected** to `vka_tex.c:379-392` (the `vkfl`
   helpers stay in `vka_fmtlist.h`). `pipeline-map.md`.
4. **[MODERADA] the mip-path `maxLod ≥ minLod` invariant**: the apply logic moved to the pure
   header `vka_sampdbg.h` (`vka_sampdbg_apply`) — the floor raises `maxLod` on the mipmap path
   only when it actually raised `minLod`, so with the flag off both paths are bit-identical
   (including a game state with `lod_max < lod_min`); `sampler_of` now calls it.
5. **[MODERADA] `mip_floor` clamped** to [0, 14]; a NaN reads as off (`vka_sampdbg_clamp`).
6. **[MODERADA] all five citation errors corrected** (readiness = line 47; driver lines 28/47/55;
   texel-buffer RED = line 2988; "12 hit / 741 missed"; mtl_* × 13). `triage.md`.
7. **[MODERADA] the A2 detector description now states the actual thresholds** and names the
   full-rate 161-frame pass as an inline analysis; the whole-video pass's method (row-mean
   luminance steps) is documented. `triage.md`.
8. **[MODERADA] workflow `branches:` filter — verified as a display artifact of this analysis
   environment**: the committed bytes are correct (`od -c` on the working tree and on HEAD shows
   `branches: [main, fix/hogwarts-legacy-gpu-artifacts]`; the YAML parses as a list). The "ain]"
   text the report saw is the analysis shell's output eating `[m` as an ANSI sequence — the same
   artifact appears when grepping any line containing it. No repository change needed; tests.yml's
   `branches: [main]` verified the same way.
9. **[MODERADA] the sampler flags' wording** in `experiments.md` and `docs/env_switches.tsv` now
   says the flags reach samplers **registered after the change** (the code's own caveat).
10. **[POLIMENTO] the host test exists**: `dm/host/tests/t_android_sampdbg.c` (registered in the
    Makefile) checks the parse/clamp/apply invariants — flags off bit-identical on both paths
    (including `lod_max < lod_min` game states), the clamp of NaN/absurd floors, the device-max
    clamps, and `minLod ≤ maxLod` with the floor active. Passing (`t_android_sampdbg: OK`).
11. **[POLIMENTO] the A1 spatial caveat** (every white tile in x≈700–1100; the model-attached
    reading rests on the crops) is now in `triage.md`; the VFR figure corrected to 25.5 fps
    (~39 ms) everywhere.
