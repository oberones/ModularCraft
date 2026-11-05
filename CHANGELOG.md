# 0.1.0 development candidate

Initial NeoForge 1.21.1 implementation: four adjustable modules, visible typed cables,
owner/operator edits, persistent compositions, server-synchronized local synthesis,
manual/redstone linear ADSR, local mute/volume, and bounded existing-device OpenAL output.
Save and network schemas start at version 1. Speakers intentionally restart disabled;
manual gates and envelope progress are transient. Unknown data is preserved/quarantined.
No earlier public saves need migration. Back up worlds before installing development builds.

The initial PolyBLEP implementation failed the alias residual gate; the candidate adds
4x internal oscillator oversampling and a preallocated decimator while retaining a 48 kHz
public graph rate. The native queue was increased from 21.3 ms to the allowed 32 ms after
measured underruns. See the evidence ledger for outstanding acceptance and performance
findings. This candidate has not been publicly published or accepted as a release.

Added a comprehensive Makefile with built-JAR interactive/client/server launches,
packaged GameTests, isolated native smoke/load/recovery fixtures, checks, packaging,
and maintenance targets. Managed instances preserve manual worlds and replace only their
tracked mod JAR. CI now uses `make ci`; launcher isolation tests accompany the commands.

Patch cables now use slim solid tubes with tapered input collars instead of thin lines
and branching arrowheads. Endpoints align with the painted jacks, and gentle sag keeps
the entire cable above the lower connected block's base. Connection rules, saves, and
audio are unchanged. An opt-in native cable fixture captures three review views.


The keyboard now accepts opt-in client MIDI input with exact notes 0–127, per-channel sustain,
combined GUI/MIDI last-note priority, remembered local device/channel selection, Refresh,
Release Notes and bounded asynchronous device recovery. Panel octave affects GUI keys only.
Network/snapshot protocol is now v3: upgrade clients and server together. World-save schema
remains v1, with no saved device handles or held performance; backups and earlier qualification
gates remain applicable. MIDI uses Java Sound without added dependencies. Hardware/listening
and two-client acceptance are separate from automated and synthetic native checks.

Fixed MIDI timeouts being hidden by Refresh, focus changes or device/channel selection.
The panel now keeps the timeout visible while a native operation is blocked and explains
when restarting Minecraft is needed. This does not repair native Java Sound provider hangs.

Fixed macOS MIDI input becoming silent after leaving and reopening the keyboard panel.
Each native connection now uses a fresh, bounded Java helper process; closing reaps it even
when the Java Sound reader fails to stop. One client worker handles lifecycle and input
polling, with no new dependencies or changes to notes, channels, saves or networking.


Added **unison spread Detune** to the Saw Oscillator: 0–100 cents per side with fractional
numeric entry. Nonzero spread mixes three normalized saw voices around the manual or
keyboard/MIDI center pitch; zero restores the original single saw. Pitch and mix edits
ramp over 10 ms, with phase/history preserved and inactive side DSP skipped at zero.
Legacy world/block oscillator records migrate to Detune 0; other unknown/invalid records
retain quarantine protection. World schema stays v1; network/snapshot v4 requires updating
clients and server together. Back up worlds before upgrading and restore the backup for
downgrade. Listening and multiplayer qualification remain separate from automated checks.


Added the Euclidean Sequencer: four independently configurable 1–16-step rhythm channels,
rotation/mutes, internal 30–300 BPM sixteenth-note clock, external clock/reset inputs and
clock output. Exact 10 ms sample-timed pulses drive existing envelopes; clock chains share
a bounded deterministic control timeline. Start resumes; Reset arms step one. Closing the
panel, disabling speakers and local mute leave the clock running. Settings/cables persist;
restart and chunk suspension require Start. The editor and block face show animated rings.

Network/snapshot v5 adds sequencer checkpoints and explicit envelope gate baselines; update
clients/server together. World/block v1, legacy detune migration and unknown-record
preservation remain compatible. Back up worlds before upgrades; new block registry entries
make downgrading a world containing sequencers unsupported. No new dependencies. Native
listening, multiplayer and all earlier qualification gates remain separately documented.


Added Note and Octave dropdowns with a Hz preview and explicit Set Note to the oscillator
panel. Concert tuning uses A4 = 440 Hz; E0–B8 fit the existing frequency range. Arbitrary
Hz tuning remains available, and connected Pitch In retains precedence. Draft selections
survive updates/resizing; the compact panel supports keyboard navigation. Only the resulting
Frequency is saved, with no schema, protocol, dependency or DSP changes.

Added a separate **Square Oscillator** with the saw's Frequency, note/octave selection,
Level, Pitch In and Audio Out, plus manual **PWM (pulse width)** from 5–95% (default 50%).
Fractional widths, keyboard/MIDI pitch precedence and 10 ms continuous edits are supported.
The original SQR face uses the matching chassis and cyan accent. Single-voice pulse DSP
uses DC compensation, bounded output and pulse-specific anti-alias filtering; saw synthesis
is preserved. Network/snapshot v6 requires matching clients/server; world v1 needs no new
migration. Back up before upgrading and restore that backup for downgrade. RP-005 documents
the pulse/filter patch. No new dependencies; native listening/multiplayer remain separate gates.


Fixed Shift-right-click cable removal: sneaking tool use now reaches the module's existing
port handler instead of falling through to selection cancellation. Removal still targets
one destination input and respects ownership; the overlay now names the right mouse button.

LFO/CV extension (2026-11-05): eighth module with five waves, 0.01–20 Hz free frequency,
bipolar/unipolar output and automatic pulse-measured clock divisions. Add signed resonance
and four ADSR CV depths; capture time CV at stage entry and smoothly track held sustain.
Existing cutoff CV gains a bipolar downward sweep and the label Cutoff CV Amount. Legacy
positive envelope mapping and short-note ADSR gain remain unchanged. Panels scroll with
fixed gate/local-audio controls. Delayed previous-panel close messages no longer invalidate
a newly inspected module. Protocol/snapshot 7 requires updating both client and server;
world schema 1 migrates exact old filter/envelope records with zero depths. Back up before
upgrading and restore the backup to downgrade. Human listening and sustained multiplayer
acceptance remain separate from automated checks; no release/publication is implied.

Audio Mixer extension (2026-11-05): ninth module, four mono audio inputs and one output;
input level/mute/multi-solo, master level/mute, live vertical faders and local peak meters.
Defaults: inputs 100%, master 25%. Ten-millisecond gain ramps, no automatic normalization,
and existing final output protection. World schema 1 retains existing records; network
and snapshots now require version 8 on every peer. Back up worlds before upgrade; restore
the backup before downgrade. RP-007 and mixer native/load probes added. Human listening,
multiplayer and sustained acceptance remain separately tracked; no release is implied.

## v0.1.0 (2026-11-05)

### Feat

- add mixer block
- add clock-synced LFO
- add square oscillator block
- add better note control to saw oscillator block
- add euclidean sequencer module
- add detune capability to saw osc module
- add midi support to keyboard block
- add keyboard block
- initial MvP with four basic module blocks

### Fix

- address UI bug that made text difficult to read
- update wiring graphics to prevent clipping
