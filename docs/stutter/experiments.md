# Experiments — flags de desempenho do Hogwarts Legacy no Lemon Native

Cada flag: o que faz, o hitch que ataca (taxonomia em `triage.md`), efeito esperado,
custo e como medir. As novas flags (Rodada 1) são **desligadas por padrão**, restringidas
ao que o projeto já suporta (variáveis `DM_*` do `env.txt` do aparelho + live control
`ctl.txt`). Um único APK testa todas as hipóteses sem novo build.

## Como ligar

1. APK da rodada (nome `LemonNative-hogwarts-stutter-rNN-<sha>.apk`, ver scorecard).
2. No armazenamento do app: `files/env.txt` (ou a tela Advanced settings, que o app já tem) —
   adicionar a linha `DM_<FLAG>=1`. Reiniciar a sessão do jogo.
3. Verificar no log: as linhas `[nxlaunch] env DM_…=…` ecoam o que foi setado.
4. Cada experimento roda **cache frio** (apagar `files/vk_pipelines.keys` e o cache de
   pipelines antes) e **cache quente** (segunda sessão sem apagar), 3+ min na mesma rota.

## Flags novas (Rodada 1)

| Flag | Default | Ataca | Efeito esperado | Custo / risco | Como medir |
|---|---|---|---|---|---|
| `DM_PERF_REPORT=1` | off | (telemetria) | Escreve `stutter_report.json` em `files/temp/<título>/`: janelas de 300 frames (p50/p95/p99/max, over-target, drops), histograma completo de frame time e a lista de hitches `[longframe]` com atribuição de causa + totais agregados (todos os hitches, não só o ring de 1024). Base para comparar baseline vs. correção em dispositivo. A serialização e o I/O rodam numa **thread dedicada** (os callers só copiam o snapshot sob mutex — dezenas de µs por janela); a escrita final acontece no desligamento gracioso | Off = um branch; on = memcpy ~620 KB por janela de 300 frames + ~70 KB/s de I/O na thread de escrita | O arquivo em si; comparar janelas antes/depois |
| `DM_ATRACE=1` | off | (telemetria) | Marcadores ATrace/Perfetto em: compilação de pipeline no frame path (`vkCreateGraphicsPipelines (whole)`), gravação do replay worker (`replay worker: record submission`), leitura de arquivo do jogo ≥ 1 MiB (`guest ReadFile (pread)`) | Um branch por seção quando ligado; nada quando off | `perfetto` no aparelho; os stalls aparecem com o nome do responsável |
| `DM_FS_FADV=1` | off | **H2** (E/S de assets) | `posix_fadvise(SEQUENTIAL)` no fd de cada pacote do jogo aberto (≥ 64 MB): o kernel adianta a leitura e os bursts medidos (58–97 MB em 192–775 ms) podem sobrepor o decode do jogo. **Caveat FUSE**: os `.nup` estão em `/storage/emulated/0` (FUSE), onde o readahead de `SEQUENTIAL` costuma render menos que em ext4/f2fs — o E2 é justamente o teste dessa hipótese | Page cache segura páginas de readahead por mais tempo (limitado pelo kernel); sem alocação nossa | `files: read X MB / Y ms` nos `[longframe]` das transições, cache frio vs. quente |
| `DM_MEM_FB_PREFETCH=1` | off | **H5** (majflt storms) | Após mover um range de pool file-backed (código de shader) para o arquivo, `fadvise(WILLNEED)` sobre o range: os faults de leitura posterior viram minor faults | **RAM**: o page cache volta a segurar até os bytes que o pool file-backed tinha liberado (o que o `DONTNEED` economizou). Em Hogwarts no log: ~15–50 MB no meio da sessão; em jogos com pools de 2,9 GB pode encher a RAM — usar com atenção. **Churn**: WILLNEED logo após o DONTNEED do mesmo range re-lê do flash o que acabou de ser escrito — se o range não for relido logo, o prefetch é custo sem ganho | `page faults N major` nos `[longframe]` durante compilações; p99 das janelas |

## Flags existentes do projeto relevantes (já no APK, sem novo build)

Estas já existiam (fonte: `vka_pso.c`, `vka_psogate.h`, `hle_nvn.c` e linhas `[auto]`
do log). Documentadas aqui porque são os controles das hipóteses H1/H6/H7:

| Flag | Default neste aparelho | Ataca | Nota |
|---|---|---|---|
| `DM_VK_PSO_ASYNC` | 2 | H1 | Compilação assíncrona com skip de draws (pop-in rotulado); 0 = tudo no frame path. O gate (`vka_psogate.h`) serve o frame primeiro |
| `DM_VK_PSO_SLOTS` | 1 (auto por driver ID 18) | H1 | Compilações simultâneas (memória permitindo). Turnip perde o aparelho com >1 por compilações longas |
| `DM_VK_GPL` | on | H1 | Graphics pipeline libraries: pipeline ausente é linkado de bibliotecas e o otimizado é feito em background |
| `DM_VK_PACE_PANEL` | on (PEANUTS) | H6 | O pacer agenda em refreshes inteiros medidos do painel (não 1/60s do clock do host) |
| `DM_LONGFRAME` | on | (telemetria) | As linhas `[longframe]`; 0 desliga (menos I/O de log) |
| `DM_LONGFRAME_THREADS` | 250 | (telemetria) | Amostragem de threads nos frames ≥ 100 ms; 0 desliga |
| `DM_VK_GPU_FLOOR` | KGSL | H7 | Piso de clock da GPU pelo KGSL (host DCVS: highest clock only) |
| `DM_VK_ADPF_SPAN` | — | H7 | ADPF/PerformanceHintManager (verificar suporte no aparelho antes do experimento) |
| fps locker / frame step | perfil | H6 | O cap de 60 do host não é aplicado neste jogo (`present cap not applied`); o jogo apresenta no próprio intervalo — experimentos de pacing usam o fpslocker do perfil |

## Experimentos planejados (ordem por impacto)

1. **E1 — baseline instrumentalizado** (`DM_PERF_REPORT=1` apenas): mesma rota do vídeo
   (Hogsmeade → transição → cutscene → caverna → saída), 5 min, cache frio e quente.
   Saída: `stutter_report.json` de cada sessão — a linha de base oficial (p50/p95/p99/p99.9,
   hitches/min, causa dominante por hitch).
2. **E2 — `DM_FS_FADV=1`** sobre E1: mede o tempo de leitura das transições
   (hipótese: bursts 195–775 ms caem; total da sessão pouco muda).
3. **E3 — `DM_MEM_FB_PREFETCH=1`** sobre E1: mede majflt/frame nas compilações
   (hipótese: storms 758–2309 → ~0 major faults; custo de RAM medido pelo footprint do report).
4. **E4 — trace** (`DM_ATRACE=1` + perfetto 5 min na rota): confirma se os stalls de 3–5 s
   são worker de replay, compilação no frame path ou espera de condvar — decide a Rodada 2
   (paralelizar replay vs. pré-aquecer seeds vs. fila maior do encoder).
5. **E5 — pacing** (fpslocker do perfil + `DM_VK_PACE_PANEL`): confirmar se a cadência de 30 fps
   em painel 144 Hz (razão 4,8) gera judder percebido além dos hitches medidos.

## Resultados (atualizado 2026-11-22)

- **E1 — CUMPRIDO** (`provado`): primeiro `stutter_report.json` válido em 22/11 07:03
  (7,2 min, `eeb5be5`, Turnip T30) — 91 hitches ≥100 ms (12,6/min), p50 32,3 ms,
  dropped 4,1 %; contador oficial == parser do log (91). Detalhes e comparativo com o
  baseline de 06:01 em triage.md §9. Reserva: `DM_MEM_FB_PREFETCH` ausente na sessão.
- **E2 — parcial** (`provado`): sessão de 07:29 (7,9 min, mesma rota do save, 3 flags):
  I/O de transição 5,2 → 3,8 s = **−27 %** (alvo ≥30 %), contagem 12 → 8, sem hitch novo
  noutra causa. A um passo do critério.
- **E3 — CUMPRIDO** (`provado`): com `DM_MEM_FB_PREFETCH=1` em 07:29, majflt
  74 673 → 7 975 (−89 % vs. a sessão FADV que revelou o efeito colateral; footprint
  +1,6 % ≤ 10 %); gaps "nada ocupado" caíram junto (21 → 8).
- **E4 — opcional**: os gaps caíram 106 → 34 → 21 → 8 (24,8 → 2,3 s); H7 residual não
  é mais alvo principal.
- **Acumulado 22/11**: 30,4 → 7,9 hitches ≥100 ms/min (−74 %); meta ≤6,1/min (−80 %);
  tempo perdido −83 %; dropped 11,5 % → 2,4 %. H9 (replay recording) = 65 % do tempo
  perdido, alvo nº 1 da rodada 3.

## Métrica de sucesso (por flag)

- E2: tempo somado de `files: read` nas transições cai ≥ 30% sem novo hitch noutra causa.
- E3: soma de major faults por sessão cai ≥ 50% com footprint +≤ 10%.
- E4: cada stall ≥ 1 s fica atribuído a um marcador nomeado (provado, não provável).
- Todos: p99 de frame time e hitches/min não pioram; zero crash/pop-in novo.
