# ModularCraft MVP implementation plan

The user-approved implementation plan supplied on 2026-11-03 is implemented here.
The [specification](spec.md) remains authoritative. Existing Spec Kit files are retained.

## Target and architecture

Minecraft **1.21.1**, NeoForge **21.1.218**, Java **21**, ModDevGradle **2.0.148**,
Gradle **8.13**. macOS is the initial playback target; other loaders/platforms are
unqualified. Official references: [setup](https://docs.neoforged.net/docs/1.21.1/gettingstarted/),
[payloads](https://docs.neoforged.net/docs/1.21.1/networking/payload/),
[GameTests](https://docs.neoforged.net/docs/1.21.1/misc/gametest/).

`domain`, `dsp`, `transport` contain Minecraft-independent Java; `game` and `network`
are server/common adapters; `client` owns UI, rendering, audio. Logical server owns
mutable world state. Audio owns preallocated processing state; preparation receives
immutable snapshots and never touches world objects. Use the existing Minecraft
OpenAL context and sound executor, with a narrow documented adapter, no second device.
48 kHz, 128 frames; PolyBLEP saw, TPT state-variable low-pass, linear sample ADSR,
Q 0.707..10, 10 ms ordinary-control/graph transitions, 5 Hz DC block, protected stereo mix.
The alias red test required 4x internal oscillator oversampling and a 95-tap Blackman
decimator; public graph/envelope rate remains 48 kHz. The added group delay is 0.245 ms.
L1 coefficient normalization bounds oscillator peaks at the selected level.

## Budgets and ordering

Retain every SS-003 quota. Version 1 world saves; version 7 network/snapshots; edits 4 KiB, snapshots 32 KiB,
world storage 2 MiB. 20 edits/s/player, 200/world; queues 64/player, 256/world.
Cleanup and gate release bypass ordinary throttling. Outbound 128 KiB/s/listener,
2 MiB/s/server. One preparation worker, eight pending jobs, 1,024 client events,
32 ms PCM queue. Events use monotonic server transport, sequence and revision;
clock exchange every second, 40 ms scheduling lead, analytic envelope checkpoints.
Late recovery enters current state. Pause freezes singleplayer transport.
Target DSP/mix p95 <1 ms/block and mod server work <2 ms/tick; measure, do not presume.

## Constitution check

| Principle | Design and evidence |
|---|---|
| I world safety | Server authority, no forced loads; isolated quarantine; dedicated server check |
| II boundaries | Bounded codecs, identity/revision/permission checks; malicious input tests |
| III TDD | Behavior tests observed red before implementation; validation ledger retains logs |
| IV design | Immutable domain contracts, client isolation, warnings/checkstyle/PMD |
| V composition | Typed stable ports, deterministic DAG, exact MU contracts and reference patches |
| VI timing/audio | Sample timeline, analytic checkpoints, virtual-clock and numeric measurements |
| VII bounded work | Explicit caps above, resource cleanup, measured stress fixtures |
| VIII persistence | Stable UUIDs, versioned data, refusal preserves source; roundtrip tests |
| IX reproducibility | Pinned wrapper/dependencies, integrity metadata, inventory, rebuild hashes |
| X player control | Local mute/settings, localized diagnostics, numeric controls, complete guide |

## Qualification

Run `./gradlew test check build`, `./gradlew runGameTestServer`, and packaged dedicated
server plus native singleplayer/two-client procedures in `docs/acceptance.md`.
TDD and each evidence class are tracked separately in `validation/ledger.md`.
No public release, commit, or push is authorized. Required hardware/human gates stay
PENDING until actually observed; builds never substitute for listening acceptance.

## Saw detune extension

Add one oscillator control, Detune, in cents, 0..100 with default 0 and fractional
numeric entry. It is symmetric unison spread: a center saw at the manual/CV pitch plus
saws at `baseHz * 2^(-cents/1200)` and `baseHz * 2^(cents/1200)`. Nonzero spread mixes
each at one third of Level. Zero spread uses only the center voice, preserving the
original sound, including after returning from unison. Smooth the mix and all frequency
targets over 480 samples (10 ms), retain phases/history across graph changes, and prepare
pitch ratios off the audio thread. When enabling unison from zero, side voices inherit
the center phase/history. No stereo widening or extra ports; Audio Out remains mono.
Do not clamp effective side pitches to the base Frequency limits.

Keep world schema 1: migrate only the exact legacy oscillator Frequency/Level parameter
set to Detune 0 in stored world/block records. Preserve other malformed/unknown records.
Network/snapshot v7 requires all three controls and matching peers; append the enum value
to retain existing control ordinals. No dependencies or ownership changes. At most three
voices per oscillator, with side DSP skipped at settled zero spread; existing graph/module/
traffic caps stay fixed. Include maximum spread in the existing DSP load measurement.

| Principle | Detune design and evidence |
|---|---|
| I | Existing logical-server edit path, disposable packaged integration/native fixtures |
| II | Finite 0..100 validation, stale/unauthorized edits and malformed codec tests |
| III | Observe new behavior tests failing before implementation, then full automated checks |
| IV | Immutable parameter state, graph-preparation pitch calculation, existing audio ownership |
| V | Three-voice spread contract, unchanged CV semantics, RP-001/RP-003 detune variations |
| VI | Measured center/side pitches, 10 ms phase-continuous edits, normalized finite output |
| VII | Three voices/oscillator, inactive side DSP skipped, unchanged quotas, load measurement |
| VIII | Exact legacy save migration, repeatable roundtrips, v7 peer refusal and backup guidance |
| IX | Existing pinned toolchain/dependencies, packaged artifact checks |
| X | Localized slider/numeric control, units/range, native screenshot and audition procedure |

Test new defaults/bounds, old record migration, persistence, snapshot refusal, server edits,
manual and keyboard/MIDI pitch, disconnected CV, and live graph transitions. Run `make verify`
and a disposable native detune UI probe. Listening and two-client timing stay PENDING until
observed; retain every earlier release gate. Record explicit self-review in the ledger.

SC-005: four patches of 16 modules/24 cables/two speakers, exact spec topology.
Each patch uses a 4x4 grid at two-block spacing; origins (0,0), (8,0), (0,8), (8,8).
Pitches 110/220/330/440 Hz, level 50%, cutoff 5,000 Hz, resonance 25%, amount 50%,
default ADSR, volume 10%. Hold all gates 2 s/release 2 s for 30 minutes.
Baseline: M1-class Apple Silicon, >=16 GB RAM, macOS 26.6.2, Java 21, 1080p,
render 12 chunks, simulation 8, shaders/VSync off. Record exact machine/device first;
compare identical inactive/active fixtures. Measure 100 actions for SC-003/004,
20 restart/lifecycle cycles and five first-time users. MU-009 plus DC <-60 dBFS,
alias residual <-35 dB against oversampled reference, finite extremes, <0.02
constant-fixture transition step, <1 ms arithmetic drift/hour are required.


## Keyboard extension

Add the fifth registered module through existing registration and asset generation. Pure Java
KeyboardInput handles source priority/hit geometry; KeyboardPerformance owns the exclusive
transient session lease, KeyboardSession validates input order and sample scheduling, and
KeyboardState carries concert pitch/gate in bounded immutable snapshots. Octave is the only
persistent keyboard parameter. Explicit AUDIO/CONTROL/PITCH/GATE roles enforce cabling.

KeyboardScreen uses the existing panel style and host widgets, a twelve-key piano, mouse
and A W S E D F T G Y H U J input, and octave buttons/arrows. Server-generated session IDs
bind note packets to inspection; note releases stay FIFO, emergency cleanup invalidates the
session, and overflow releases performance. Input spacing is retained when draining a tick;
accepted samples and subsequent broadcasts never rewind past pending performance events.

Server envelope checkpoints incorporate external gate and retriggering. RenderGraph resolves
keyboard pitch separately from envelope controls, retains phase, and smooths frequency over
480 samples. No audio-thread allocations or additional dependencies are introduced.

Keep existing budgets and qualification gates. Run `make verify`, then the disposable
`make smoke GRADLE_ARGS=-PqaKeyboard TICKS=500` fixture for real GUI screenshots, ordered
note/transpose/release assertions and native audio counters. This automated probe is not a
listening pass or two-client timing qualification. Record results in validation/ledger.md.


## MIDI input extension

Implement MI-001–006 with the existing Java 21 `javax.sound.midi` API and no dependency changes.
The client owns local device preferences, a bounded asynchronous adapter, and combined GUI/MIDI
priority. The logical server accepts relative GUI or absolute MIDI notes through its existing
session/revision/permission checks. Network/snapshot v7 carries expanded pitch to 0–127; world v1 remains
unchanged. Native input providers are initially qualified on macOS only when actual evidence
exists. Detailed ownership, failure and timing contracts are in `docs/architecture.md`.

| Principle | MIDI design and evidence |
|---|---|
| I world safety | No world/device state crosses workers; client-only provider, packaged server loading |
| II boundaries | 52-byte packet, explicit mode/range, unchanged authorization; malformed/old-session tests |
| III TDD | Observed model, adapter, snapshot, packet and native failures before implementation; ledger |
| IV ownership | Injectable backend/serial worker/clock; game-thread priority and worker-owned handles |
| V composition | Existing CV/gate ports, fixed gate, monophonic sustain/fallback; RP-003 extension |
| VI timing | Existing server receive/sample ordering and smoothing; SC-003/004 retained, no hardware timing claim |
| VII bounds | One worker, four task slots, one input/helper, 64 ports, two bounded 256-event queues, 64/tick, 2,061 holds; timeout/overflow tests |
| VIII persistence | Only local device/channel preferences; world v1 preserved, protocol/snapshot v7 refuses older peers |
| IX dependencies | Built-in Java Sound only; all dependency and host pins retained |
| X controls | Opt-in device/channel, Refresh, Release Notes, localized diagnostics, local mute, guide and screenshots |

Run `make verify` and `make smoke GRADLE_ARGS=-PqaKeyboard TICKS=500`; record artifact hashes,
red–green results and self-review. Native synthetic playback is distinct from physical MIDI,
listening and two-client timing. Preserve all earlier PENDING/BLOCKED gates and manual worlds.

MIDI reconnect remediation: use one disposable headless Java 21 helper per native input
connection to contain the observed macOS retained-reader/silent-reopen failure. The original
client worker owns discovery, process lifecycle and 5 ms event polling; helpers are never
concurrent and are reaped before replacement. Preserve generation invalidation, local
preferences, server scheduling and all musical/authority contracts. This maps constitution
I/IV/VII to process isolation and bounded cleanup, IX to the same JDK/no new dependencies,
and III/VI/X to reconnect regressions, unchanged timing acceptance and recovery guidance.
Physical timing qualification remains separate from helper lifecycle evidence.


## Euclidean sequencer extension

Implement EU-001–008 using immutable prepared channel settings and a shared pure-Java
sample-domain control DAG. Extend the existing module/parameter/port registration and v1
named save records; network/snapshot v7 carries sequence checkpoints and envelope baseline
gates. Server components advance autonomous clocks independently of audible activation,
while clients render exact edges between ticks. Control source identity includes output port.
Use the existing ownership, quota and 40 ms scheduling contracts; apply the 1,024-per-patch /
4,096-per-tick transition limits with deterministic suspension on overload.

Keep client-only editor, visual projection and face overlay separate from audio ownership.
Use original generated artwork, cached ring geometry and 32-block visibility. Exercise
red–green domain/timing/codec/audio/host tests, `make verify`, and the disposable
`make smoke GRADLE_ARGS=-PqaSequencer TICKS=650` native fixture. Measure the existing DSP
budgets with both four-channel rhythms and chained clocks; retain all prior native,
multiplayer, listening, security and performance qualification gates. Record self-review.


## Oscillator note selection

Extend ModuleScreen with note/octave dropdowns, a preview, and explicit Set Note beneath
Frequency. OscillatorNote owns the transient draft and matches frequency/revision acknowledgements;
NoteDropdown owns bounded 12/9-option menus and keyboard/narration behavior. Use A4 = 440 Hz
and scientific octaves 0–8, disable C0–D#/Eb0 below 20 Hz, and submit the full-precision Hz
through the existing authoritative parameter path. Preserve arbitrary saved tuning, drafts
across updates/resizing, and connected pitch precedence. No schema/protocol/DSP/dependency changes.

Validate conversion/draft behavior with JUnit, exact parameter packet/NBT/server authorization
with packaged GameTests, and actual mouse/keyboard/compact layout with the disposable
`make smoke GRADLE_ARGS=-PqaDetune TICKS=600` fixture. Run `make verify`, inspect captured
screenshots and record self-review in the ledger. Earlier listening, multiplayer, performance
and release gates remain applicable; no commit, push or publication is implied.


## Square oscillator extension

Append SQUARE_OSCILLATOR and PULSE_WIDTH (5–95%, default 50); explicitly classify integer
parameters. Shared oscillator pitch resolution and note UI cover both kinds. Keep mono
Audio Out/Pitch In, Frequency/Level defaults, world schema 1, pins and all existing budgets.
Network/snapshot v7 refuses older peers; exact old saw migration remains unchanged.

PulseOscillator owns a single phase, 95-tap history, and 480-sample frequency/width ramps.
Use four-sample PolyBLEP edges at 192 kHz and an 18 kHz L1-normalized decimator; compensate
mean `2*duty-1` and divide by `2*max(duty,1-duty)` before decimation. Transfer phase/history
at graph adoption; Level uses the existing smoother. No audio-thread allocations or dependencies.
The 18 kHz decimator corner and wider edge kernel address aliasing measured at narrow 8 kHz pulses.

Extend deterministic original assets and eight-set package checks. Use red–green domain/DSP
regressions, independent oversampled alias/DC measurements, square/mixed load fixtures,
packaged parameter/lifecycle tests, `make verify`, and `make smoke GRADLE_ARGS=-PqaSquare TICKS=600`.
Record self-review and evidence; listening, multiplayer, hardware and release gates stay explicit.

## LFO and parameter CV extension (2026-11-05)

Implement LF-001–006 using immutable LfoSettings, analytic Lfo checkpoints, and the shared
ControlTimeline. Persist zero-default signed depths and append identifiers; world v1 migrates
only exact legacy filter/envelope records. Network/snapshot v7 requires matching peers.
ControlTimeline owns stage-entry CV, 480-sample sustain frames and keyboard retriggers;
RenderGraph owns sample-rate filter/gain modulation and inherited 480-sample smoothers.
No new dependencies or resources outside existing quotas; LFO checkpoints stay within 32 KiB.

| Constitution principle | Design and evidence |
|---|---|
| I world safety | Logical-server edits, no chunk loading, disposable tests; preserve manual worlds |
| II boundaries | Finite/range/identity/cable checkpoint checks, exact migration, stale/foreign edit tests |
| III TDD | Observed failing domain, DSP, modulation, codec, rendered-audio and panel-close regressions |
| IV ownership | Pure Java LFO/control timeline, immutable snapshots, isolated client projection |
| V composition | Typed CV, signed depth, exact mappings, stage-entry semantics, RP-006 and DAG limits |
| VI timing/audio | Analytic phase, sample boundaries, recovery equivalence, 10 ms transitions |
| VII bounds | Existing transitions/module/traffic caps, finite overload suspension, 64-module benchmark |
| VIII persistence | World v1 exact legacy migration; protocol 7; phase is transient; backup guidance |
| IX reproducibility | Existing pinned dependencies/toolchain, regenerated original art, packaged checks |
| X player control | Localized selectors, scrollable fields, fixed local-audio/gate controls, native screenshots |

Validate with `make verify GAMETEST_DIR=run/make/lfo-gametest-final` and fresh-instance
`make smoke GRADLE_ARGS=-PqaLfo TICKS=600`, plus
`make smoke-load GRADLE_ARGS=-PqaLfo TICKS=1200`. The maximum fixture has four patches of
16 modules/24 cables/two speakers: sequencer, three LFOs, detuned saw, seven filters,
two envelopes, two speakers. Tempo 300; LFO divisions 1/16; waveforms triangle/rising/falling
saw; filter/ADSR depths 50%; ordinary remaining defaults. Three clock cables, nine audio,
two trigger, two gain, four ADSR CV and four filter CV cables. Measure <1 ms audio p95 and
<2 ms mod-server p95 on the recorded machine, retain underruns/tail failures explicitly.
A short native run does not replace the 30-minute/two-client/listening qualification.
