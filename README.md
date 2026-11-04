# ModularCraft

Build a shared modular synthesizer from five Minecraft blocks: **Saw Oscillator**,
**Low-Pass Filter**, **Envelope Generator**, **Amplifier / Speaker**, and **Keyboard**. Connect their
ports with the Patching Tool; nearby players synthesize the accepted performance locally.
No prerecorded loops, accounts, internet audio, external services, or extra audio software
are required. Creative access is sufficient; there are no survival recipes in this MVP.

This is an **uncommitted development candidate**, not an accepted public release. The
[evidence ledger](validation/ledger.md) distinguishes automated checks, native engine
measurements, user-reported listening, and outstanding release acceptance.

## Install

Use **Minecraft 1.21.1**, **NeoForge 21.1.218**, and **Java 21**. Put
`build/libs/modularcraft-0.1.0.jar` in the client and server `mods` directories. Every
participant needs the same protocol-compatible mod. The initial playback target is macOS;
Windows/Linux playback and other loaders are unqualified. A headless server does not
need an audio device. Do not install the `-sources.jar` as a mod.

Use Minecraft's **Master** and **Jukebox/Note Blocks (Records)** sliders. The mod also has
independent persistent local volume and mute. Default keys are **M** for mute and **K**
for audio settings; rebind them under Options → Controls → Key Binds → ModularCraft.
Audio settings are also available from the title screen, pause menu, and module panels.

## First instrument: RP-001

1. Open the **ModularCraft Instruments** Creative tab. Take an oscillator, filter, envelope, speaker, and the
   **Patching Tool**. Place the modules two blocks apart, with their labeled fronts facing
   you. All front ports are on the lower strip.
2. Hold the tool and right-click **Oscillator AO → Audio Out**, then **Filter AI ← Audio In**.
3. Select **Filter AO → Audio Out**, then **Speaker AI ← Audio In**.
4. Select **Envelope CO → Envelope Out**, then **Speaker GI ← Gain Control In**.
5. Put the tool away. Right-click the speaker and choose **Enable speaker**. Its default
   volume is 10%. The envelope is initially released, so silence here is expected.
6. Right-click the envelope. **Hold Gate** with the mouse, or hold **Space** when a numeric
   field is not focused. Hold for two seconds, then release. The 220 Hz note attacks,
   settles to sustain, then fades out. Closing the screen releases its manual gate.
7. Press **M** to stop local sound. Press it again to hear the current shared performance.
   Muting yourself never stops the instrument for other players.

Panels keep the world running. Drag a slider and release to submit it, or type an exact
number and press **Apply**. The label above the slider shows the **server-accepted** value;
a stale or invalid edit leaves that value intact and displays recovery guidance.

Default RP-001 settings, including every parameter:

| Module | Settings |
|---|---|
| Oscillator | Frequency 220 Hz; Level 50% |
| Filter | Cutoff 5,000 Hz; Resonance 0%; Envelope Amount 0% |
| Envelope | Attack 0.010 s; Decay 0.200 s; Sustain 70%; Release 0.300 s |
| Speaker | Volume 10%; Enabled on after step 5 |

For a continuous drone, **Shift-right-click Speaker Gain In** with the tool to remove
that cable. The HUD warns that disconnected gain means full control gain. With audio
still connected, the speaker now sounds continuously. Change oscillator pitch to 440 Hz
for one octave up. Compare filter cutoff 8,000 Hz and 500 Hz, then resonance 0% and 75%.
Reconnect the gain cable to return to envelope-controlled notes.

## Brightness sweep: RP-002

Keep all three RP-001 cables and add **Envelope Out → Filter Cutoff Control In**.
Set every module as follows:

| Module | Settings |
|---|---|
| Oscillator | Frequency 220 Hz; Level 50% |
| Filter | Cutoff 500 Hz; Resonance 0%; Envelope Amount 50% |
| Envelope | Attack 0.500 s; Decay 0.500 s; Sustain 30%; Release 1.000 s |
| Speaker | Volume 10%; Enabled on |

Hold Gate for two seconds and release. Loudness and brightness rise together, settle
lower while held, then fade. The cutoff mapping is
`base + (20,000 − base) × envelope × amount`, where envelope and amount are fractions.
At full envelope these settings reach 10,250 Hz. Amount 0% restores the base cutoff.
Redstone power at an envelope block drives the same gate: **manual OR redstone OR Gate In**.
Releasing one source while the other is active does not end or retrigger the note.
Attack, decay, and release are linear. Time edits affect the next entered stage.

## Play the keyboard: RP-003

Keep RP-001's three cables and all its default settings. Add a **Keyboard**, with:

1. **Keyboard CV → Pitch CV Out** → **Oscillator PI ← Pitch CV In**.
2. **Keyboard GO → Gate Out** → **Envelope GT ← Gate In**.
3. Enable the speaker, remove redstone power from the envelope, then right-click the keyboard.

The panel shows twelve chromatic keys C–B, default octave 4. Click and hold or drag across
keys; dragging outside releases the mouse note. Alternatively, focus the piano and play
**A W S E D F T G Y H U J** (C C# D D# E F F# G G# A A# B). Tab navigates controls.
**Octave −/+** or left/right arrows while the piano is focused select octaves 1–7.
Octave changes transpose held mouse/computer notes immediately; MIDI pitches stay fixed. The last pressed held key wins; releasing
it returns to the previous held key. Each new active note retriggers attack from the
current envelope level. Releasing all inputs lowers Gate and lets the release tail finish
at the last pitch. Closing the panel or losing window focus releases held notes.

Press **H** in octave 4 to hear A4 = 440 Hz. CV uses 1 V/octave, with 0 V = C4;
connected CV determines exact equal-tempered oscillator pitch. Its saved Frequency setting
is restored when Pitch In is disconnected. The keyboard emits no audio or velocity.
Manual/redstone envelope gates remain OR-combined with Gate In; while either is held,
keyboard note changes change pitch without restarting the envelope.

Only one eligible player can hold a keyboard at a time. Another player must wait for release.
Inputs use the existing 20 changes/s/player budget (including five held-note renewals/s);
release always bypasses ordinary edit throttling. An overloaded session releases and asks
you to reopen the panel. Polyphony, velocity expression, pitch bend, and recording are not supported.

### Play from a MIDI controller

In the keyboard panel, use **MIDI** to cycle through input ports and Off. **Refresh** rescans
ports; the selected port and **Channel** (All or 1–16) are remembered in local preferences.
MIDI starts Off until you choose a device. Only a unique descriptor match reconnects
automatically; missing or identical devices require Refresh/reselection. The port's list
number helps distinguish identical names during manual selection. Hardware and virtual
input ports exposed by Java Sound are eligible; the internal synth and sequencer are excluded.

MIDI works only while this editable panel is open and Minecraft is focused. It plays exact
notes 0–127 (A4 = note 69 = 440 Hz), independently of the panel octave. The most recent
mouse, computer, or MIDI note wins. Releasing it returns to the previous held/sustained
note. Repeated MIDI Note On retriggers; velocity zero is Note Off. Velocity otherwise does
not change the gate level. The twelve displayed keys retain the panel octave; the Note
readout shows the accepted pitch even when a MIDI note lies outside that octave.

A sustain pedal (CC64, values 64–127 down) retains released MIDI notes on its channel;
it does not sustain computer or mouse input. CC123 releases the channel's physical holds
while respecting the pedal; CC121 resets the pedal; CC120 clears the channel's MIDI holds
and pedal. All release operations let connected envelopes follow their normal release tails.
**Release Notes** clears all local holds and pedals; use local mute for immediate local silence.

Closing the panel, changing worlds, losing focus/permission/range, or reloading resources
releases notes. Refocusing reconnects with no held notes; press again to play. Device/channel
changes clear only MIDI holds. Device loss is checked once per second; check the connection
and click Refresh to recover. A provider that does not report physical removal may require
Release Notes or closing the panel. No missed notes are replayed. Device operations time out
visibly after two seconds without blocking Minecraft. A stalled native provider may need a
game restart; Refresh does not spawn replacement workers. A timeout remains visible across
Refresh, focus changes and device/channel selections while that operation is still blocked.
MIDI connections now use one small Java helper process, which closes when the panel loses
eligibility and starts fresh on return. This avoids macOS Java Sound keeping a broken reader
across device reopen; it adds no installed dependencies and never keeps notes from a prior panel.

MIDI shares the existing traffic limits, including renewals, so dense controller/arpeggiator
streams can be rejected. Queue overflow stops the session and requires reopening the panel.
Only supported notes/pedal/reset messages consume the MIDI queue. All clients and the server
must use protocol **3**; existing world saves remain version **1**. Physical MIDI devices,
listening, and multiplayer timing still need the acceptance procedure below.

## Patching, permissions, and saved work

Audio outputs connect only to audio inputs. Envelope outputs connect only to cutoff or
gain control inputs. Keyboard CV Out connects only to oscillator Pitch CV In; Gate Out
connects only to envelope Gate In. An input has one source; an output can feed four destinations.
Shift-right-click a destination input to remove exactly that cable. Right-click air or
a non-port area with the tool to cancel selection. Cable arrowheads show direction.
Ports have labels and the tool HUD shows their full names; color is supplementary.

Only the owner or a server operator may edit or break a module through player interactions.
Stay within six blocks of each endpoint, in the same dimension; both endpoints must be
loaded. Cables connect same-owner modules only, span at most 16 blocks between centers,
and cannot form feedback loops. Host protection rules also apply. Breaking a module
removes its cables; a replacement at the same position has a new identity and defaults.

| Capacity | Limit |
|---|---|
| One connected patch | 16 modules, 24 cables, two speakers |
| One owner, including unloaded modules | 64 modules, 128 cables |
| Entire world, across dimensions | 256 modules, 512 cables |
| Active world playback | Four patches, eight speakers |
| Ordinary accepted edits | 20 per second per player, 200 per second globally |

Module UUIDs, owners, controls, and cable endpoints persist. **Speakers restart disabled**;
manual/keyboard gates and live envelope progress are not saved. Keyboard octave is saved;
its pitch resets to C in that octave on fresh activation. Enable speakers to start fresh
from the current redstone state. Unloading any part of a patch suspends the entire patch;
load all its chunks and explicitly enable its speakers again. Instruments never force
chunks to stay loaded. A true singleplayer pause freezes music; module panels do not pause.

Unknown or corrupt records are quarantined and preserved, including connected affected
components. Do not edit the save to guess missing values. Back up the entire world, use
a compatible mod version or restore a known-good backup, and retain the original data
for recovery. The global registry is `data/modularcraft.dat`; per-module data is also in
its owning block entity. World-save schema remains version 1; existing patches load unchanged. Network and
performance-snapshot protocol is version 2: update both server and clients together.

## Troubleshooting

- **No sound:** check local mute, local volume, Minecraft Master and Records, speaker
  enable, its Audio In cable, and the envelope gate. No audio input always means silence.
- **“Envelope released”:** hold Gate or apply redstone; disconnecting Gain In deliberately
  changes to a sustained drone. An unconnected filter control means zero modulation.
- **Patch suspended:** load every member's chunk and enable the speaker again. A missing
  or quarantined module keeps its component silent.
- **Too far away:** gain is full through two blocks, then linear to silence at 32 blocks.
  Another dimension is silent. Walls do not occlude sound.
- **Stale edit:** another accepted change advanced the revision. Review refreshed values
  and submit again. Fast slider movements are coalesced; excessive edits are throttled.
- **Device/reload recovery:** choose a working Minecraft sound device, then use local
  audio settings → Resynchronize audio. Recovery joins the current note instead of
  replaying missed events. Device changes never stop the server's composition.
- **Heavy resonance or many speakers:** final protection keeps output finite and at or
  below full scale. Long resonant filter chains can intentionally drive that protection.
- **Local preferences do not save:** check `config/modularcraft-listener.properties`
  permissions. World owners cannot alter these listener preferences.

## Build and inspect

```sh
make help                     # All build, check, launch, and maintenance targets
make client                   # Build and launch the actual JAR for interactive validation
make smoke                    # Fresh playable RP-001 fixture; exits after about one minute
make smoke-recovery TICKS=400  # Twenty audio recovery cycles in a fresh world
make verify                   # Full checks, packaged headless GameTests, and checksums
```

Requires Java 21, Python 3.9+, and GNU Make 3.81+ (including macOS's system Make).
Packaged interactive saves persist in `run/make/client`. Smoke targets create isolated
fresh worlds; existing saves are retained. See [development commands](docs/development.md)
for load probes, server launches, overrides, output locations, and cleanup behavior.
`make client-dev` launches development classes when that is what you want to test.

The Gradle wrapper is pinned to 8.13 with its distribution checksum. Java sources use
toolchain 21. Compiler warnings, google-java-format, Checkstyle, PMD, JUnit, and headless
NeoForge GameTests are wired into the build. `./gradlew formatJava` formats Java sources.
Dependency updates require reviewing the lock and integrity metadata against upstream;
do not regenerate hashes merely to suppress a verification failure.

Outputs: installable JAR, source JAR (including editable artwork), full rebuildable
`-project.zip` (including tests and build inputs), and `SHA256SUMS` under
`build/libs`. See [architecture](docs/architecture.md), [acceptance procedures](docs/acceptance.md),
[dependency/license inventory](THIRD_PARTY_NOTICES.md), and [art provenance](art/PROVENANCE.md).
The test-only native fixture is excluded from the distributable. Public publishing,
recording/export, recipes, sequencers, additional waveforms/modules, and other loaders
are deferred.
