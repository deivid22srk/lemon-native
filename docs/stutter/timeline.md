# Timeline — Hogwarts Legacy no Lemon Native (sessão gravada)

Fonte primária: `screen-20261121-173341.mp4` (ZIP do usuário, gravação nativa do aparelho,
1600x720, VFR, 140,7 s, ~25,5 atualizações/s). Fonte secundária: reupload do YouTube
`xDx6tvPkxso` (não usada para métrica — o YouTube recodifica e resampleia; tudo aqui vem
do arquivo original). Análise por `ffprobe`/analisador próprio (`.work/analysis/`).

## Como os hitches foram medidos

A gravação é VFR: um pacote por mudança de conteúdo na tela. O intervalo entre
atualizações da tela é a cadência percebida pelo jogador. **Congelamento** = intervalo
> 100 ms (a 30 fps o jogo deveria apresentar 3+ frames nesse intervalo). A leitura
quantizada do gravador não resolve micro-cadência (judder de sub-frame), mas
congelamentos >= 100 ms são reais.

## Números-base (linha de base, cache/estado do aparelho como gravado)

| Métrica | Valor |
|---|---|
| Cadência p50 (atualização de tela) | 32,8 ms — ~30 fps |
| p95 | 67,6 ms |
| p99 | 253,0 ms |
| p99.9 | ~900 ms |
| Maior congelamento | 1001,8 ms |
| Congelamentos > 100 ms | 89 (38,0/min) |
| Congelamentos > 250 ms | 39 (16,6/min) |
| Congelamentos > 500 ms | 13 (5,5/min) |
| CV dos intervalos de atualização | 1,33 (cadência muito irregular) |
| FPS no overlay do app | 30 fps sustentado (FT 33,3 ms), picos 8–10 fps |

FPS alvo: o overlay do Lemon Native mostra **30 fps** com FT 33,3 ms na maior parte do
gameplay — embora `DM_FPS_TARGET=60`, o cap do host não é aplicado (`[nvn] present cap
60 fps not applied: … the game's own present interval stays`). O jogo apresenta no
próprio intervalo e a cadência observada é 30 fps.

## Linha do tempo dos hitches (tempo do vídeo)

Densidade de congelamentos por janela de 5 s (nº de eventos > 100 ms):

| Janela (s) | Eventos | Cena / ação do jogador | Tipo |
|---|---|---|---|
| 0–5 | 7 | Diálogo em Hogsmeade (legenda pt-BR), câmera quase parada | picos isolados 118–597 ms |
| 5–10 | 4 | Diálogo segue | picos 118–463 ms |
| 10–15 | 2 | Diálogo segue | picos isolados |
| 15–20 | 0–1 | Diálogo termina | estável |
| 20–25 | 2 | Fim de diálogo, jogador se move | estável na maior parte |
| 25–30 | 9 | **Transição para tela preta** (carga de área) | série longa: 569–1002 ms |
| 30–35 | 11 | Tela preta; overlay marca **8 fps / FT 119,6 ms** | série longa contínua |
| 35–40 | 12 | Volta a renderizar (interior escuro) | cauda da série |
| 40–50 | 3–4 | Cutscene interna escura | estável com picos |
| 50–55 | 4 | Cutscene (personagem bebendo) | picos isolados |
| 55–65 | 9 + 11 | Cutscene / transição de cena | série dupla |
| 65–85 | 3–4 | Gameplay na caverna | estável |
| 85–110 | 1–2 | Caverna, explorando | estável |
| 110–141 | 2–2 | Saída da caverna sobre-exposta; overlay desce 30→29→26 fps (FT 34→38 ms) | degradação gradual, sem picos |

Leituras do overlay de FPS (canto superior direito, amostrado a cada ~15 s):

| t (s) | FPS | FT (ms) | 1%/último |
|---|---|---|---|
| 0 | 10 | 100,4 | 2 |
| 8 | 30 | 33,9 | 2 |
| 20 | 30 | 33,4 | 2 |
| 34 | 8 | 119,6 | 4 |
| 50 | 30 | 33,3 | 3 |
| 70 | 28 | 36,2 | 2 |
| 95 | 30 | 33,4 | 24 |
| 110 | 29 | 34,3 | 20 |
| 125 | 26 | 38,5 | 13 |
| 135 | 26 | 38,2 | 16 |

## Lista completa de congelamentos (> 100 ms)

Gerada por `scripts/analyze_stutter.py` → `.work/analysis/video_gaps.json`
(inclui timestamp e duração de cada um dos 89 eventos; amostra):

```
t=0.265s 597ms   t=0.878s 334ms   t=1.211s 235ms   t=1.641s 188ms
t=1.942s 269ms   t=2.260s 366ms   t=4.068s 190ms   t=7.833s 383ms
t=8.498s 463ms   t=9.256s 287ms   t=9.592s 250ms   t=10.023s 118ms
t=11.617s 289ms  t=23.627s 628ms  t=24.255s 569ms  t=25.273s 696ms
t=26.616s 647ms  t=27.263s 1002ms t=28.265s 794ms  t=29.276s 399ms
t=31.285s 634ms  t=36.434s 339ms  ... (completo no JSON)
```

## O que a linha do tempo diz

1. **Dois regimes distintos.** (a) Gameplay assentado: cadência 30 fps regular
   (p50 32,8 ms; bins 1x-mediana = 1576 eventos). (b) Episódios de carga: séries de
   congelamentos 100–1000 ms seguidos por 3–15 s — sentidos como "o jogo travou".
2. **As transições de cena são os piores momentos** (t=23–37: ~30 congelamentos em
   14 s). É onde o jogo carrega área nova (tela preta) — compatível com o padrão
   H1/H2/H5 da taxonomia (compilação, E/S, page faults).
3. **A caverna degrada o FPS sem picos** (30→26 fps, FT 33→38 ms): sobrecarga de GPU
   sustentada, não stall de CPU.
4. O gravador (VFR ~25,5/s) quantiza tudo acima de ~33 ms; micro-judder de 30 fps em
   painel 144 Hz (razão não-inteira 4,8) **não é** verificável neste vídeo — fica
   como hipótese a testar com flag de refresh.

## Correlação com os logs (calibração aproximada)

O log da sessão do vídeo (`0100F7E00C70E000-20261121-172921.log`) cobre 259 s; o vídeo
141 s. O mesmo roteiro de jogo reproduzido na sessão B (log de 20:33, outro driver)
produz **os mesmos episódios nos mesmos eventos de jogo** (ver `triage.md`), o que
torna os stalls reprodutíveis. O mapeamento exato vídeo↔log é incerto (o relógio do
log começa no início da sessão, o vídeo foi gravado a partir de um momento desconhecido):
a correspondência mais consistente é vídeo t=0 ≈ log t≈+30 s (sessão A) — nível
`provável`, não `provado`.
