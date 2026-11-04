# Four-channel Euclidean sequencer — 2026-11-04

Implemented against `c2ac2f0` with uncommitted changes. No commit, push or publication.
Runtime JAR SHA-256:
`750d473afbe8a54862f4a747432fdd8ee65a2dd6d740a7df5d3572d61868cd03`.
The final native run loaded this same packaged JAR. Source/project archives are refreshed
after collecting this evidence; their final hashes are in `build/libs/SHA256SUMS`.

The approved plan defines behavior. The
[Euclid-O-Matic manual](https://github.com/josbouten/Euclid-O-Matic/blob/main/v0.2/doc/Euclid-O-Matic_v0.2_Manual.pdf)
is the design reference; no firmware, artwork or branding was copied. Original editable
art is in `art/euclidean_sequencer_front.svg`, generated with the existing asset tool.

## Commands and results

| Command / check | Result | Evidence |
| --- | --- | --- |
| `make test TEST='*SequencerTest'` | PASS: exhaustive masks/rotations, controls and hour-long virtual clock | [JUnit](logs/SequencerTest.xml) |
| `make test TEST='*SequencerTimelineTest'` | PASS: separate outputs, chain, envelope continuity and bounded suspension | [JUnit](logs/SequencerTimelineTest.xml) |
| `make test TEST='*SequencerCodecTest'` | PASS: settings/performance separation, pending settings, identity/time, malformed/truncated input | [JUnit](logs/SequencerCodecTest.xml) |
| `make test TEST='*SequencerAudioTest'` | PASS: rendered pulses and intervening silence advance without additional snapshots | [JUnit](logs/SequencerAudioTest.xml) |
| `make verify` | PASS: 108 JUnit, 15 packaged GameTests, 12 launcher tests; zero test failures/errors/skips; formatting, Checkstyle, PMD and artifact inspection | [Log](logs/sequencer-verify.log) |
| `make smoke GRADLE_ARGS=-PqaSequencer TICKS=650` | PASS: packaged functional probe, eight screenshots, 20 native audio teardown cycles | [Final log](logs/sequencer-native-final.log) |
| Native server-work maximum below 2 ms | **FAIL:** maximum 13.640 ms, despite sampled steady work of 0.026–0.052 ms | [Final log](logs/sequencer-native-final.log) |
| Zero native underruns | **FAIL:** one observed; no backend failure was reported | [Final log](logs/sequencer-native-final.log) |
| Listening, two-client SC-004 and 30-minute sequencer load | **PENDING** | [Procedures](../../docs/acceptance.md) |

The native fixture contains 14 modules and 15 cables: a primary sequencer, three clocked
followers facing the other directions, two voices and four envelopes. It edits A to seven
steps, mutes/unmutes, stops/resumes, exercises compact scrolling and closes the editor before
world captures. It uses a fresh disposable world in `run/make/smoke-t_urmmug`; manual worlds
were preserved. Assertions verify accepted state through the real screen/network/server
path. Audio teardown assertions verify zero surviving sources/buffers, not human audition.

An earlier run passed functional assertions but three camera positions were obstructed.
The fixture spacing/cameras were corrected and the native probe repeated. Its
[original log](logs/sequencer-native.log) is retained; original captures remain under
`run/make/smoke-ygy__w0x`. That run is not evidence for the final orientation images.

## Red–green evidence

- Initial test contracts referenced the missing sequencer/domain/timeline/checkpoint APIs:
  [domain](logs/sequencer-red.log), [timeline](logs/timeline-red.log),
  [codec](logs/codec-red.log). These are missing-API compile failures, distinct from the
  executable behavioral failures below. Their corresponding green logs are retained.
- [Audio integration red](logs/audio-red.log): the prepared renderer produced no sequencer
  pulses. [Green](logs/audio-green.log) followed timeline integration in `RenderGraph`.
- [Server integration red](logs/server-red.log): the GameTest failed “Runs without speaker
  or panel lease.” [Green](logs/server-green.log) followed authoritative runtime integration.
- [Checkpoint regression red](logs/checkpoint-red.log): a clock-connected snapshot accepted
  inconsistent internal-clock state. Constructor validation now rejects that record, and
  fallback stopped checkpoints derive external mode from the actual cable. The final
  [codec results](logs/SequencerCodecTest.xml) cover both cases.
- Existing keyboard/MIDI/manual/redstone, detune, save migration/quarantine, graph limits,
  malformed network input and lifecycle tests remain in the full passing suite. Snapshot
  tests now use v5 and explicitly reject older protocol versions.

## Timing and performance

Environment: Apple M2 Pro, 16 GiB RAM, macOS 26.6.2 arm64, Java 21.0.12.1,
Minecraft 1.21.1, NeoForge 21.1.218. DSP measurements use 48 kHz, 128 frames/block,
5,000 warmup blocks and 10,000 measured blocks (26.667 seconds of simulated audio).
The same machine ran the native Minecraft/OpenAL probe at 1920×1080; no acoustic capture
or device-specific latency measurement was performed.

| Four-patch workload (64 modules, 96 cables, eight speakers) | DSP p95 | Measured allocated bytes |
| --- | ---: | ---: |
| Existing reference, zero detune | 0.179 ms | 0 |
| Existing reference, 100-cent detune | 0.256 ms | 0 |
| Four-channel sequencers, all hits, 300 BPM, 100-cent detune | 0.240 ms | 0 |
| Sequencer clock/reset chains, 300 BPM, 100-cent detune | 0.216 ms | 0 |

All meet the retained DSP p95 <1 ms/block gate. See [sequencer measurements](logs/SequencerLoadTest.xml)
and [reference measurements](logs/MeasurementTest.xml). Measured blocks continue after
warmup on a monotonic timeline. These are bounded synthetic benchmarks, not a long native
load session. Clock correctness uses a simulated hour at fractional-period 137 BPM and
requires error below one sample; no wall-clock-hour claim is made.

The final native probe's tick-400 audio p95 was 0.054 ms/block, maximum 5.368 ms, one
underrun, `failed=false`. Server maximum was 13.640 ms. These observations leave native
performance acceptance open; low steady samples do not erase the maximum or underrun.

## Visual review

Inspected the actual native PNGs: crisp labels, four independently animated patterns,
hollow rests/filled hits/outlined playheads, textual mute plus pause markers, exact numeric
entry and responsive scrolling. World overlays align with the authored face and jacks in
all four directions. The seven-step ring replaces the idle sixteen-step marks.

- [Defaults](defaults.png), [edited two-column editor](editor.png), [muted channel](muted.png).
- [Compact one-column layout scrolled to Channel D](compact.png).
- [North with all four trigger cables](north.png), [east](east.png), [south](south.png), [west](west.png).

## Explicit self-review

- **Musical contract:** canonical modular mask, clockwise rotation, channel-specific lengths,
  exact 480-sample pulses, integer clock remainder, next-edge edits, immediate mute release,
  resume/reset ordering and cable-switch edge priming are implemented in pure Java.
- **Authority and bounds:** transport uses existing ownership/range/session/revision checks.
  Digital routing retains output port identity and the existing graph limits. Fast external
  clocks increment a saturating diagnostic; timeline exhaustion suspends the component and
  reports recovery guidance. Server quotas are 1,024 transitions/patch and 4,096/tick.
- **Ownership:** server and each renderer own separate mutable prepared timelines. Snapshot
  objects are immutable. The audio loop uses preallocated graph/buffer state; Minecraft
  rendering stays client-only. Visual projection does not depend on local audio output.
- **Compatibility:** world/block schema remains v1. Module-specific exact parameter counts
  replace the previous four-parameter cap. Legacy oscillator migration and unknown-record
  quarantine remain covered. Only settings/cables persist; all loaded sequencers begin
  stopped. v5 checkpoints include pending settings via module values, active settings,
  clock phase/levels, positions/pulse endings and envelope baseline gates. Matching peers
  are required; downgrade worlds containing the new module only from a backup.
- **Lifecycle:** tests cover closed-editor/speaker-free progression and unload suspension;
  native probe covers cleanup. Late checkpoint adoption matches uninterrupted envelope
  and rhythm progression in deterministic tests. Human listening and two-client timing
  remain separate requirements.
- **Scope:** six-module asset/packaging checks pass. Dependency/platform pins are unchanged;
  no pitch sequencing, probability, swing, division, variable gates, presets or MIDI clock.
  The README's RP-004 remains within existing module/cable/speaker limits.

Implementation self-review is complete. Release acceptance is **not complete**: retain the
native performance failures above, pending human/multiplayer/load evidence and all earlier
security/release gates in the [ledger](../ledger.md).
