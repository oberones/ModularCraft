# LFO / CV review — 2026-11-05

Implemented LF-001–006 on baseline `aa231cbd488ec974b28320b4d747602101f2e839`, branch
`001-modular-synth-mvp`; changes remain uncommitted. [Results](results.json),
[LFO panel](panel.png), [filter controls](filter.png), [compact envelope controls](envelope.png).

## Automated evidence

- [Red evidence](red.txt): absent module/ports and signed cutoff mapping; stub waveform,
  timing and clock behavior; missing ADSR CV; protocol/migration; rendered gain/cutoff/
  resonance; delayed previous-panel CLOSE; surviving LFO phase after destination removal.
  Failures were observed before the corresponding implementation/correction.
- [Full verification](verify.txt): 144 JUnit tests, 12 Python launcher tests, 29 packaged
  GameTests; formatting, Checkstyle, PMD, Java 21 compilation and eight asset sets pass.
  The actual packaged JAR loads without client classes on the headless server.
- [Waveforms/timing](LfoTest.xml): all five shapes and polarities, seven divisions, high-input
  priming, loss/reacquisition, disconnection, manual and synchronized one-hour sample timing.
  [Recovery](LfoRecoveryTest.xml): stage/sustain checkpoint equivalence, frequency/shape
  continuity, fast-clock rejection, tempo changes, component merge history and invalid values.
- [Modulation](LfoModulationTest.xml): signed time depths, stage-entry capture, zero depth,
  decay boundary and held sustain. [Audio](LfoAudioTest.xml): audible routing, resonance and
  cutoff changes, bipolar gain clamping. Legacy short sequencer notes retain exact ADSR gain.
- [Codecs](LfoCodecTest.xml): protocol 7 roundtrip/old-peer refusal, exact legacy world-v1
  filter/envelope migrations, repeated decode and malformed-subset refusal. Existing decoder,
  graph/authority and lifecycle tests remain enabled. Port geometry covers one through seven
  jacks on every facing, including the new four/six-port filter/envelope panels.
- [Maximum DSP fixture](LfoLoadTest.xml): four patches, 64 modules/96 cables/eight speakers,
  48 kHz/128 frames, 5,000 warmup and 10,000 measured blocks: p95 **0.266083 ms** against
  <1 ms. Measured allocation **136 bytes total**, within the existing <4,096-byte allowance;
  this is not a claim of exactly zero VM allocation.

The default managed GameTest instance initially produced quarantined module failures after
repeated historical runs. Fresh isolated instances passed; existing test/manual worlds were
not deleted. An initial panel-close GameTest incorrectly inspected a held FakePlayer gate,
which the server deliberately releases in the same tick. The corrected regression observes
retained keyboard pitch, fails with the old close behavior, and passes with the target guard.

## Native evidence and limits

Apple M2 Pro, 16 GiB, macOS 26.6.2 (25G83), arm64, Eclipse Adoptium 21.0.12.1; 854×480
window, render distance 12, simulation 8, VSync off. Native artifact hashes are recorded
individually in results.json; no physical MIDI or other-platform qualification is implied.

[Native UI transcript](native.txt): `make smoke GRADLE_ARGS=-PqaLfo TICKS=600`, fresh
`run/make/lfo-native-final`. Actual mouse actions cycle every waveform and set frequency,
polarity and division; resonance and all ADSR depths are accepted, invalid depth is refused,
Page Up/Down reaches compact-panel fields, and ten recovery cycles return owned audio
channel/timer counts to zero. Screenshots were inspected; every LFO selector is visible,
filter depths are grouped with their controls, and envelope scrolling has a visible indicator.
Maximum observed underruns before counters reset: **1**. Recovery counters resetting to zero
are not used to erase that observation.

[Native load transcript](native-load.txt): `make smoke-load GRADLE_ARGS=-PqaLfo TICKS=1200`,
fresh `run/make/lfo-load-20261105a`: four maximum-size patches, all clocks running, finite
protected output. Final audio p95 **0.299334 ms**, maximum audio block **5.158 ms**;
server p95 **0.593292 ms** over 1,080 samples, measured max **8.516292 ms**. Both p95
budgets pass for this approximately one-minute workload, but **one underrun** was observed.
These are short, single-client measurements, not 30-minute or multiplayer qualification.

Native builds preceded the final destination-removal correction. Their exact artifact hashes
remain separate; the final runtime passes the dedicated packaged phase-preservation regression
and full verification. DSP processing code is unchanged between the measured load artifact
and final runtime. Human RP-006 listening/acoustic, two-client timing/late join, sustained
native zero-underrun performance, and every earlier security/platform/release gate remain
PENDING or retain their earlier BLOCKED/FAIL status. No public release is accepted.

## Explicit self-review

Reviewed appended identifiers, strict bounds and signed mappings, stage-entry ordering,
analytic phase and checkpoint reconstruction, same-port source identity across merge/split,
zero-depth compatibility, exact stored-record migrations, world/quota preservation, common/
client class separation, resource cleanup, and localization/artwork provenance. Runtime
processing remains bounded and contains no new per-sample application allocations, I/O,
locks, or world access. Dedicated tests cover the two lifecycle issues found during review:
delayed old-panel close and destination removal restarting surviving LFO phase. No dependency
pins, AGENTS.md, manual worlds, commit, push, or publication were changed/performed.
