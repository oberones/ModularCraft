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

Retain every SS-003 quota. Version 1 world saves; version 3 network/snapshots; edits 4 KiB, snapshots 32 KiB,
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
session/revision/permission checks. Network/snapshot v3 expands pitch to 0–127; world v1 remains
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
| VII bounds | One worker, four task slots, one input, 64 ports, 256 events, 64/tick, 2,061 holds; timeout/overflow tests |
| VIII persistence | Only local device/channel preferences; world v1 preserved, protocol/snapshot v3 refuses older peers |
| IX dependencies | Built-in Java Sound only; all dependency and host pins retained |
| X controls | Opt-in device/channel, Refresh, Release Notes, localized diagnostics, local mute, guide and screenshots |

Run `make verify` and `make smoke GRADLE_ARGS=-PqaKeyboard TICKS=500`; record artifact hashes,
red–green results and self-review. Native synthetic playback is distinct from physical MIDI,
listening and two-client timing. Preserve all earlier PENDING/BLOCKED gates and manual worlds.
