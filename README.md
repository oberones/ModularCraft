# ModularCraft

Build a shared modular synthesizer from eight Minecraft blocks: **Saw Oscillator**, **Square Oscillator**,
**Low-Pass Filter**, **Envelope Generator**, **Amplifier / Speaker**, **Keyboard**, **Euclidean Sequencer**, and **LFO**. Connect their
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
6. Right-click the envelope. **Hold Gate** with the mouse, or hold **Space** with the Hold Gate button focused. Hold for two seconds, then release. The 220 Hz note attacks,
   settles to sustain, then fades out. Closing the screen releases its manual gate.
7. Press **M** to stop local sound. Press it again to hear the current shared performance.
   Muting yourself never stops the instrument for other players.

Panels keep the world running. Drag a slider and release to submit it, or type an exact
number and press **Apply**. The label above the slider shows the **server-accepted** value;
a stale or invalid edit leaves that value intact and displays recovery guidance.

Default RP-001 settings, including every parameter:

| Module | Settings |
|---|---|
| Oscillator | Frequency 220 Hz; Detune 0 cents; Level 50% |
| Filter | Cutoff 5,000 Hz; Resonance 0%; Cutoff CV Amount 0%; Resonance CV depth 0% |
| Envelope | Attack 0.010 s; Decay 0.200 s; Sustain 70%; Release 0.300 s; all four CV depths 0% |
| Speaker | Volume 10%; Enabled on after step 5 |

For a continuous drone, **Shift-right-click Speaker Gain In** with the tool to remove
that cable. The HUD warns that disconnected gain means full control gain. With audio
still connected, the speaker now sounds continuously. Change oscillator pitch to 440 Hz
for one octave up. Compare filter cutoff 8,000 Hz and 500 Hz, then resonance 0% and 75%.
Reconnect the gain cable to return to envelope-controlled notes.

### Tune an oscillator by note

Under Frequency, choose a **Note** and **Octave**, check the Hz preview, then click
**Set Note**. For example, A3 is 220 Hz, A4 is 440 Hz, and middle C is C4
(approximately 261.626 Hz). Accidentals show both names, such as C#/Db.
The selectors cover octaves 0–8; C0 through D#/Eb0 are below the oscillator's
20 Hz minimum and cannot be applied. E0–B8 are supported.

Choosing a note does not change the sound until Set Note is accepted by the server.
The Frequency slider and numeric Apply remain available for arbitrary tuning; opening
the panel never snaps an existing frequency to a note. The selectors start at the
nearest supported note and preserve an unapplied selection through updates and resizing.
Only the resulting Hz value is saved. Connected **Pitch In** still controls the sounding
pitch; disconnect it to hear the saved manual frequency again.

Use Tab to focus a selector, Enter or Space to open it, Up/Down or Home/End to choose,
and Enter to confirm the selection. Escape closes a menu without choosing. Tab then
moves to the next control; activate Set Note to apply. Mouse selection is also supported.
For an RP-001 tuning variation, use the drone setup above and apply A3, A4, and C4,
then return to A3. Listening acceptance remains separate from automated UI checks.

### Unison detune

The Saw Oscillator panel includes **Detune (unison spread)**: **0–100 cents per side**,
with a slider and exact numeric entry (fractional cents are supported). At **0**, it plays
one saw. Above zero, it blends three saws: the center pitch, one below it, and one above
it, each at one third of the selected Level. The output remains mono. **100 cents** is
one semitone on each side; **10–25 cents** produces a narrower beating/chorus effect.
Pitch and blend changes take 10 ms. Returning to zero restores the center saw alone.

For an RP-001 detune variation, use all listed defaults, remove the speaker's gain cable
for a drone, and compare Detune **0**, **12.5**, **25**, and **100** cents. The center stays
at 220 Hz while the side voices spread symmetrically in musical pitch. At 100 cents they
are approximately 207.652 and 233.082 Hz. Reset Detune to 0 and reconnect the gain cable.
On RP-003, try the same settings while holding A4: the center stays at 440 Hz and the
side voices follow keyboard or MIDI notes. Detune is saved per oscillator.

## Brightness sweep: RP-002

Keep all three RP-001 cables and add **Envelope Out → Filter Cutoff Control In**.
Set every module as follows:

| Module | Settings |
|---|---|
| Oscillator | Frequency 220 Hz; Detune 0 cents; Level 50% |
| Filter | Cutoff 500 Hz; Resonance 0%; Cutoff CV Amount 50% |
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
connected CV determines the center pitch. Detune adds unison spread around that pitch.
The saved Frequency setting becomes the center again when Pitch In is disconnected. The keyboard emits no audio or velocity.
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
must use protocol **6**; existing world saves remain version **1**. Physical MIDI devices,
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
its owning block entity. World-save schema remains version 1; older oscillators receive Detune 0 without changing
their saved Frequency or Level. Back up the world before upgrading; restore that backup
if downgrading, since older builds cannot interpret the new control. Network and
performance-snapshot protocol is version 7: update both server and clients together.

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
recording/export, recipes, melodic sequencing, additional waveforms/modules, and other loaders
are deferred.


## Euclidean rhythms: RP-004

The **Euclidean Sequencer** creates four independent trigger patterns, A–D. Each channel
has **Steps 1–16**, **Hits 0–Steps**, **Rotate 0–Steps−1**, and **Mute**. Hits are spread as
evenly as possible; positive rotation moves them clockwise. Defaults are 16 steps with
4/5/7/9 hits, no rotation, and no mutes. Filled markers are hits, hollow markers are rests,
and a white outline marks the current step. The editor and placed block both animate.
Use **Set** or Enter to submit a numeric value; Tab navigates controls, and Page Up/Down,
the arrow buttons, or the wheel scroll the channel cards on smaller screens.

**Start / Resume** begins or resumes the next step. **Stop** releases every pulse and
holds the next-step position; **Reset** arms step one for the next clock. Pattern and tempo
changes wait for the next clock while running; a pending label identifies that transition.
Shortening Steps also reduces an excessive hit count and wraps rotation. Muting releases
an active pulse without stopping the channel's position; unmuting waits for a future hit.

The internal clock defaults to **120 BPM**, with four sixteenth-note steps per quarter
note; tempo is 30–300 integer BPM. **Clock Out** pulses on every step, even when all channels
are muted. Connecting **Clock In** selects external clocking automatically: one rising
edge advances all four channels. A connected source that stops leaves the module waiting;
there is no automatic tempo fallback. Connecting a high source does not invent an edge.
Disconnecting it resumes internal clocking after one interval. Input clocks above 50 Hz
are ignored with a visible diagnostic. **Reset In** resets on a rising edge; simultaneous
reset and clock play step one. Outputs are normalized gates lasting exactly **10 ms**,
compatible with Envelope Gate In and other sequencers' Clock/Reset In. They produce no audio
or pitch. Existing one-cable-per-input, four-destination fan-out and no-feedback rules apply.

Build a two-voice reference patch using one sequencer, two oscillators, two filters, four
envelopes, and two speakers (11 modules, 12 cables):

1. For each voice, connect Oscillator Audio Out → Filter Audio In → Speaker Audio In.
2. Connect Trigger A/B → Envelope 1/2 Gate In; connect their Envelope Outs → the corresponding
   Speaker Gain In. Set oscillator frequencies to 220/330 Hz, Detune 0, Level 50%.
3. Connect Trigger C/D → Envelope 3/4 Gate In; connect their Envelope Outs → the corresponding
   Filter Cutoff In. Set both filters to Cutoff 500 Hz, Resonance 0%, Amount 70%.
4. Set every envelope to Attack 0.001 s, Decay 0.200 s, Sustain 70%, Release 0.080 s.
   Remove manual/redstone gate holds. Enable both speakers at Volume 10%, then Start.
5. Hear separate 4/16 and 5/16 note rhythms with 7/16 and 9/16 brightness accents. Change A
   to 7 steps, 3 hits, rotation 1; mute/unmute B; Stop, Resume, and Reset. Close the editor
   and confirm the rhythms and block playheads continue. Use local mute without stopping them.
6. Optionally connect Clock Out → a second sequencer's Clock In, Start the follower, and
   verify step locking. Use any compatible gate/trigger source for Reset In.

Settings and cables survive saves. Running state, held pulses and positions do not:
world reload and chunk suspension require Start, and speakers must be enabled again.
Closing the editor, disabling speakers, and local mute leave a loaded sequencer running.
Back up worlds before upgrading; install protocol-v7 builds on every client and server.
This version does not add presets, manual step editing, swing, probability, variable gate
lengths, clock division, pitch sequences, MIDI clock, or redstone clock conversion.


## Square Oscillator — RP-005

Place a **Square Oscillator**, **Low-Pass Filter**, and **Amplifier / Speaker** two blocks
apart. Connect Square Audio Out → Filter Audio In → Speaker Audio In. Leave the filter's
Cutoff Control In and speaker's Gain Control In disconnected for a sustained drone.
Use Frequency **220 Hz**, Level **50%**, filter Cutoff **5,000 Hz**, Resonance **0%**,
Cutoff CV Amount **0%**, and speaker Volume **10%**; enable the speaker.

The square panel has the same Frequency, Note/Octave, Set Note, and Level controls as the
saw. **PWM (pulse width)** replaces Detune: enter fractional percentages from **5–95%**,
with **50%** as the default square wave. Compare **50%**, **25%**, and **5%**, then restore
50%. The equal-width square emphasizes odd harmonics; narrower pulses change the tone
and become quieter. PWM is a manual width setting; it has no built-in LFO or extra CV jack.
Compare filter Cutoff **500 Hz** versus **5,000 Hz**, then restore 5,000 Hz.

Optional keyboard variation: connect Keyboard CV Out → Square Pitch In, keep keyboard
Octave **4**, and press **H** for A4 (440 Hz). Adjust PWM while holding the note; pitch
stays at A4. Disconnect Pitch In to return to the saved 220 Hz. Gate is independent:
for articulated notes add Keyboard Gate Out → Envelope Gate In and Envelope Out →
Speaker Gain Control In, using default envelope controls. Disable the speaker or press
local mute to stop. Save/reload retains width, frequency, level and cables; speakers
restart disabled. Human listening and multiplayer qualification remain separate gates.

The new block requires protocol **6** clients/server. World schema remains **1** and
existing saw blocks retain their identifiers and sound. Back up worlds before upgrading;
restore the backup before downgrading to a build that does not know the square block.

## LFO and parameter modulation: RP-006

The **LFO** provides sine (default), triangle, rising saw, falling saw, and square waves.
Click a selector or focus it with Tab and press Enter/Space to cycle choices. Frequency
is **0.01–20 Hz**, default **1 Hz**. Output polarity defaults to bipolar **−1…+1**;
unipolar **0…1** is available for upward sweeps. Square duty is fixed at 50%.
The LFO runs while its patch is loaded, independently of speaker enable and local mute.

Connect Sequencer **Clock Out → LFO Clock In** for automatic synchronization. Cycle lengths
are 1/16, 1/8, quarter (default), half, whole note, two bars, and four bars; bar labels mean
4/4. A sequencer emits four pulses per quarter note, so the default quarter-note LFO runs
at **2 Hz at 120 BPM**. The saved Hz remains editable and resumes when the clock cable is
removed. The first two rising edges acquire timing. After two missing pulse intervals,
phase holds and the panel shows **Waiting for clock**. Restarting the clock reacquires
phase; stopping audio locally does not stop the shared clock. Clock inputs also accept
other gate-role outputs, but irregular triggers produce irregular synchronization.

Filter **Cutoff CV Amount** retains the former Envelope Amount behavior for positive CV.
Negative CV sweeps toward 20 Hz. Filter **Resonance CV In**, and envelope **A/D/S/R CV In**,
have individual signed depth controls, **−100…100%**, default **0%**. A cable alone does
not change a zero-depth parameter. Negative depth reverses modulation. Resonance and
sustain add a fraction of their allowed range; time CV multiplies the base time by
`2^(4 × CV × depth/100)`, clamped to 1 ms–10 s. At 25% depth, bipolar time CV spans
half to twice the base duration. Each time is captured when its stage begins; sustain
follows CV through 10 ms ramps while held. Inputs also accept another envelope's output.

Use the mouse wheel or **Page Up/Page Down** to scroll longer panels. Tab selects visible
controls; exact numeric entry and Apply remain available. The lower controls stay visible.
Every input accepts one source; each output can fan out to four inputs. Feedback is refused.
LFO → Speaker Gain In supports tremolo; bipolar negative values clamp to zero gain.

Build these RP-006 variations with blocks two blocks apart, all owned by the same player:

1. **Free filter sweep:** Saw Audio Out → Filter Audio In → Speaker Audio In;
   LFO Control Out → Filter Cutoff In and Resonance In. Use saw 220 Hz, Detune 0, Level 50%;
   speaker 10%, enabled; filter cutoff 5,000 Hz, resonance 25%, Amount 30%, resonance depth
   20%; LFO sine, 1 Hz, bipolar, quarter division (inactive without a clock). Expect one
   bright/dark sweep per second. Compare all five waves, then unipolar and bipolar.
2. **Clocked sweep:** Add Sequencer Clock Out → LFO Clock In to variation 1. Use tempo
   120 BPM and default channel patterns, then Start. Quarter division gives two sweeps per
   second; whole note gives one every two seconds. Change tempo to 90 BPM, Stop, Start,
   and disconnect Clock In; verify held phase, reacquisition, and restoration of 1 Hz.
3. **ADSR CV:** Starting from variation 1, add Envelope Out → Speaker Gain In, LFO Control
   Out → Envelope Attack In and Sustain In (four destinations total). Use attack .100 s,
   decay .200 s, sustain 50%, release .300 s; attack/sustain depths 25%, other depths 0%.
   Hold the envelope gate for two seconds, release for one second, repeat. Attack durations
   vary between notes and sustain moves while held. Then remove the Attack/Sustain CV cables,
   set those depths to 0%, connect LFO to Decay/Release instead, and set those depths to 25%.
   Repeat the gate pattern to hear decay/release variation. No redstone or external gate is
   needed. All waveforms/settings not changed above retain variation 1 values.

World schema remains **1**; exact legacy filter/envelope records gain zero-valued depths,
so existing patches keep their settings. Network/snapshot protocol is **7**: update clients
and server together. Back up the world before upgrading; restore that backup when returning
to an older binary, which cannot understand LFO records. Unknown/malformed records remain
preserved rather than silently repaired. Transient phase is not saved across world/chunk
reload; speakers still restart disabled. Native listening and multiplayer acceptance are
tracked separately in the [ledger](validation/ledger.md).
