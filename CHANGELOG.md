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
