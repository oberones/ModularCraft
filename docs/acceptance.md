# Acceptance workbook

Use the exact artifact hashes from `build/libs/SHA256SUMS`. Record the source revision
and dirty state, both machines, Java, NeoForge, OS, display settings, output devices,
network conditions, capture devices/rates, clock alignment procedure, uncertainty,
operator, date, workload, and raw evidence paths. PASS/FAIL/BLOCKED/PENDING are separate;
an offline render or native counter is not an acoustic capture or a listening pass.

## Automated commands

```sh
make verify
make integrity
```

The [Makefile guide](development.md) covers packaged launches and isolated smoke worlds.
`make verify` includes packaged headless GameTests; `make ci` is its no-daemon counterpart.

`MeasurementTest` measures four 60-second pitch cases in the main suite, one-second ADSR
linearity and intermediate retriggers in `CoreTest`, MU-009 filter behavior, finite
extremes, DC, aliasing, and a fixed-load DSP benchmark. Alias measurement uses coherent
frequencies nearest 55/220/880/3,520 Hz, a 65,536-frame FFT, and a 16x naive-saw reference
with ideal spectral downsampling. Non-harmonic residual bins measure aliasing; harmonic
pass-band coloration is not misreported as aliasing. A 128-sample warmup excludes the
initial filter fill. Pitch tests use the exact specified frequencies for 60 seconds each.
ADSR stage edges are checked to one sample, independently of game ticks. Arithmetic
transport is simulated over one hour including pause. Seeded malformed snapshot tests
exercise 1,000 inputs, and world roundtrips repeat 20 times.

The fixed DSP fixture has four patches, each with one oscillator, ten series filters,
three envelopes, and two speakers; 12 audio and 12 control cables. Each envelope fans
out to four distinct control inputs, covering all ten filters and both speakers.
Grid positions and controls match SC-005; the offline timing run is not the 30-minute
native/network workload.

## Native fixture, isolated development use

For an automatically prepared disposable fixture, use `make smoke`, `make smoke-load`, or
`make smoke-recovery TICKS=500`. Each builds/stages the current JAR, creates a fresh world,
bootstraps a GameTest world seed if needed, and exits after the configured game ticks.
The manual procedures below remain available for custom acceptance environments.

The test source set includes an opt-in native fixture and probe; neither ships in the
mod or sources JAR's executable sources. Use a **new disposable run directory**, copy a
fresh GameTest `level.dat` into `saves/ModularCraft-QA/`, and prepare `options.txt` before
launch. Never point the fixture at a personal save: it places a platform and instruments
near spawn. Example setup is recorded in `validation/native-load.log` and this workspace's
`run/native-load` directory. Render settings: 1080p, render distance 12, simulation 8,
shaders off, VSync off, `pauseOnLostFocus:false`, `onboardAccessibility:false`.

```sh
./gradlew runClient -Pqa -PclientRunDir=run/native-qa
./gradlew runClient -Pqa -PqaLoad=true -PclientRunDir=run/native-load
```

For packaged verification, copy the runtime JAR named by `build/runtime-artifact.json` into the disposable
run directory's `mods/` folder and add `-Ppackaged`. The probe prints the loaded code
origin so an IDE class directory cannot be mistaken for the installable artifact.
Add `-PqaCycles=true` for twenty local audio resynchronizations with native channel/timer
teardown assertions. This does not replace the world-restart/device lifecycle checklist.

The default probe runs 1,200 ticks (about one minute), prints queue/peak/processing/underrun
counters, and exits. `-PqaTicks=36000` runs 30 minutes at normal tick rate. Gate schedule
is two seconds on/two seconds off. Fixture code is
`src/gametest/java/com/malevolentgods/modularcraft/gametest/NativeFixture.java`.
A one-client development probe does **not** satisfy two-client packaged SC-005.

## Packaged play and timing

1. Install only the candidate JAR plus the exact loader on a fresh Creative client and
   a normal headless dedicated server. Confirm nine blocks/tool, all textures/models,
   labeled ports, cable arrows, and every panel control. Confirm no client/audio class
   loads on the server. Obtain/accept any host licensing terms as the server operator.
2. Build both README reference patches using only their instructions. Test every listed
   variation, redstone/manual OR gating, retriggers, short gates, disabled/missing inputs,
   and accepted/rejected numeric edits. Record actual listening, including starts/stops,
   maximum controls, disconnection to a drone, and multiple speakers.
3. SC-003: ten minutes, one RP-001 patch, 100 gate/parameter actions. Capture an input
   marker and acoustic onset on aligned clocks; subtract deliberately configured ADSR
   duration when classifying responsiveness. p95 input-to-response ≤100 ms, no stuck notes
   or unintended interruptions >100 ms. Measure mute separately at ≤100 ms.
4. SC-004: two macOS clients on a dedicated server. RTT ≤50 ms, variation ≤10 ms, no
   induced loss. Capture 100 shared edits/gates on both outputs. p95 response ≤150 ms;
   p95 inter-client onset difference ≤50 ms. Report synchronization uncertainty rather
   than assuming packet timestamps equal physical output time. Join during attack,
   decay, sustain, and release: current-state recovery ≤1 second, no replay/retrigger.
5. Spatial: sample distances 2, 8, 16, 24, 32, and >32 blocks from speaker center; expected
   gain is 1, .8, .5333, .2667, 0, 0. Turn the listener, use two speaker positions, move
   dimensions, and verify local controls never affect another listener. Compare Master,
   Records, local volume, mute, and speaker disable independently.
6. SC-005: both clients, 30 minutes, four exact maximum-load patches. Each is a 4×4 grid
   at two-block spacing; origins form a 2×2 grid eight blocks apart. Pitches 110/220/330/
   440 Hz, levels 50%, cutoff 5,000 Hz, resonance 25%, amounts 50%, default ADSR, speakers
   10%. Hold all gates two seconds/release two seconds. Compare identical inactive and
   active worlds on M1-class Apple Silicon with ≥16 GB, macOS 26.6.2, Java 21, and the
   display settings above. p95 frame duration increase ≤20%, world update p95 <50 ms,
   DSP+mix p95 <1 ms/block, mod server work target ≤2 ms/tick. Record tail spikes,
   allocations, native queue depth, underruns, and acoustic gaps, not just averages.
7. SC-006: 20 save/restart cycles and 20 unload/reload/reconnect cycles. Compare exact
   UUIDs, owners, controls, and cable endpoints. Speakers must require re-enabling after
   restart/patch unload. Gate release must survive screen close, range/permission loss,
   death, disconnect, and redstone changes. Resource counts return to idle; old voices
   stop within 250 ms of locally observed lifecycle events. Change devices and reload
   resources during held notes, including recovery while muted.
8. Exercise permissions, operator edits, stale races, malformed packets, all capacity
   limits/split/merge paths, over-distance/dimension cables, replacement identities,
   corrupt/unsupported records, unloaded quotas, and request/traffic overload. Each
   rejection must preserve accepted state and identify a recovery action.
9. SC-001: five first-time users, fresh installs, only the README. At least four build,
   wire, enable, play, release, and locate local mute within ten minutes without help.

Hosted CI is a separate record: a local workflow-shaped command is not a hosted run.
Do not call the MVP fully accepted until every applicable release gate passes. Preserve
failed captures and provenance alongside the final pass; do not overwrite the original
world or recordings during recovery.


## RP-003 keyboard acceptance

Run `make smoke GRADLE_ARGS=-PqaKeyboard TICKS=500` for a fresh packaged-JAR fixture.
It opens the actual keyboard through server inspection, plays overlapping computer keys,
transposes a held note, drags over black/white keys and outside, reopens through audio settings, checks accepted note/gate state, and captures six native
screenshots. Inspect idle/held/octave/released panels and the world block appearance.

Separately audition all twelve notes, mouse drag over black keys and outside, mouse plus
computer holds, octave limits, same-key repeats, close/reopen through audio settings, focus
loss, CV/gate cable removal, manual/redstone overlap and release tails. Verify A4 = 440 Hz,
C1/B7 bounds, CV disconnection restoring Frequency, and no unintended clicks/stuck notes.
Repeat unload, removal, disconnect, permission/range loss, and overload recovery. Run
SC-003 and SC-004 with RP-003 (including two listeners and late join); automated native
state assertions do not establish listening or acoustic timing acceptance.


## RP-003 MIDI acceptance

Automated command: `make smoke GRADLE_ARGS=-PqaKeyboard TICKS=500`. Native synthetic events
must show note 69/gate=true after pedal-held Note Off and note 69/gate=false after pedal release.
Inspect the keyboard screenshots for readable device/channel/status/release controls. This is
not a physical MIDI or listening pass. Run `make verify` for codec, authoritative server and
fake-provider lifecycle tests.

For physical acceptance, record the runtime JAR hash, Java/macOS versions, controller model,
firmware, connection/driver, audio device, sample/buffer configuration and measurement method.
Use RP-003 at conservative speaker volume, then perform the following:

1. Select the input and All channels; play note 69 (A4, 440 Hz). Change the panel octave while
   holding it: MIDI pitch must remain fixed. Overlap computer/mouse and MIDI notes, release in
   both orders, and confirm last-held fallback and retriggers. Try MIDI 0 and 127 at low volume;
   validate pitch numerically where physical audition is inappropriate.
2. Repeat a note, send velocity-zero Note On, test channels 1 and 16 individually and together,
   and confirm filtering. Sustain notes with CC64 values 64/127, release them with 63/0, and
   verify GUI notes are unaffected. Test CC123 under sustain, CC121 with physical holds, CC120,
   and Release Notes. Envelope tails are expected; local mute must stop local audio promptly.
3. Hold/sustain while changing device/channel, closing the panel, opening local audio settings,
   unfocusing, reloading resources, moving out of range, changing dimension, disconnecting and
   breaking/unloading the block. No old hold may return on reconnect/refocus/reopen.
4. Unplug the selected device while holding a note and verify reported removal releases input.
   Replug, Refresh and play fresh notes. Record whether the provider reports removal; if it
   does not, record BLOCKED device-loss qualification and verify Release Notes/close recovery.
   Test unavailable and duplicate device descriptors without substituting another device.
5. Repeat open/close/reconnect 20 times, playing fresh notes after every return; native
   device/transmitter/receiver and helper-process counts must return to idle baseline. Flood supported MIDI events until overflow: input must release, the
   panel must request reopening, and the game must remain responsive. Unsupported CC/pitch
   bend floods must not consume the event queue. The isolated native helper must exit after
   close (including forced termination of a blocked native close), with no overlapping helper.
   A parent-side discovery stall still requires timeout guidance and may need a game restart.
6. Apply SC-003 (100 measured actions over ten minutes, p95 <=100 ms, zero stuck notes) and
   SC-004 (two clients, RTT <=50 ms/jitter <=10 ms, p95 <=150 ms response and <=50 ms skew).
   Record MIDI event arrival and acoustic onset uncertainty; do not infer this from fake tests.
   Audition sustain release, fallback, octave changes and reconnect for clicks/stuck sound.

Physical MIDI, unplug reporting, listening and multiplayer remain PENDING until measured.
Windows/Linux device qualification is outside the current macOS-first acceptance claim.


## Unison spread audition

Use the README's RP-001 drone variation and RP-003 A4 variation, recording artifact hash,
backend/device and capture/listening conditions. Compare 0/12.5/25/100 cents: one center saw
at zero, audible beating/three-voice spread otherwise, no center transpose, conservative
level. Repeat slider/numeric edits and return to zero; listen for clicks, stuck voices or
unexpected attenuation after settling at zero. Confirm MIDI and released-note tails retain
the selected spread; disconnect CV to restore the saved center frequency. Save/restart and
confirm spread persists, while speakers restart disabled. Repeat with two clients under
SC-003/004. Automated spectral/UI checks do not establish this listening/timing acceptance.


## Euclidean sequencer qualification

Run `make smoke GRADLE_ARGS=-PqaSequencer TICKS=650` for a disposable packaged four-orientation
fixture, numeric editing, mute/transport checks, screenshots and 20 native audio cleanup
cycles. Review the screenshots at normal GUI scale and also test small-window scrolling,
Tab/Enter and Page Up/Down. This probe is not a listening pass.

Audition README RP-004: distinguish both rhythms and filter accents, shorten one channel,
rotate, mute, stop/resume/reset, close the editor, and use local mute. Confirm uninterrupted
playheads when muted or speakers are disabled. Chain a second module by Clock Out → Clock In;
stop the source and verify the follower waits. Disconnect the input and hear internal timing
resume after one interval. A held-high clock must not create a connection pulse. Reset
simultaneously with clock must play step one. Listen for unwanted clicks and stuck gates.

Repeat joins, reconnects, pause/resume, chunk unload/reload and world restart; require fresh
Start after suspension/restart while saved settings/cables survive. With two clients apply
SC-004 conditions, document onset skew and edit latency, and confirm joining does not reset
another listener. Run the 30-minute load qualification with four sequencer-driven patches,
record hardware/backend, p95 block/tick/frame time, allocations and underruns. Existing
release gates remain applicable until independently observed.


## Square oscillator acceptance

Run README RP-005 and record width 50/25/5%, filter comparison, keyboard/MIDI pitch,
width edits while held, CV removal, Level 0, enable/disable/mute, save/reload, chunk unload,
block removal and reconnect. Confirm saved saw patches retain their original settings/sound.
On two matching protocol-v8 clients verify shared PWM edits, late join, authority and timing
against existing SC-003/004 limits. Listen for clicks, stuck tones, harsh aliasing and intended
pulse-tone changes; automated samples/screenshots do not establish listening acceptance.
Use a disposable `make smoke GRADLE_ARGS=-PqaSquare TICKS=600` for widgets and screenshots.

## LFO / CV acceptance (RP-006)

Follow every RP-006 variation in README, including all shapes/polarities, tempo edits,
clock stop/resume/disconnect, each ADSR input, inverted depths and zero-depth restoration.
Capture the same held/released notes with two clients; join during attack and a sustain
ramp, recover audio, mute/unmute, pause/resume singleplayer, and unload/reload the patch.
Confirm current phase on listener recovery and fresh transient phase after reload, disabled
speakers after unload, no replay backlog, clicks, stuck gates, or unexpected gain inversion.
Record listening separately from numerical measurements and synthetic native UI assertions.
Use the LFO load fixture for a 30-minute/two-client qualification; the short automated fixture
is only functional/performance evidence for its recorded duration and machine.

## Mixer / RP-007

Run the four-source RP-007 patch in README. Audition every input and Master while dragging,
entering exact values, muting, soloing multiple channels, disconnecting and reconnecting.
Check smooth transitions, silence with Master mute, and expected individual/blended pitches.
Observe pre-speaker meters and OVER, then verify local mute/paused/inactive states. Repeat
at compact GUI sizes with keyboard navigation and narrator enabled. Reopen after local
settings and verify accepted edits. Save, restart and reload: all mix controls/cables must
persist, speakers start disabled, and enabling restores the intended blend.

On two protocol-v8 clients verify shared edits, stale/foreign rejection, late join,
chunk/dimension unload, removal and recovery. Run the mixer load fixture for 30 minutes,
record hardware, tails, underruns and p95 budgets. Automated probes do not mark these
listening, multiplayer, disk-restart or sustained gates passed.

## Amp / Speaker acceptance

Use RP-008 from the user guide. Solo each of four mixer inputs and verify its sequencer
trigger independently articulates the intended voice. Compare ADSR, Drone, and external
Envelope/LFO override; disconnect Gain In while playing, change Level, and listen for
unintended clicks. Amps alone must never emit sound. Check spatial output at one and two
Speakers and rejection of a third Speaker without rejecting additional Amps.

New Speakers start enabled and remember their switch; this supersedes restart-disabled
steps above. Verify enabled/disabled save/reload, whole-patch chunk availability, capacity
waiting, and explicit overload recovery. Sequencers still need Start after reload. Test
migration on a COPY of a schema-1 world: old combined blocks become Amps, preserve gain
cables/volume, and need new Speakers. Verify malformed records retain their original data.

Automated probes: `make smoke GRADLE_ARGS=-PqaAmp TICKS=600` and
`make smoke-load GRADLE_ARGS=-PqaAmp TICKS=1200`. The latter contains four 16-module,
18-cable, two-Speaker patches. Existing 24-cable fixtures cover aggregate cable bounds;
their former Speaker Gain inputs now target filter resonance. Record native screenshots,
DSP/server p95, allocations and underruns. Human listening, two-client skew, and 30-minute
sustained testing must be recorded separately; short smoke runs do not establish them.
