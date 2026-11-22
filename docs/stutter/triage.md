# Triage — stuttering do Hogwarts Legacy no Lemon Native

Fase 0 (seção 3 do briefing), executada sobre os dois logs do usuário e a gravação
original do aparelho. Níveis de confiança: `provado` (dado direto de log/vídeo),
`provável` (inferência com evidência forte), `hipótese` (a testar).

## 1. Ambiente medido

| Item | Valor | Fonte |
|---|---|---|
| Aparelho | Motorola Edge 30 Fusion, Android 14 (SDK 34), arm64-v8a | `[nxlaunch] device …` (ambos os logs) |
| GPU | Adreno 660 | `[readiness]` |
| RAM | 7,2 GiB | `[readiness]` |
| Driver — sessão A (17:29, log do vídeo) | WN-Turnip-1.19-b_Axxx, Mesa 26.3.0-devel (git-30da39187d), Vulkan 1.3.363 | `[readiness]` log A |
| Driver — sessão B (20:33, log novo) | Turnip T30 @Mr_Purple_666 ("PurpleVK public driver", git-62ac221a33), Vulkan 1.4.359, driver ID 18 | `[readiness]` log B |
| Painel | 144 Hz (spec do aparelho) | `hipótese` — o host mede os ticks reais do painel (`vka_panel_tick`) mas o log não imprime o valor |
| Build | lemon-native-1.0.0-dev, commit 6bfe805c8557, shader chain f82a38bc8494 | `[nxlaunch]` |
| Jogo | Hogwarts Legacy 0100F7E00C70E000, .nup em `/storage/emulated/0/2Hogwarts/` | `[nxlaunch]` |
| FPS alvo (config) | `DM_FPS_TARGET=60`, cap 60 (default) | env do log |
| FPS alvo (real) | **30 fps** — o cap do host não é aplicado (`present cap 60 fps not applied`); cadência observada 30 fps no vídeo/overlay | `[nvn]` + vídeo |
| Swapchain do jogo | 3 texturas, interval 1, 1280x720, formato 0x25 | `[nvn] WindowInitialize` |
| Upscaler | sgsr1 (default) | `[nxlaunch] settings` |
| Thermal | eventos `throttle green`/`yellow` (status 0); caps de CPU observados 1075–2995 MHz por cluster (6 linhas `[overlay]` nas duas sessões) | `[overlay]` |
| Settings do usuário | `[adv] none: no Advanced row is set` — nenhum ajuste manual | `[nxlaunch]` |

Sessões: A = 259 s, 5933 frames do guest, média 22,9 fps; B = 396 s, 8945 frames,
média 22,6 fps. Média baixa = menus/carregamentos; gameplay assentado = 30 fps.
Tamanhos dos logs (linhas com a tag): A = 55 `[nxlaunch]` + 4 `[overlay]` + 153 `[nvn]`; B = 53 + 2 + 224.

## 2. Telemetria que já existe (importante para a Rodada 1)

O app **já tem** a maior parte da instrumentação pedida pela seção 5, ligada por
padrão (`DM_LONGFRAME` default on; DM_LONGFRAME_THREADS=250 ms de amostragem de
threads): linhas `[longframe]` por frame longo com atribuição de causa —
`whole frame`, GPU busy/span, `new pipelines + shader libraries, X ms` (H1),
uploads de textura (H3), `acquire`/fences (H8), page faults major/minor (H5),
leitura de arquivos MB/ms (H2), `guest waits` (condvar/mutex, com thread culpada),
`replay recording/CPU` (H9), `pool placement`/`give-back` (H4), `busiest threads`.
Semântica em `dm/gfx/nvn/hle_nvn.c` (~2482–2540) e `dm/gfx/vk_android/vka_perf.c`
(270–380). Impresso só quando o frame é 40% acima da média móvel de 16 — é a cauda
da distribuição, não a distribuição completa (a distribuição completa virá do
`stutter_report.json`, Rodada 1).

**O que falta** (Rodada 1): relatório JSON exportável com a distribuição completa
(p50/p95/p99/p99.9/hitches por minuto), marcadores ATrace/Perfetto, contadores
térmicos/cpufreq por hitch, e numeração de wall-clock por evento.

## 3. Inventário dos logs

Contagem = **linhas que contêm a tag** (uma linha pode ter mais de uma tag; A tem 60
ocorrências de `[nxlaunch]` em 55 linhas, B tem 58 em 53).

| Arquivo | Sessão | Conteúdo |
|---|---|---|
| `0100F7E00C70E000-20261121-172921.log` | A (Turnip 1.19) | 3107 linhas: 1554 `[longframe]`, 948 `[vk]`, 153 `[nvn]`, 55 `[nxlaunch]`, 4 `[overlay]`… termina "stopped by the user" |
| `0100F7E00C70E000-20261121-203320.log` | B (PurpleVK 26.3) | 3929 linhas: 2052 `[longframe]`, 1170 `[vk]`, 224 `[nvn]`, 53 `[nxlaunch]`, 2 `[overlay]` |
| `screen-20261121-173341.mp4` | gravação do aparelho (17:33:41) | 1600x720 VFR, 140,7 s — fonte da timeline |

Palavras-chave procuradas: `compile`, `pipeline cache`, `miss`, `stall`, `wait`,
`fence`, `timeout`, `slow`, `late`, `drop`, `skip`, `fps`, `frame time`, `LMK`,
`OOM`, `GC`, `trim`, `thermal`, `throttl`, `cpufreq`, `major fault`, `read`, `IO`,
`SAF`, `mmap`, `fallback`, `interpreter`, `unresolved`, `swapchain`, `present`,
`vsync`, `refresh`. Achados relevantes e o que **não** aparece:

- `lowmemorykiller`/`LMK`/`OOM`/`onTrimMemory`/**não aparecem** — sem pressão de
  memória extrema nas sessões (RAM 7,2 GiB).
- `GC` do app Java/Kotlin — nenhum sinal de pausa de GC correlacionada (H11
  sem evidência).
- `thermal`/`throttl`: `[overlay] throttle green/yellow` — status 0, sem
  espinha térmica nas janelas dos piores stalls.
- Logs **não têm** tempo por frame completo (só a cauda adaptativa) — dito
  explicitamente conforme o briefing; instrumentação priorizada na Rodada 1.

## 4. O padrão: os mesmos stalls nos mesmos eventos de jogo, em dois drivers

Os maiores stalls reproduzem **no mesmo frame e no mesmo t_s aproximado** nas duas
sessões (o usuário refez o mesmo roteiro):

| Evento de jogo | Sessão A | Sessão B | Assinatura |
|---|---|---|---|
| Boot/primeira carga (frame 4, t≈3,7–3,8 s) | 937 ms (read 95 MB/195 ms, minflt 41591, condvar RenderThread 0) | 931 ms (read 96,8 MB/192 ms, minflt 43700) | H2 + H5 |
| Transição de cutscene em Hogsmeade (frame 145, t≈14,1 s) | 5547 ms (read 58,5 MB/775 ms, condvar TaskGraphThreadNP 0, pool 198 ms, CPU 10,1 s) | 5742 ms (read 63,8 MB/652 ms, condvar TaskGraphThreadNP 0, pool 219 ms, CPU 10,5 s) | H2 + H5 + H8 |
| Lote de efeitos da cutscene (frame ~2314–2353, t≈104–118 s) | 5448 ms (**pipe 4819 ms**, replay 5391 ms, condvar RenderThread 1 5379 ms, majflt 608) | 5566 ms (**pipe 3058–3311 ms**, replay 3285–3583 ms, condvar RenderThread 1 3251–3587 ms) | **H1 + H9 + H8** |
| Tempestade de majflt (frames ~1706–2029, t≈75–98 s) | 3833 ms (majflt 760, condvar) | 3696 ms (majflt 758, condvar) | H5 |
| Storm de majflt maior (t≈141 s / t≈98 s) | 2437 ms (majflt 1266) | 1705 ms (majflt 273, read 9,8 MB/61 ms) | H5 |
| Lotes de compilação (~t 174–177 s / 345–374 s) | 884–1624 ms (pipe 382–827 ms, replay 436–913 ms) | 90–234 ms por frame com **GPU 75–206 ms** por 28 s | H1/H9 (A) e sobrecarga de GPU (B) |

Isso é o resultado experimental mais forte da triagem: **os stalls são
reprodutíveis e atrelados a eventos do jogo** (entrar em cena/cutscene/efeito novo),
não aleatórios, e **se repetem através de uma troca de driver** (Turnip 1.19 →
PurpleVK 26.3). Consequência: o cache de pipelines do driver não está cobrindo esses
casos — o spike de compilação acontece mesmo na sessão seguinte.

## 5. Classificação pela taxonomia (seção 4 do briefing), com impacto

Impacto = frequência × duração, somado sobre os `[longframe]` de cada sessão
(atribuição automática por `scripts/analyze_stutter.py`, thresholds em
`classify()`). Totais das duas sessões combinadas.

| Rank | Cluster (ID) | Evidência | Confiança | Freq (A+B) | Tempo total | Maior evento |
|---|---|---|---|---|---|---|
| 1 | **H1 Compilação de pipeline na primeira vez + H9 gravação do replay** | `new pipelines … 3058–4819 ms` + `replay recording 3285–5391 ms (CPU 3,3–5,4 s)` + condvar RenderThread até 5,4 s, em lotes; 66+91 eventos H1, replay>20 ms em 365+983 frames | `provado` (dados) / correção a validar | 66+91 lotes | ~55 s | 5,4 s |
| 2 | **H5 Page faults (majflt storms)** | majflt 608/758/1266/2309 por frame; 230+200 eventos, até 2,4 s de frame; código de shader file-backed (`DM_MEM_FILEBACK_KINDS=shader`) + assets paginados sob demanda | `provado` (dado), causa do paging `provável` | 430 frames | ~79 s | 2,4 s (7,4 s somados) |
| 3 | **Sobrecarga de GPU sustentada** (não é stall de CPU) | 995+1102 longframes com GPU > 70% do frame (33–65 ms recorrente; episódio B: GPU 75–206 ms por 28 s); vídeo: FT 33→38 ms na caverna | `provado` | ~2100 frames | ~95 s | GPU 206 ms/frame |
| 4 | **H2 E/S de assets no caminho principal** | read 58–97 MB em 195–775 ms nos boot/cutscene; 125+110 eventos; leitura de `/storage/emulated/0` (FUSE) via pread | `provado` (leitura), causalidade do stall `provável` | 235 eventos | ~12 s | 775 ms |
| 5 | **H8 Esperas guest (condvar/mutex)** | condvar RenderThread/TaskGraph até 5,4 s; 81+80 eventos | `provado` como sintoma de serialização (S1/S4) | 161 eventos | ~58 s | 5,4 s |
| 6 | **H4 Alocação/pool placement** | `pool placement 112–223 ms (pools 11–50 MB)` nas transições; 80+73 eventos | `provado`, menor | 153 eventos | ~4,9 s | 223 ms |
| 7 | **H6 Frame pacing / painel** | cadência p50 32,8 ms regular fora dos stalls; pacer do host agenda slots de refresh inteiro medido (`vka_panel_tick`); painel 144 Hz vs 30 fps = razão 4,8 não-inteira | `hipótese` (micro-judder não verificável no vídeo VFR) | — | — | — |
| 8 | **H7 Térmico/cpufreq** | throttle green→yellow (6 linhas `[overlay]` nas duas sessões), status 0; caps de CPU 1075–2995 MHz; sem correlação com os piores stalls | `provado` que não domina | 6 eventos | — | — |
| — | H3 Upload de textura | 4+3 eventos, 112,1 ms somados nas duas sessões, máx 29,4 ms | `refutado` como causa de stutter nestas sessões | 7 | 0,1 s | 29,4 ms |
| — | H10 Log/instrumentação | sem pico de I/O de log nos hot paths; sampler de threads só em frames ≥ 100 ms | sem evidência de dano | — | — | — |
| — | H11 GC do app | nenhuma pausa de GC correlacionada nos logs | `refutado` nestas sessões | — | — | — |

Notas de verificação (Crítico rodada 1): H3 somado = 112,1 ms nas duas sessões
(4+3 eventos, máx 29,4 ms); o maior frame GPU-bound bruto do log B é 422,7 ms
(`GPU_bound.max_ms`) — o valor típico do episódio sustentado é 75–206 ms com span ≈ busy;
as linhas `[overlay]` são 6 no total (4 em A, 2 em B).

## 6. Ranking por impacto (frequência × duração) e plano

1. **H1+H9** (lotes de pipeline + gravação do replay, 5+ s por evento) — maior
   hitch único e maior dor sentida nas transições.
2. **H5** (majflt storms, 430 frames, ~79 s somados).
3. **Sobrecarga de GPU** (~95 s somados; define o teto de FPS nas cenas pesadas).
4. **H2** (E/S no caminho principal nas transições).
5. H4, H6, H7 — menores.

**Detalhe relevante do código** (para as correções): `dm/games/0100F7E00C70E000/`
(Hogwarts) **não tem `vk_prewarm.bin`** — o seed de prewarm do título está vazio,
enquanto o app já suporta carregar um (profile_rt) e já grava as keys de cada run em
`vk_pipelines.keys` (vka_pso.c). Compilações do frame path passam pelo gate
`vka_psogate.h` (slots por memória, frame primeiro); o usuário roda com
`DM_VK_PSO_ASYNC=2` e `DM_VK_PSO_SLOTS=1` (auto por driver ID 18). O spike de
replay recording (H9) acontece no worker único (`vka_worker.c`/`vka_enc.c`) — a
fila enche e o submit do jogo espera.

## 7. Limitações registradas

- Sem aparelho: nada aqui é "corrigido"; são causas medidas e correções candidatas.
- O vídeo VFR quantiza a cadência (limite inferior para judder); overlay de FPS
  do próprio app é a segunda fonte.
- A calibração vídeo↔log é `provável`, não `provado` (ver timeline.md).
- O log da sessão gravada (A) cobre 259 s mas o vídeo 141 s — parte dos stalls
  do log não tem imagem correspondente.
- A "GPU busy" do log mede o span de submissão do replay; um span muito acima do
  busy é CPU do worker de replay (vka_perf.c) — os episódios de GPU usados no
  rank 3 são os com span ≈ busy (GPU de fato trabalhando).

## 8. Log do aparelho após o APK da rodada 02 (2026-11-22 06:01) — `provado`

Sessão de 6,3 min (frames até 10094), **build `eeb5be5` (rodada 02), mesmo driver do
log de ontem** (`Turnip_Adreno_Driver_T30_Mr_Purple_666_`) — a pré-condição de mesmo
driver do plano de validação é satisfeita entre os dois logs. Jogo a 30 fps em painel
60,16 Hz (pace lines `window 30 fps`). **Nenhum flag de perf ligado** (sem
`DM_PERF_REPORT`/`DM_FS_FADV`/`DM_MEM_FB_PREFETCH`/`DM_ATRACE` no eco do env) — não
é um teste das correções; é um segundo baseline, com cache de pipelines quente.

Comparação mesmo driver, ~6,5 min cada:

| Métrica | 21/11 (`6bfe805c`) | 22/11 (`eeb5be5`) | Δ |
|---|---|---|---|
| hitches ≥100 ms /min | 30,4 | 20,9 | −31 % |
| hitches ≥500 ms /min | 5,19 | 1,11 | −79 % |
| hitches ≥1 s | 11 | 3 | −73 % |
| pior frame | 5742 ms | 1527 ms | −73 % |
| presents dropped | 11,5 % | 5,8 % | −50 % |
| pipelines novos (s de compile) | 447 (28,3 s) | 85 (3,8 s) | −81 % |
| tempo perdido em hitches ≥100 ms | 67,6 s | 22,8 s | −66 % |

Leitura honesta: a cauda melhorou (explicação mais provável: cache de pipelines
quente — os storms de 3–5,5 s de H1 de ontem sumiram; 34→3 hitches de H1), mas
**20,9 hitches ≥100 ms/min segue 3,4× acima da meta** (−80 % ⇒ ≤6,1/min). Sem os
flags ligados, nada da rodada 02 estava em teste.

Causas dos 132 hitches ≥100 ms desta sessão (contagem | tempo):

1. **H9/emu misto — 78 | 11,4 s**: frames de 100–500 ms com replay recording
   10–30 ms + waits espalhados. As pace lines mostram recording de 24 ms/frame
   com 57 draws e lookup de pipeline a 253 µs/draw numa janela — alvo da rodada 3.
2. **Gaps "nada ocupado" — 34 | 7,3 s** (ontem: 106 | 24,8 s): frames em que GPU,
   replay, guest e I/O somam <30 % do frame. Pista forte de **H7**: CPU MHz visto
   em 998/710/844 numa janela pesada com "ADPF no session" — o piso de clock da
   GPU (KGSL) está ativo, o da CPU não. Precisa de trace (E4) para nomear.
3. **H2 I/O de transição — 12 | 5,2 s**: t=3,8–34 s (frames 4/134/666), leituras
   de 36–89 MB com 182–587 ms no caminho principal — alvo direto de `DM_FS_FADV=1` (E2).
4. H1 residual 3 (1,2 s) · H8 sync 3 (1,8 s) · H5 2 (0,3 s).

`stutter_report.json` não foi gerado (flag off) — **o E1 oficial ainda não rodou**;
a rodada 3 só codifica após E1–E3 (e E4 para os gaps), conforme o veredito da rodada 02.

## 9. E1 oficial: primeiro `stutter_report.json` + `DM_FS_FADV=1` (2026-11-22 07:03) — `provado`

Sessão de 7,2 min (frames até 12000, 434,7 s), **build `eeb5be5`, mesmo driver Turnip
T30 Mr Purple 666**, painel 60,21 Hz (`window 30 fps`). Flags no eco do env:
`DM_PERF_REPORT=1` e `DM_FS_FADV=1` — **`DM_MEM_FB_PREFETCH` NÃO estava nesta sessão**
(estava ligado às 06:30; pedir para o usuário re-adicionar). O launcher imprime
`[RED] ... is not a switch of this build: nothing reads it` para as duas flags, mas o
report foi gravado e o readahead atuou — o aviso é falso-positivo do validador
(`docs/env_switches.tsv` não lista as flags que só o código lê; registrar entrada no tsv
como correção de rodada 3). Writer sem nenhum `[RED] perf report writer` — gravação
íntegra; o JSON saiu com um `]` duplicado no fim (flush interrompido ao fechar o app;
reparado no parse — candidatar `ftruncate`+flush final no exit gracioso).

**Validação cruzada `provado`:** o contador oficial do report (91 hitches ≥100 ms)
bate exato com a contagem do parser no log (91); ≥1 s (1) também bate.

Comparação com a mesma metodologia (parser v2, TGT 33,3 ms), mesmo driver:

| Métrica | 06:01 flags off | 07:03 FADV on | Δ |
|---|---|---|---|
| hitches ≥100 ms /min | 20,9 | **12,6** | −40 % |
| hitches ≥200 ms /min | 5,9 | 3,8 | −36 % |
| hitches ≥500 ms /min | 1,11 | 0,42 | −62 % |
| hitches ≥1 s | 3 | 1 | −67 % |
| pior frame | 1527 ms | 1388 ms | −9 % |
| tempo perdido (≥100 ms) | 22,8 s | 15,8 s | −31 % |
| presents dropped | 5,8 % | 4,1 % | −29 % |
| p99 present | 100 ms | 50 ms | −50 % |
| pipelines novos | 85 (3,8 s) | 42 (0,3 s) | −92 % no tempo (confundidor: cache quente) |
| major faults na sessão | 13 172 | **74 673** | +5,7× (⚠) |

Métricas oficiais do report: p50 mediano das janelas 32,3 ms (30 fps sustentado),
p99 de janelas mediano 73,5 ms, dropped 4,10 %, footprint pico 2584 → fim 1590 MiB.

Leitura honesta:

1. **Progresso real, mas atribuição confundida** — o começo da rota é idêntico nos dois
   logs (hitches de boot em t≈4 s e t≈10 s, ~1–1,5 s cada, ambos I/O de transição): o
   FADV **não** resolveu os dois maiores hitches da sessão. A queda de −40 % inclui
   cache de pipelines quente (H1: 3,8 s→0,3 s) e rota mid-game não garantida igual.
2. **12,6 hitches/min = −59 % vs. o baseline original de 30,4 (21/11)**; a meta é
   ≤6,1/min (−80 %) — falta ~um terço do caminho.
3. Causas dos 91 hitches ≥100 ms desta sessão (n | tempo): **H9/emu misto 49 | 8,6 s**
   (pace lines: replay recording 16–19 ms/frame no regime — 50 % do orçamento de
   33,3 ms, alvo nº 1 da rodada 3); **gaps "nada ocupado" 21 | 3,9 s** (CPU vista a
   998/1555/844 MHz com "ADPF no session" — H7, pede E4); **H2 I/O de transição
   12 | 4,7 s**; H5 majflt 7 | 1,4 s.
4. **⚠ majflt 5,7× maior sob FADV** (74 673 = ~172/s): hipótese — readahead enche o
   page cache e expulsa páginas do app (RAM 7,2 GiB, footprint ~2,3 GiB + JIT);
   espalhados não geram hitches grandes, mas podem puxar o p50 (32–33 ms) e os gaps.
   Medir com `DM_ATRACE`/E4 antes de mudar política (ex.: `FADV_RANDOM` em direórios
   de shader, readahead menor).
5. Maior hitch da estabilidade (sem storm de boot): 504 ms no frame 9547
   (replay recording 168 ms + condvar 86 ms) — H9 confirmado como maior causa única.

E1 está **cumprido** (linha de base oficial instrumentada existe). E2 parcialmente
(números acima); E3 precisa da sessão com `DM_MEM_FB_PREFETCH=1` re-adicionado.
Rodada 3 só codifica após E2/E3 limpos na mesma rota (6–10 min) e E4 para os gaps.

## 10. E2/E3: sessão de 07:29 com os TRÊS flags (2026-11-22) — `provado`

Sessão de 7,9 min (frames até 13482), build `eeb5be5`, mesmo driver, painel 60,18 Hz.
`DM_PERF_REPORT=1` + `DM_FS_FADV=1` + `DM_MEM_FB_PREFETCH=1` (os três ✓). Validação
cruzada de novo exata: report 62 hitches ≥100 ms == parser 62.

| Métrica | 21/11 original | 06:01 off | 07:03 FADV | 07:29 3 flags |
|---|---|---|---|---|
| hitches ≥100 ms /min | 30,4 | 20,9 | 12,6 | **7,9** |
| hitches ≥500 ms /min | 5,19 | 1,11 | 0,42 | 0,38 |
| hitches ≥1 s | 11 | 3 | 1 | 1 |
| tempo perdido (≥100 ms) | 67,6 s | 22,8 s | 15,8 s | **11,7 s** |
| presents dropped | 11,5 % | 5,8 % | 4,1 % | **2,4 %** |
| major faults na sessão | — | 13 172 | 74 673 | **7 975** |
| H2 I/O de transição (n\|tempo) | 12 \| 5,2 s | 12 \| 5,2 s | 12 \| 4,7 s | **8 \| 3,8 s** |
| gaps "nada ocupado" (n\|tempo) | 106 \| 24,8 s | 34 \| 7,3 s | 21 \| 3,9 s | **8 \| 2,3 s** |
| H9/emu misto (n\|tempo) | — | 78 \| 11,4 s | 49 \| 8,6 s | 46 \| 7,6 s |
| footprint pico | — | — | 2584 MiB | 2626 MiB (+1,6 %) |

Vereditos:

1. **E3 CUMPRIDO** — o prefetch eliminou o efeito colateral do FADV: majflt
   74 673 → 7 975 (−89 % vs. a sessão FADV, o comparador relevante; −39 % vs. o
   baseline sem flags), footprint +1,6 % (≤ 10 %). Os gaps "nada ocupado" caíram
   junto (21 → 8 | 3,9 → 2,3 s) — consistente com as falhas de página que o
   readahead empurrava.
2. **E2 PARCIAL** — I/O de transição 5,2 → 3,8 s = −27 % (alvo ≥30 %), contagem
   12 → 8, sem hitch novo noutra causa. Ficou a um passo do critério; rota não
   garantida idêntica pode explicar a diferença.
3. **Acumulado: −74 % vs. o baseline original (30,4 → 7,9/min)**; a meta é
   ≤6,1/min (−80 %). Tempo perdido −83 % (67,6 → 11,7 s), dropped −79 %
   (11,5 → 2,4 %). Falta remover ~1,8 hitches/min.
4. **H9 é agora 65 % do tempo perdido** (46 | 7,6 s de 11,7 s) e o custo de
   replay recording segue 16–19 ms/frame no regime (`pace lines`) — **alvo nº 1 da
   rodada 3, provado sem E4**. Os hitches de boot continuam (frames 4/127/754:
   948/1546/733 ms, I/O de transição) — alvo nº 2. E4 fica opcional para o H7
   residual (8 | 2,3 s).

A rodada 3 pode codificar: os dois maiores alvos têm causa nomeada e medida.

## 11. Rodada 3 codificada (2026-11-22): o custo da criação de imagens novas (H9) — `provado` na causa, `hipótese` no ganho

Decomposição da gravação na sessão de 07:29 (`pace cost`, frame 168): **14,4 ms/frame =
draws 4,0 + other records 8,5 + commit 1,4 + submissão 0,2 + texturas 0,3** — e os spikes
H9 (os hitches ≥100 ms com `replay recording` 100–190 ms) são **storms de imagens novas no
thread de replay**: frame 702 = 86 imagens em 168,7 ms (~2 ms cada); frame 749 = 300 imagens
em 92,9 ms (majflt 237 + pool placement 99,8 ms no mesmo frame); frame 7602 = gravação de
107,8 ms com **33,7 ms de CPU** (travado, não trabalhando). O prewarm já cobre H1
(1005 pipelines feitos à frente em 2,3 s; o frame path esperou 0 ms). As prioridades do
replay thread já existem (`DM_VK_REPLAY_NICE`, live, default −8).

Codificado nesta rodada (gate: nada muda para outros jogos; toda a mudança é dentro de
`__ANDROID__` e os outros títulos continuam sem os switches):

1. **Cache das queries de formato** (`vka_tex.c`): `vkGetPhysicalDeviceImageFormatProperties`
   perguntava ao driver até 3× por imagem nova, sempre a mesma resposta para o mesmo
   (formato, tipo, uso, flags) — num storm de 300 imagens, centenas de perguntas na mesma
   janela, todas no thread de replay. Agora uma tabela de 128 entradas (sob `g_tmu`) guarda
   o veredito por tupla; o fallback (derrubar STORAGE, depois os usos de attachment) fica
   idêntico. Log único ao passar de 1024 acertos. `hipótese`: parte do custo por imagem
   (0,3–2 ms) era a query; o quanto, o aparelho diz.
2. **Split do custo no log** (`vka_perf.c`): a linha dos longframes agora separa o tempo de
   driver das imagens novas — `new images 86, 168.7 ms (driver 84.2) with their first
   content` — para a próxima sessão do aparelho dizer se o storm é driver (BO/alocação no
   kernel) ou host (walk/compartilhamento/primeiro conteúdo). `provado` como instrumento.
3. **`env_switches.tsv`**: entradas de `DM_ATRACE`, `DM_FS_FADV`, `DM_MEM_FB_PREFETCH`,
   `DM_PERF_REPORT` — o launcher marcava as flags como "not a switch" (falso-positivo
   visto nos logs de 06:30/07:03/07:29). `provado`.

Medição honesta esperada: se a query era o gargalo, os storms de imagens caem
proporcionalmente aos acertos do cache; o split (driver) nomeia o que sobrar para a
rodada 4 (as candidatas: criar a imagem fora do thread de replay — grande e arriscada —,
e o seed de prewarm do aparelho, `vk_pipelines.keys` → `vk_prewarm.bin` via `game.py seed`).
A meta segue ≤6,1/min; a rodada 2 fechou em 7,9/min.
