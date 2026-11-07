# Twenty-module patch capacity — 2026-11-07

Implemented and self-reviewed; sustained performance/release acceptance remains PENDING.
Machine-readable measurements and test totals: [results.json](results.json).

The shared module cap is 20 for server components, snapshots and every per-module
checkpoint collection. The renderer derives its bound as 4 × 20 = 80; the player cap
remains 64. Cable/speaker/world/byte/transition budgets and dependency pins are unchanged.
Network/snapshot protocol 11 refuses older peers; saves remain schema 2.

## Automated evidence

- Red: all five new capacity tests failed on the original module/snapshot/protocol bounds.
  Green: all five pass. They cover growth through 17–20, atomic rejection at 21, splitting
  and merging 10+10, save/load, snapshots, 20-entry checkpoints, malformed counts, old
  protocol rejection, and the unchanged 24-cable ceiling.
- The expanded four-patch benchmark exposed the renderer's former 64-module bound
  (`active_modules`); deriving 80 from the shared patch limit resolved it.
- `make verify GAMETEST_DIR=run/make/patch-capacity-gametest-confirm`: PASS. 25 launcher
  tests, 185 JUnit tests, formatting, Checkstyle, PMD, strict dependency verification,
  42 packaged GameTests, and artifact inspection. The first packaged run failed the
  existing `sneakremovalthroughgamemode` test; all 42 passed without code changes in a
  fresh instance. Its cause is unestablished; the initial failure is retained locally.
- Twenty-entry snapshot sizes: Amp 5,740; Envelope 6,940; Keyboard 2,460; Sequencer
  10,480; LFO 5,040 bytes, all below the retained 32 KiB limit.
- Pure DSP: 80 modules / 88 cables / eight speakers across four owners, with four
  detuned saws per patch, two filters per voice and all sequencer hits set to 16.
  After 5,000 warmup blocks, 10,000 measured 128-frame blocks at 48 kHz yielded
  **0.714792 ms p95, zero measured steady-state allocations** (<1 ms target PASS).
- Documentation links, localization/generator agreement and `git diff --check`: PASS.
  `AGENTS.md`, dependency pins and manual worlds are unchanged.

## Native evidence

`make smoke-load GRADLE_ARGS=-PqaAmp TICKS=1200 QA_DIR=run/make/patch-capacity-native`
completed successfully using the packaged runtime JAR in a fresh disposable instance.
The fixture verifies four 20-module/22-cable patches, four running sequencers and eight
admitted speakers. Distinct synthetic owners exercise owner-authorized operations.
It uses two detuned saws and two square oscillators per patch and default rhythmic hits;
this differs from the pure DSP workload above.

Host: Apple M2 Pro, 16 GiB RAM, macOS 26.6.2 (25G83), aarch64, Eclipse Adoptium
Java 21.0.12.1+1-LTS; Minecraft 1.21.1 / NeoForge 21.1.218. Approximately 60 seconds
in-world, 75 seconds launcher elapsed. This is an absolute workload probe, not an
inactive/active frame-duration comparison.

- Server p95 **0.668917 ms**, below the retained 2 ms target. The sampled interval
  maximum was 2.374917 ms; the overall diagnostic maximum was 8.002709 ms.
- Final rolling audio p95 **0.583209 ms**. The initial reported window was
  **1.190750 ms**, exceeding the retained 1 ms target; later windows were below it.
- **One underrun**, maximum audio block **15.374667 ms**, peak 0.141469; no reported
  audio failure. These counters prevent claiming clean native performance acceptance.

Thirty-minute sustained runs, two-client timing, human acoustic/listening acceptance,
and earlier security/platform/release gates remain **PENDING**. No commit, push,
publication, or installation into a play/server instance was performed.

Self-review checked shared bounds, old-peer refusal, transactional state preservation,
owner-correct 80-module fixtures, legal cable distances, unchanged quotas, and accurate
historical evidence. Raw red/green/verify/native logs are retained locally in this
folder as ignored `.log` files; their relevant results are captured in the JSON above.
