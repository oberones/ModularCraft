# Architecture and ownership

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

Pitch CV and gate have separate port roles. Snapshot v3 carries keyboard MIDI note numbers
0–127 and gate booleans; CV = (note−60)/12 V and Hz = 440×2^((note−69)/12). Server-side
external gates feed analytic envelope checkpoints. RenderGraph resolves pitch cables into
smoothed frequency targets, preserves oscillator phase, and leaves envelope control routing
unchanged. World v1 persists octave only, using existing named records; network v3 rejects
old peers. Snapshot recovery restores current pitch/gate, not historical note events.


## MIDI input ownership

The client-only Java Sound adapter uses one daemon worker with four pending-operation slots,
no replacement workers, one open input, and at most 64 discovered device entries. Provider
objects are queried without opening; selected device/transmitter/receiver handles are closed
on cancellation, replacement and shutdown. Discovery/open/health/close never run on the game
or audio thread. A two-second operation deadline revokes callbacks and reports failure;
late results close their resources. A handle whose close fails remains owned and blocks
replacement opens until cleanup succeeds. Native calls cannot be forcibly interrupted safely, so
an unresponsive provider remains bounded to that worker until it returns or the process exits.

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

Network/snapshot v3 deliberately refuses older peers/snapshots; world v1 and saved octave
remain unchanged. The SHA-256 of the device name/vendor/description/version is a local
selection descriptor, not a hardware identity. Only unique matches restore automatically;
explicit selection can distinguish duplicate ports for the current focused session. Device
and channel preferences are stored with the existing listener settings, never in world data.
