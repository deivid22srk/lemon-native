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
| Thermal | eventos `throttle green`/`yellow` (status 0); caps de CPU observados 1497–2265 MHz por cluster | `[overlay]` |
| Settings do usuário | `[adv] none: no Advanced row is set` — nenhum ajuste manual | `[nxlaunch]` |

Sessões: A = 259 s, 5933 frames do guest, média 22,9 fps; B = 396 s, 8945 frames,
média 22,6 fps. Média baixa = menus/carregamentos; gameplay assentado = 30 fps.

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

| Arquivo | Sessão | Conteúdo |
|---|---|---|
| `0100F7E00C70E000-20261121-172921.log` | A (Turnip 1.19) | 3107 linhas: 1554 `[longframe]`, 948 `[vk]`, 153 `[nvn]`, 60 `[nxlaunch]`, 20 `[overlay]`… termina "stopped by the user" |
| `0100F7E00C70E000-20261121-203320.log` | B (PurpleVK 26.3) | 3929 linhas: 2052 `[longframe]`, 1170 `[vk]`, 224 `[nvn]` |
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
| 8 | **H7 Térmico/cpufreq** | throttle green→yellow, status 0; caps de CPU 1497–2265 MHz; sem correlação com os piores stalls | `provado` que não domina | 20 eventos | — | — |
| — | H3 Upload de textura | 4+3 eventos, 56 ms somados, máx 29 ms | `refutado` como causa de stutter nestas sessões | 7 | 0,1 s | 29 ms |
| — | H10 Log/instrumentação | sem pico de I/O de log nos hot paths; sampler de threads só em frames ≥ 100 ms | sem evidência de dano | — | — | — |
| — | H11 GC do app | nenhuma pausa de GC correlacionada nos logs | `refutado` nestas sessões | — | — | — |

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
