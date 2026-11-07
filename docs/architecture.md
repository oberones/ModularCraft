# Architecture and ownership

Current saves use schema 2 and network/snapshots use version 9. The
[Amp / Speaker contract](#amp--speaker-split-schema-2-protocol-9) below supersedes
historical combined-output and restart-disabled details in earlier extension notes.

`domain` defines immutable module/control/port snapshots, deterministic graph validation,
transactional global quotas, and explicit versioned binary codecs. It imports no Minecraft
classes. `dsp` owns sample-domain mutable state, and `transport` owns monotonic arithmetic,
clock estimation, admission windows, and bounded queues. Host adapters live in `game`,
`network`, and the physically isolated `client` package.

The server owns accepted module and cable state on its game thread. Network threads can
only place connection-derived requests in `EditInbox`: 64 ordinary queued edits per
player, 256 globally, with coalesced parameter updates. Cleanup has a separate bounded
priority lane; releasing the envelope manual gate cancels an older queued press.
Keyboard note-off stays FIFO to preserve taps; only emergency keyboard cleanup invalidates
the session ahead of pending notes. Server handlers validate
identity, loaded state, owner/operator access, same dimension, six-block interaction,
host protection, and expected revision. Successful edits have one sequence order.

The overworld SavedData registry covers all dimensions and unloaded modules. Module block
entities retain their semantic state. Reconciliation uses UUID, owner, type, position,
and revision rather than reconnecting a replacement at an old position. Unsupported
world/record data is retained verbatim. Invalid connected records remain quarantined.
All save writes use Minecraft/NeoForge's persistence system. Record lengths, counts,
finite values, identifiers, graph rules, and aggregate world storage are validated.

Accepted changes send patch snapshots, not PCM. A snapshot contains stable identities,
revisions, a global sequence, a 48 kHz transport sample, typed cables, and analytic ADSR
checkpoints. Complete rosters remove stale/split/merged components. Periodic snapshots
also cover join, range entry, and recovery. Clock exchange is once per second; ordinary
shared changes use a 1,920-sample (40 ms) lead. Late snapshots evaluate their current
stage analytically. No missed-note backlog is rendered.

The client game thread owns listener preferences, view models, immutable snapshots, and
one preparation executor (one worker, eight pending jobs). It transfers compiled graphs
through an eight-slot single-producer/single-consumer handoff. The audio owner has a
1,024-event scheduler ordered by sample then sequence, splitting a processing block at
an event boundary. Sources are evaluated once per sample and shared across destinations.
Surviving oscillator phase, oscillator decimator history, and filter state transfer at
block boundaries. Topology changes crossfade over 480 samples; ordinary controls ramp
for 480 samples. Envelope curves retain their exact analytic linear stages.

Audio runs at 48 kHz in 128-frame blocks. The saw is PolyBLEP with four internal samples
and a precomputed 95-tap Blackman decimator. The initial 48 kHz-only implementation failed
the alias gate; the revised implementation passes the documented measurement. L1
normalization proves bounded oscillator peaks. The filter is a topology-preserving
state-variable low-pass with Q linear from 0.707 to 10 across resonance 0–95%. Each output
channel has a 5 Hz DC blocker and a finite full-scale clamp after spatial mixing.

The audio adapter borrows Minecraft's existing OpenAL device through its library and
sound executor. It owns one relative stereo channel and twelve reusable 128-frame
buffers: exactly 32 ms queued PCM. A timer wakes every 2 ms and permits at most one
pending pump. DSP and native calls execute only on the sound executor; processing does
not allocate application buffers, access worlds, log, perform I/O, or acquire locks.
Master volume is applied once by Minecraft's OpenAL listener; Records and local volume
are applied in the module mix. Speaker distance gain and orientation-relative equal-power
panning are calculated before final protection.

Client-only mixins expose three private host properties and observe sound teardown and
integrated-server pause. Vanilla's standard `AudioStream` path queues one-second blocks,
so it cannot meet this plan's buffer budget unchanged. The access surface is pinned to
1.21.1/NeoForge 21.1.218 and is covered by native loading/streaming probes, with device
switch and external audio-mod compatibility still separate acceptance gates.

Resource reload, disconnect, dimension change, mute, pause, device loss, and stop paths
close the native channel and buffers, cancel the timer, retire graph preparation, and
recover from current snapshots. Server patch unload disables all affected speakers and
clears transient performance. No audio classes are referenced by the common entrypoint.

Traffic admission caps snapshots at 32 KiB, edits at 4 KiB, and the registry at 2 MiB.
Listener/server traffic budgets are 128 KiB/s and 2 MiB/s. Overflow drops bounded work
and requests/resumes a current-state synchronization. Game-thread edit draining has a
1.5 ms admission slice; the **2 ms overall work target still needs load qualification**
because initial encoding/class-loading work exceeded it in native probes. Numeric bounds
are not a hard real-time guarantee from Java, the OS, or the audio device.


## Keyboard authority and pitch

Keyboard sessions are server-issued per inspected panel, with strictly increasing input
sequences. A performance lease belongs to one session and renews every 200 ms, expiring
at 750 ms. Accepted note changes retain server receive spacing even within one tick,
with a minimum 48-sample separation and 1,920-sample lead. Cleanup bypasses edit tokens;
queue overload releases and invalidates the affected session. Subsequent snapshots cannot
rewind pending performance timestamps. Renewal never starts/retriggers a note.

Pitch CV and gate have separate port roles. Snapshot v8 carries keyboard MIDI note numbers
0–127 and gate booleans; CV = (note−60)/12 V and Hz = 440×2^((note−69)/12). Server-side
external gates feed analytic envelope checkpoints. RenderGraph resolves pitch cables into
smoothed frequency targets, preserves oscillator phase, and leaves envelope control routing
unchanged. World v1 persists octave only, using existing named records; network v8 rejects
old peers. Snapshot recovery restores current pitch/gate, not historical note events.


## MIDI input ownership

The client-only MIDI adapter uses one daemon worker with four pending-operation slots,
no replacement workers, one open input, and at most 64 discovered device entries. Discovery
borrows unopened Java Sound provider objects. A selected connection runs in one headless
Java 21 helper process launched with the same runtime as Minecraft. Only the mod's fixed
standalone classes are extracted into one private temporary directory per client, removed
on normal JVM exit. No dependencies, native binaries, network services or shell commands
are added. The helper has a 32 MiB maximum Java heap and 64 MiB metaspace ceiling; those
are not a total process RSS limit.

Each panel/focus/selection generation owns a fresh helper and therefore a fresh Java Sound
reader/cache. This contains the macOS provider's retained-reader/silent-reopen defect.
The helper explicitly closes receiver/transmitter/device on a stop command. The worker
allows 1.25 seconds for graceful exit, then forcibly terminates the helper and waits up to
250 ms. It retains ownership and refuses another open if the process is still alive. A
parent-liveness watchdog exits the helper if Minecraft disappears, including during a
blocked native open/close. No process is automatically respawned after an input failure.

Device discovery/open/health/close and bounded pipe polling all run on the same worker;
no game/audio thread performs I/O. Idle polling every 5 ms avoids an extra client-tick
transport delay. The helper's bounded 256-event ingress queue, OS pipe, and client's
256-event queue are separate stages; each poll transfers at most 64 four-byte messages,
and each client tick consumes at most 64 events. Unsupported messages and unselected channels are rejected before
queueing; malformed frames or helper exit stop input, and overflow at either queue
invalidates performance. Native/device timestamps are not transferred. The helper's
one-byte readiness handshake is followed by fixed-width note/controller frames and an
explicit overflow marker; no unbounded strings or deserialization cross the pipe.

A two-second operation deadline revokes callbacks and reports failure. Late results close
their resources. Refresh/focus/selection changes retain a timeout while an operation is
outstanding. Parent-side discovery remains bounded to its original worker if a provider
stalls there; the helper containment specifically addresses native connection lifetimes.

Only the game thread owns input priority, channel selection and UI state. Callback messages
cross a 256-event queue; at most 64 are drained each tick. Generation-tagged callbacks,
queue entries, and overflow indications cannot revive a previous focus/session/device.
Unsupported MIDI messages are filtered before queueing. Overflow clears input, sends emergency
session cleanup, and requires reopening. Health checks once per second rescan availability;
missing/unavailable/ambiguous ports require explicit retry. Provider disconnect detection is
limited by the platform's Java Sound reporting; no silence timeout cuts legitimate long notes.

The pure input model contains at most 13 GUI and 2,048 MIDI sources, with 16 pedal states.
MIDI notes use channel+pitch identity; repeated onsets replace/retrigger that source, and
one Note Off releases it. Sustain preserves priority order until release; GUI holds remain
independent. A single ordered winner drives the existing authoritative performance lease.
Keyboard packets are exactly 52 bytes: the existing 51 bytes plus pitch mode 0 (relative)
or 1 (absolute). Relative notes are 0–11, absolute notes 0–127, release is -1 with mode 0.
Renewal cannot start a note. Server octave changes affect only a relative winner. All input
keeps server-receive scheduling (minimum 48 samples between accepted events, 1,920-sample
lead), existing revision/permission/range checks, and the 20/player and 200/world edit budgets.
MIDI device timestamps are ignored; sample-accurate hardware-input timing is not claimed.

Network/snapshot v8 deliberately refuses older peers/snapshots; world v1 and saved octave
remain unchanged. The SHA-256 of the device name/vendor/description/version is a local
selection descriptor, not a hardware identity. Only unique matches restore automatically;
explicit selection can distinguish duplicate ports for the current focused session. Device
and channel preferences are stored with the existing listener settings, never in world data.


## Oscillator unison spread

Detune is a persistent, server-validated 0..100-cent-per-side control. The graph prepares
three frequency targets: center, center / 2^(cents/1200), center * 2^(cents/1200), after
resolving manual versus keyboard/MIDI pitch. `UnisonSaw` owns three independent PolyBLEP
phases/decimators, three frequency ramps and a blend ramp. Exponentiation and allocation
occur only during graph preparation. Audio adoption transfers state without mutating the
old graph; all targets ramp over 480 samples. Center pitch is unchanged by Detune.

Zero spread selects the original center voice; nonzero selects an equal three-voice mix.
The blend is a convex combination, preserving the existing Level ceiling. Side processing
is skipped at settled zero; enabling spread then inherits center phase/history. Each
oscillator has at most three voices, with no new graph ports, stereo routing or queues.
The existing 64-module fixed-load benchmark also measures all four oscillators at maximum
spread. Native performance/listening/two-client gates remain distinct.

World schema v1 and block schema v1 accept only the exact legacy Frequency/Level key set
as a migration to Detune 0. Other incomplete/extra/non-finite records retain rejection or
opaque quarantine. The complete named control set is written on next save, and repeated
loads are stable. Snapshot/network v8 refuses older formats and does not apply this stored
record migration. Existing parameter ordinals are retained by appending DETUNE.


## Euclidean control timeline

`SequencerSettings` prepares four immutable validated channel configurations; `Sequencer`
owns a single 48 kHz runtime with integer clock remainders, next-step counters, input edge
levels and five pulse endings. The canonical mask is `(step * hits) % steps < hits`, rotated
clockwise, with zero hits silent. Musical edits are pending until a clock; mute is immediate.
`ControlTimeline` evaluates clock/reset/gate wiring in topological order, with reset before
clock at the same sample. Digital routing includes the source port, so all four outputs
remain independent. The server and audio renderer use the same bounded algorithm.

Logical-server timelines operate even without enabled speakers. Envelope checkpoints exist
only for audible components; manual/redstone baseline gates are carried separately from
external gates. Keyboard retriggers retain their prior priority. Runtime changes use the
existing ordered 40 ms lead, validation and revisions. Internal pulses do not send packets:
normal periodic snapshots carry current sequencer and envelope checkpoints, and listeners
advance them on the sample timeline. Late recovery advances state without replaying audio.
The renderer prepares all routing and mutable runtimes outside processing; it does not
allocate buffers, perform I/O, acquire locks, or touch world objects while advancing.

World/block schema v1 stores 17 named controls and cables only. Snapshot/network v8 adds
bounded per-module sequencer checkpoints and manual/redstone envelope baselines; matching
peers are mandatory. The parameter decoder now uses the selected module's bounded control
count. Legacy oscillator migration and byte-preserving quarantine remain in place.

At most 16 sequencers/64 channels fit one patch and 256/1,024 fit the world; all existing
module, cable, speaker, storage, bandwidth, job and audio-queue quotas remain unchanged.
Clock input accepts at most 50 Hz, with 480-sample output pulses and no pulse overlap.
Timeline work is capped at 1,024 output transitions per component and 4,096 per server tick;
overload stops that component and disables its speakers, requiring explicit recovery.
Client recovery is likewise bounded and silences overloaded graph components.

`SequencerVisuals` owns a separate client-thread projection, independent of local mute.
The editor and world renderer share cached ring coordinates. World overlays use the accepted
scene, check loaded chunks, camera-facing side and frustum, and stop at 32 blocks; no chunk
scans/tickets or client classes are introduced into common loading. Unload, disconnect and
world changes discard projections; integrated pause freezes the visual sample coordinate.


## Square oscillator

SQUARE_OSCILLATOR appends a module kind and PULSE_WIDTH appends a fractional 5–95% control;
integer-only parameters are classified explicitly. Shared pitch preparation and note UI use
both oscillator kinds; the server reports CV pitch precedence without mentioning saw detune.
World v1 keeps named records and exact legacy saw migration. Network/snapshot v8 refuses
older peers; square records require exactly Frequency, Pulse Width and Level.

PulseOscillator has one phase/history, frequency/width smoothers, four internal samples per
48 kHz frame, and the same 95-tap decimator design as the saw, with an 18 kHz corner. Four-sample PolyBLEP
edges use the integrated cubic B-spline step: a nonnegative smoothing kernel preserves
bounds even when narrow-pulse edges overlap. Subtract the ideal duty-dependent mean and
normalize by twice the longer duty fraction before decimation. This bounds peaks without
RMS compensation; narrow pulses are quieter. Frequency/width/level ramp over 480 frames.
Graph adoption copies state only between matching IDs and module kinds. Preparation stays
off the audio owner; processing remains bounded and allocation-free. Saw DSP is unchanged.

## LFO and continuous parameter CV

`LfoSettings` is immutable; `Lfo` owns analytic phase/rate anchors and clock acquisition.
Frequency edits integrate a 480-sample linear rate ramp; waveform/polarity edits crossfade
from the current output over 480 samples. Free-running phase uses the sample timeline,
not accumulated tick/frame deltas. Sync uses accepted rising edges (minimum 960 samples),
pulse-count phase anchors, measured intervals, and a two-interval timeout. No timer queue
or per-sample server work is introduced. Component merge/split rewiring retains the prior
source identity from every old timeline, avoiding accidental clock reacquisition.

ControlTimeline now includes ADSR stage boundaries and transport-aligned 480-sample control
frames for connected nonzero sustain CV. At each event, sources are resolved in DAG order,
effective time/sustain values are prepared, then stage/gate transitions occur. Keyboard
retriggers use this same path. Time controls are captured at stage entry, while held sustain
uses 480-sample linear ramps. Envelope sources can modulate other envelopes without recursion;
cycles are rejected before preparation. Events consume the existing 1,024-per-component /
4,096-per-server-tick budget and suspend on overflow, without catch-up backlog.

Audio evaluates LFO controls at 48 kHz. Filter LFO cutoff and resonance CV, and LFO gain CV,
use 480-sample target ramps; gain is clamped to 0…1. Existing envelope cutoff/gain paths keep
their analytic behavior, so short sequencer notes and legacy envelope sweeps are preserved.
Surviving CV smoother state transfers with filter/oscillator state during graph adoption.
No audio processing allocates, performs I/O, or reads mutable Minecraft state.

Snapshot protocol 8 adds a bounded UUID→LFO checkpoint map containing phase/rate anchors,
clock input level/interval/pulse count, and waveform transition state. Existing envelope
checkpoints also carry effective next-stage values and active sustain ramps. Decode validates
numbers, identities, cable/clock consistency, sizes and duplicate entries before adoption.
World schema 1 persists only named settings/cables; exact old filter and ADSR parameter sets
migrate by adding zero depths. Partial malformed sets remain refused. New enum identifiers
are appended. No dependency, loader, graph, packet, or storage quota changes are required.

UI scrolling is confined to the parameter area; gate, local audio and Done stay fixed.
Selectors use localized named values, never raw enum numbers. A delayed CLOSE for a previous
module cannot release the currently inspected module's session. Server ownership, range,
revision checks, and cleanup priority otherwise follow the existing paths.

## Audio Mixer

`MixerSettings` defines 14 persisted controls and four mono inputs. `Mixer` owns five
480-sample effective gain smoothers; mute/solo decisions are prepared outside processing.
`RenderGraph` resolves fixed input indices, evaluates each source once, sums inputs and
adopts gains/meter envelopes at block boundaries. Audio remains mono until speakers.
The AudioEngine exposes meters only from its current graph, never its fading predecessor.

Each mixer owns five preallocated peak/decay/overload values and a sequence-stamped meter
publication. The sole sound-executor writer publishes once per processing chunk; the UI
copies a consistent publication at 20 Hz or skips contention. This adds no network traffic,
locks or steady-state audio allocation. 300 ms linear decay and one-second overload hold
run in sample time; publications stale after 500 ms of wall time become unavailable.

`MixerEdits` owns an at-most-14-entry coalescing queue, one outstanding edit and 100 ms
minimum send spacing. Only accepted server revisions drive audio; UI drafts stay separate.
Rejected edits cancel pending changes; a two-second timeout requires reopening/retrying.
Mixer parameter requests receive authoritative views even after the local-settings detour
has closed inspection. Screen close cancels unsent changes. All validation remains on the
existing logical-server path. World schema 1 is unchanged; protocol 8 refuses older peers.

## Amp / Speaker split (schema 2, protocol 9)

This contract supersedes the combined output and restart-disabled behavior above.
`AMP` owns Audio In, Gain In, Gate In, and Audio Out. It reuses the sample-domain
Envelope and ControlTimeline, including authoritative manual/redstone gates, keyboard
retriggering, checkpoints, and cleanup. Amp has Level (default 100%), four ADSR controls,
and Drone (default off); it has no envelope output or ADSR CV inputs. Gain precedence is
connected external control (clamped 0–1), then Drone, then internal envelope. Envelopes
continue advancing while overridden. Normal ADSR edges retain their sample timing;
source/Drone changes crossfade gain over 480 samples and Level uses the existing smoother.
Only `SPEAKER` enters the spatial stereo mix and output protection. Its only port is Audio In.

Speaker Volume defaults to 10%. `SPEAKER_ENABLED` stores player intent (default 1),
`SPEAKER_SUSPENDED` stores an overload latch (default 0), and `ModuleState.enabled` is
transient admitted playback. The UI hides these implementation parameters and uses the
revision-protected ENABLE action; generic parameter packets cannot change them. Admission
preserves active components, then considers waiting components by lowest UUID, and speaker
UUID within each component. Entire components must be loaded. Capacity waits never clear
intent or displace active playback. Chunk unload clears transient gates and activation;
loading the whole patch resumes eligible speakers. Overload requires explicit re-enable,
including across restart. Sequencer running state remains transient and must be restarted.

The existing `modularcraft:speaker` registry entry now represents Amp. The new Speaker
uses `modularcraft:audio_speaker`; models, inventory names, loot, and block entities use
this explicit registry mapping instead of assuming enum names equal registry names.
World and block schema 2 distinguish new Speaker records from schema-1 combined speakers.
Exact old SPEAKER/VOLUME records migrate to AMP/AMP_LEVEL plus default ADSR and Drone on.
Migration preserves all identity/position/owner/revision/cable data; it does not place output
blocks. Failed/unknown records remain opaque: schema-2 world records use the high bit of
the unsigned 16-bit length prefix to mark quarantined data, which is never decoded as a
live module or cable. A schema-1 rejection cannot become a live schema-2 module after
saving. Original bytes (including identity) are retained without nested wrappers or extra
bytes. The 4,096-byte record, 1,024-record and 2 MiB world limits are unchanged.
Block and registry reconciliation share the same decoder. Standalone schema-2 module bytes have an explicit magic/version prefix.
Network and snapshots use version 9 and refuse older peers. Backups are required for downgrade.

All existing quotas remain: 16 modules / 24 cables / two Speakers per component; four
active patches / eight active Speakers; 32 KiB snapshots, 2 MiB world records; unchanged
transition and traffic budgets. `qaAmp` supplies a 15-module/17-cable native reference and
four 16-module/18-cable load patches (64 modules/72 cables/eight Speakers). Existing
96-cable stress fixtures remain and now route former speaker gain cables to filter
resonance inputs. Automated performance and native listening evidence remain distinct.
