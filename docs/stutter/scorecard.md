# Scorecard — correção de stuttering do Hogwarts Legacy (perf/hogwarts-legacy-stutter)

Ciclo Criador + Crítico (rubrica da seção 6.3 do briefing). Cada rodada: um run do
`build-apk.yml` (APK `LemonNative-hogwarts-stutter-rNN-<sha>.apk`) + o relatório do
Crítico com o formato obrigatório. Linha de base medida em `triage.md`/`timeline.md`.

| Rodada | Run do CI | APK | Nota geral | Δ | Status |
|---|---|---|---|---|---|
| 1 | r29 = run 44966959699 (26ae007), success | `LemonNative-hogwarts-stutter-r29-26ae007.apk` (33,2 MB) | 5.9 | — | avaliada |
| 2 | r30 = run 44971206327 (bb1f9a0), success | `LemonNative-hogwarts-stutter-r30-bb1f9a0.apk` | 6.0 | +0.1 | avaliada |
| 2 (fix-up) | r31 = run 44972795183 (6354999), success | `LemonNative-hogwarts-stutter-r31-6354999.apk` (33,2 MB) | 6.0 | — | correções pós-relatório |

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

### Rodada 02 — Nota geral: 6.0 / 10 (anterior: 5.9, Δ +0.1)

Nota por cluster (Causa raiz 25% · Efetividade 25% · Segurança 15% · Custo 10% · Robustez 10% · Telemetria 5% · Código 5% · Docs 5%):

| Cluster (ID, tipo, impacto) | Causa raiz | Efetividade | Segurança | Custo | Robustez | Telemetria | Código | Docs | Nota |
|---|---|---|---|---|---|---|---|---|---|
| a) H1+H9 (+H8): compilação de pipeline + replay worker, 1,0 | 8 | 1.5 | 9 | 9 | 4.5 | 9 | 8.5 | 8 | **6.3** |
| b) H5: page faults (majflt storms), 0,6 | 7 | 2 | 8 | 7 | 5.5 | 7.5 | 8 | 8 | **5.9** |
| c) Sobrecarga de GPU sustentada, 0,9 | 7 | 0.5 | 10 | 10 | 2 | 7 | 5 | 8 | **5.6** |
| d) H2: E/S de assets no caminho principal, 0,35 | 7 | 2 | 8 | 8 | 5.5 | 8 | 8 | 8 | **6.0** |
| e) H4/H6/H7 menores, 0,2 | 7 | 2 | 10 | 10 | 3 | 7 | 5 | 7.5 | **6.0** |

Nota geral = (6.3×1.0 + 5.9×0.6 + 5.6×0.9 + 6.0×0.35 + 6.0×0.2)/3.05 = **6.0**. A subida é pequena e **deve** ser pequena: a rodada consertou o instrumento (crash, race, I/O na frame path, reprodutibilidade) e os docs — não consertou nenhum stutter, não mediu nada em dispositivo. Cluster (c) continua intocado (2º maior impacto, sem experimento próprio nem em E1–E5).

**Métricas (baseline → atual)**: continua NÃO havendo medição em dispositivo; nenhum "depois" existe. Baseline inalterado (vídeo p50 32.8 / p95 67.6 / p99 253.0 / máx 1001.8 ms / CV 1.33 / 38.0 congelamentos >100 ms por min; maior stall 5741.6 ms reproduzido nos dois drivers). **Achado novo da re-conferência**: drift de p50 dentro da sessão do log B — 33.4 ms nas primeiras janelas → 34.8/35.2/38.9/39.2 nas janelas 5100–6300 → **57.0 ms na janela 8400** (p95 175.4): degradação sustentada intra-sessão, evidência direta para o cluster (c)/H7.

**Travas aplicadas**: (1) teto de evidência sem dispositivo — Efetividade de (b)/(d) mantida em 2; (2) gate de data race (cap 6.0) **não aplicado** com justificativa: a corrida lesiva foi eliminada por construção (writer thread única, snapshot sob `g_mu`, I/O sem lock); resta corrida formal benigna em dois ints pós-join (MODERADA, correção obrigatória). Não aplicadas: build vermelho (r30/r31 verdes), telemetria default-on (4 flags default-off), redução de qualidade/regressão visual.

**Falhas**:
- **[MODERADA]** `scorecard.md` — p50/p95 declarados "fonte exata" com faixa cherry-picked (32,1–33,4/47–86); real das 29 janelas: p50 **32.09–57.02**, p95 **42.02–187.63**.
- **[MODERADA]** `validation-plan.md` — não commitado; META_HITCHES sem fórmula; não fixava o driver (confundidor cross-driver); E5 sumiu sem justificativa.
- **[MODERADA]** `triage.md` §3 — tabela ainda "60/20" enquanto a frase nova dizia 55/4; linhas vs. ocorrências misturadas sem regra.
- **[MODERADA]** `hle_perf_report.c:207` — `g_thread_on=0; g_final=0;` pós-join sem mutex (corrida formal benigna; correção obrigatória).
- **[POLIMENTO]** malloc/pthread_create falham em silêncio; `why` do JSON perdeu a discriminação hitch×window; path absoluto no script; linha 1 do scorecard vazia; relatório da rodada 1 não colado.

**Hipóteses**: provado (reprodução cross-driver, H1 por cache miss `0 hit / 71–72 missed` no frame 300 de ambas as sessões, H5 storms até 2309 majflt/frame, H2 bursts 58–97 MB/192–775 ms, GPU-bound sustentado como teto de FPS; H3/H11 refutados; H7 não domina; script reproduz a análise exatamente — verificado por execução). Provável (mecanismo H1 = seed ausente + fila única do replay worker; H5 = pools file-backed + paging; causalidade H2). Em aberto (eficácia das flags em dispositivo, atribuição dos stalls ≥1 s, judder 30 fps@144 Hz, ganho de SEQUENTIAL via FUSE, ganho líquido do WILLNEED pós-DONTNEED).

**O que melhorou**: stride inicializado (BLOQUEANTE resolvida, `hle_perf_report.c:168`); I/O 100% fora da frame path (GRAVE resolvida); corrida de desligamento eliminada em substância (GRAVE resolvida; residual virou MODERADA); script commitado e reprodutível (`log_stats.json` idêntico, `video_gaps.json` byte-idêntico — rodado em sandbox); docs corrigidos com fontes; plano de validação S0–S3 escrito; CI r30/r31 verdes; nenhuma correção de desempenho codada sem trace — correto.

**Veredito**: **Continuar.** Falta a condição da seção 8: **medição em dispositivo (E1–E5)**. Pré-requisitos: commitar `validation-plan.md`, corrigir p50/p95 do scorecard e a tabela §3 da triage, fixar o driver no plano. A rodada 3 só deve codar (H1/H9) depois que o S3/trace nomear os stalls ≥ 1 s.

### Rodada 01 — Nota geral: 5.9 / 10 (anterior: —)

Nota por cluster: a) H1+H9 **6.1** (Causa 8 / Efet. 1.5 / Seg. 9 / Custo 9 / Rob. 3 / Tel. 8 / Cód. 8 / Docs 8); b) H5 **5.8** (7/2/8/7/5/7/8/8); c) GPU sustentada **5.6** (7/0.5/10/10/2/7/5/8); d) H2 **5.9** (7/2/8/8/5/8/8/7); e) menores **6.0** (7/2/10/10/3/7/5/7). Nota geral = (6.1×1.0 + 5.8×0.6 + 5.6×0.9 + 5.9×0.35 + 6.0×0.2)/3.05 = **5.9**.

**Métricas**: sem "depois" — só baseline. Frame time population-enviesada (longframes = cauda adaptativa): A p50 40.5 / p95 178.5 / p99 806.7 / máx 5547.2; B p50 42.1 / p95 169.8 / p99 689.8 / máx 5741.6. Vídeo: 38.0 congelamentos >100 ms/min (89 eventos), 16.6 >250, 5.5 >500; maior hitch 5741.6 ms (frame 141 B / gêmeo 145 A); CV 1.33. Correções candidatas com efeito medido = zero até então.

**Travas aplicadas**: risco de data race não resolvido → Bloqueante (teto 6.0) — flush(SIGTERM) × rpt_write(render); correção sem medição → Efetividade baixa em (b)/(d); não aplicadas: build vermelho, telemetria default-on, redução de qualidade, hitch sem alvo.

**Falhas**: **[BLOQUEANTE]** `hle_perf_report.c` coarsen — `g_w.stride` nunca inicializado → loop infinito + OOB na janela 240 (frame ~72000 ≈ 40 min a 30 fps com a flag de E1 ligada). **[GRAVE]** race flush×rpt_write (globals sem sincronização, dois escritores do mesmo .tmp). **[GRAVE]** rpt_write ~700 KB síncrono na thread de present a cada 300 frames — efeito observador nos frames medidos; "<1%" não medido. **[GRAVE]** `scripts/analyze_stutter.py` citado por 2 docs e inexistente no repo. **[MODERADA]** scorecard: p99 "46–931" (real 1497.57 na janela 2100), over-target "269–295 de 300 @33,3 ms" (impossível — o limiar do log é 16,7; real 267–300); p50/p95 sem fonte. **[MODERADA]** experiments: F_RDAHEAD citado mas removido do código; sem caveat FUSE. **[MODERADA]** triage: `[nxlaunch]` 60→55, `[overlay]` 20→4 (A)/2 (B), H7 20→6 eventos, caps 1497–2265→1075–2995 MHz, H3 56→112,1 ms, GPU máx 206→422.7 ms. **[POLIMENTO]** validation-plan referenciado e inexistente; ring de 1024 sem totais; gcc -Werror pré-existente; TSVs sem as flags (deliberada, anti-conflito).

**Hipóteses**: provado (stalls reprodutíveis nos mesmos eventos em dois drivers — frames 145/141, 2314/2353, storms 760/758, 1266/273; H1 por cache miss; H2 bursts 58–97 MB/192–775 ms; GPU-bound sustentado; H3/H11 refutados; H7 não domina). Provável (H1 = seed ausente + fila do replay; H5 = pools file-backed; causalidade H2). Em aberto (calibração vídeo↔log; judder 144 Hz; eficácia das flags; "cache do driver não cobre" parcialmente confundido pela troca de driver).

**Veredito**: **Continuar**, condicionado a: corrigir o BLOQUEANTE e as GRAVEs **antes** de usar `DM_PERF_REPORT=1` nas sessões de validação; commitar o script e corrigir os números. Nenhuma hipótese medida em dispositivo ainda — E1–E5 precisam rodar no APK r29 antes de qualquer correção adicional; a rodada 2 só deve codar depois do trace (E4) nomear os stalls ≥ 1 s.

### Verificação de números (Crítico, rodada 1)

15 números conferidos contra os dados brutos; 6 discrepâncias, todas em docs (nenhuma nos JSONs) — corrigidas nas rodadas 1-fix-up e 2 (tabela completa no relatório original do Crítico, arquivado no histórico da conversa e resumido acima).
