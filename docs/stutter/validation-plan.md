# Validation plan — medição em dispositivo (Etapa 2 do briefing)

Estado: **PRONTO PARA MEDIÇÃO EM DISPOSITIVO** (não "corrigido"). Este plano diz ao
usuário exatamente o que rodar, em que ordem, e o que devolver para o Crítico
reavaliar **com o teto de evidência removido**.

## APK

`LemonNative-hogwarts-stutter-r29-26ae007.apk` ou posterior (r30+ com as correções
do relatório da rodada 1 — usar sempre o **último run verde** da branch
`perf/hogwarts-legacy-stutter`; o nome do arquivo leva o run e o SHA). Não instalar
APKs de runs vermelhos.

## Flags (ordem de ligação, uma por sessão)

Adicionar em `files/env.txt` (uma linha por sessão; reiniciar o app após trocar):

| Sessão | env.txt | Objetivo |
|---|---|---|
| S0 (baseline) | `DM_PERF_REPORT=1` | linha de base oficial (histograma completo + hitches) |
| S1 | S0 + `DM_FS_FADV=1` | E2 — E/S (H2) |
| S2 | S0 + `DM_MEM_FB_PREFETCH=1` | E3 — page faults (H5) |
| S3 | S0 + `DM_ATRACE=1` | E4 — trace dos stalls ≥ 1 s (H1/H9) |
| S4 (opcional) | fpslocker do perfil / `DM_VK_PACE_PANEL` | E5 — pacing/judder (H6); só depois de S0–S3, na mesma rota |

`DM_LONGFRAME` continua on (default) — as linhas `[longframe]` alimentam o report.

## Roteiro fixo de teste (a mesma rota do vídeo, nos dois modos)

**Pré-condições (obrigatórias para a comparação valer):** o MESMO driver de GPU em
S0–S3 (não trocar Turnip/PurpleVK no meio — a troca foi o confundidor que a Fase 0
teve que controlar); térmica em estado similar (aparelho esfriado entre sessões,
mesmo carregador, sem rodar outras coisas); aparelho carregando ou sempre com a
mesma faixa de bateria; mesma rota e mesma ordem.

**META_HITCHES com fórmula explícita:** hitch = frame do guest com tempo inteiro
> 2× o alvo (alvo = 33,3 ms, o 30 fps observado; logo > 66,7 ms). A contagem vem do
`stutter_report.json` de cada sessão (campos `hitches_total` e `hitches_over_100ms`/
`over_250ms`/`over_1000ms`, somados sobre TODAS as janelas). Meta: `hitches_total`
da sessão com flag cair ≥ 80% vs. S0 no MESMO modo de cache (frio vs. frio, quente
vs. quente), sem que `hitches_over_1000ms` suba nem apareça nova causa dominante.

Reproduzir o fluxo gravado em `screen-20261121-173341.mp4`, nesta ordem, com a
mesma rota do vídeo: **Hogsmeade (diálogo) → transição/preto → cutscene interna
escura → caverna → saída sobre-exposta**. **Cache frio** e **cache quente**:

1. **Cache frio**: apagar `files/vk_pipelines.keys` (e o cache de pipelines ao lado)
   antes da sessão. Rodar o roteiro por **pelo menos 3 minutos** depois do boot do jogo.
2. **Cache quente**: repetir o roteiro na sessão seguinte, sem apagar nada.
3. Cada sessão S0–S3 segue o mesmo roteiro (S3 apenas 5 min na rota inteira, para o trace).

## Como gravar

- Gravação de tela nativa do aparelho (igual à que já foi enviada) — o arquivo
  `screen-<data>.mp4` com o overlay de FPS do app visível.
- Marcar verbalmente/visualmente o início do roteiro (abrir o mapa e fechar, por
  exemplo) para calibrar vídeo↔log.

## Como exportar os dados

1. **`stutter_report.json`**: ao sair do jogo (desligamento gracioso pelo launcher),
   copiar `files/temp/0100F7E00C70E000/stutter_report.json` — um por sessão
   (renomear com a sessão: `stutter_report_S0_frio.json` etc.).
2. **Log do app**: o mesmo arquivo de log de sempre (`0100F7E00C70E000-*.log`).
3. **Trace Perfetto** (sessão S3): com `DM_ATRACE=1`:
   `adb shell perfetto -o /data/misc/perfetto-traces/trace --time 300s -b 512mb sched freq idle am wm gfx view binder driver long_track`
   (ou o tile "System Tracing" da barra rápida, gravar durante a rota, parar e exportar).
4. Enviar de volta: os JSONs de cada sessão, os logs, o(s) vídeo(s) e o trace.

## O que o Crítico vai comparar (critérios de sucesso)

- **META_HITCHES**: redução ≥ 80% de hitches/min (>2× o alvo de 33,3 ms) vs. S0,
  em cache frio e quente.
- p99/p99.9 de frame time e cadência (CV) melhores que S0 nos dois modos.
- S1: soma de `files: read` nas transições cai ≥ 30%.
- S2: major faults por sessão caem ≥ 50% com footprint +≤ 10%.
- S3: cada stall ≥ 1 s fica atribuído a um marcador nomeado (H1 vs H9 vs outro).
- Sem regressão visual (pop-in/draws perdidos), sem crash, sem novo stall noutra causa.
