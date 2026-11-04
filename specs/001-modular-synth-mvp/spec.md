# Feature Specification: Modular Synthesizer MVP

**Feature Branch**: `001-modular-synth-mvp`
**Created**: 2026-11-03
**Status**: Draft — validated for planning
**Input**: User description: "I want to create an MVP for a minecraft mod that implements modular synthesis in block form. Each type of synth module, oscillator, filter, etc., will be represented by a unique block. The blocks will then be able to be 'wired together' in order to create a full modular synthesizer. The MVP for this project should be a fully functional mod with a saw oscillator block with adjustable controls, a filter block with adjustable controls, an enveloper generator with adjustable controls, and an amplifier/speaker block that actually outputs the sound to nearby players. Use blender and your own image generation as needed to create visuals."
**Constitution**: [ModularCraft Constitution](../../.specify/memory/constitution.md), version 1.0.0, reviewed 2026-11-03.

The MVP lets a player build, play, adjust, and save an audible modular synthesizer inside Minecraft. It delivers five distinct module blocks, visible connections, and sound shared with nearby players. The instrument produces sound from the current patch and controls; a decorative installation or prerecorded sound loop does not satisfy this feature.

## Clarifications

### Session 2026-11-03

- Q: How should players adjust module controls in the MVP? → A: Right-click a block to open its control panel; use in-world ports for wiring.
- Q: What shape should the envelope’s attack, decay, and release transitions use? → A: Fixed linear attack, decay, and release; the control value changes evenly throughout each stage.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Build and hear a first instrument (Priority: P1)

A player obtains the four modules and a patching tool in Creative mode, places them, and follows a short guide to connect a saw oscillator through a filter to an amplifier/speaker. An envelope generator controls the amplifier's loudness, giving the player a playable note.

**Why this priority**: A complete, audible path through all four requested modules is the core MVP.

**Independent Test**: In a fresh Creative world, follow reference patch RP-001 using only the supplied guide. Enable the speaker, hold the envelope's manual gate, hear the note, then release it and hear the sound fade to silence.

**Acceptance Scenarios**:

1. **Given** a fresh supported installation, **When** the player opens the Creative inventory, **Then** the five separately named, visually distinct modules and patching tool are available, placeable, and identifiable.
2. **Given** the RP-001 connections and defaults, **When** the speaker is enabled and the manual gate is held for two seconds and released, **Then** the speaker produces the configured saw tone, its loudness follows attack, decay, sustain, and release, and it returns to silence.
3. **Given** an oscillator connected directly to an enabled speaker with no gain-control cable, **When** the frequency changes from 220 Hz to 440 Hz, **Then** a sustained tone rises by one octave; disconnecting its audio cable silences that speaker.
4. **Given** an enabled speaker with no audio source or an envelope connected without an audio source, **When** the patch runs, **Then** the speaker remains silent and identifies the missing audio input.

---

### User Story 2 - Shape and perform the sound (Priority: P1)

A player adjusts oscillator pitch and level, filter brightness and resonance, envelope shape, and speaker volume while hearing the result. Manual controls and ordinary redstone provide a way to play the envelope independently of the optional keyboard connections.

**Why this priority**: Adjustable, musically effective modules distinguish a synthesizer from a fixed sound block.

**Independent Test**: Use RP-001, compare the defined control settings in MU-008, and run RP-002 to hear an envelope sweep the filter. Perform the same held note manually and with a redstone lever.

**Acceptance Scenarios**:

1. **Given** an audible patch and permission to edit a module, **When** the player right-clicks the block to open its control panel and edits a numeric control, **Then** its label, unit, accepted value, and effect match the module contract; playback continues while the panel is open, and reopening it shows the accepted value.
2. **Given** a sustained saw tone, **When** cutoff is lowered from 8,000 Hz to 500 Hz, **Then** upper harmonics are reduced; raising resonance emphasizes frequencies near cutoff without creating a runaway output.
3. **Given** a released envelope, **When** either the manual gate or redstone becomes active, **Then** linear attack begins, followed by linear decay to sustain; while either remains active the envelope sustains, and when both are inactive linear release begins.
4. **Given** an envelope in release, **When** the gate becomes active again, **Then** attack restarts from its current level without a discontinuous jump. Ending a gate during attack or decay begins release from the current level.
5. **Given** RP-002, **When** a note is triggered, **Then** its envelope shapes both loudness and filter brightness; setting filter modulation amount to zero restores the base cutoff.
6. **Given** a control editor, **When** an invalid, non-finite, or out-of-range value is submitted, **Then** the previous valid value remains and the permitted range is shown.

---

### User Story 3 - Understand and edit a patch (Priority: P2)

A player can see what is connected, identify port roles, add or remove a cable, and recover from an invalid connection without losing a working patch.

**Why this priority**: Understandable patching is required for experimentation beyond the tutorial.

**Independent Test**: Build RP-001, fan out its envelope to the filter, inspect the cables, then attempt incompatible, duplicate, occupied-input, over-distance, and cyclic connections.

**Acceptance Scenarios**:

1. **Given** two compatible in-world ports, **When** the player selects a source and destination on the block faces with the patching tool, **Then** one visible cable appears and each endpoint identifies its role and connection; using the tool on a port selects that port instead of opening its module control panel.
2. **Given** an output with an existing cable, **When** it is connected to another compatible free input within limits, **Then** both destinations receive the signal. An input accepts only one source.
3. **Given** an incompatible, self, cyclic, duplicate, occupied-input, cross-dimension, or over-distance connection attempt, **When** it is submitted, **Then** no cable is added or replaced and a specific rejection explains how to correct it.
4. **Given** a valid patch, **When** a cable is explicitly removed or a module is broken, **Then** only the affected connections are removed, remaining settings are preserved, and affected sound follows the disconnected-input rules without a stuck note.
5. **Given** a pending cable selection, **When** it is cancelled or its source disappears, **Then** no partial cable remains. Selection and errors are understandable without relying on color or sound alone.

---

### User Story 4 - Share sound while retaining listener control (Priority: P1)

Nearby players hear the same performance from the speaker's location. Each listener can reduce or mute ModularCraft audio independently of the musician and other listeners.

**Why this priority**: Actual sound for nearby players is an explicit user requirement, including multiplayer.

**Independent Test**: With two clients on a dedicated server, one player performs RP-001 while the other walks around and away from the speaker, joins during a held note, and uses local mute.

**Acceptance Scenarios**:

1. **Given** two listeners in range in the same dimension, **When** the musician triggers or changes the instrument, **Then** both hear the same pitch, timbre, and envelope progression within SC-004 timing limits.
2. **Given** a fixed speaker, **When** a listener changes position or orientation, **Then** sound is located at that speaker, becomes quieter with distance, and is inaudible at or beyond 32 blocks or in another dimension.
3. **Given** a held note, **When** a player joins or enters range, **Then** the player hears the current sustained state within one second without restarting the note for anyone. Joining during an envelope's progression uses its current progression rather than replaying its attack.
4. **Given** shared playback, **When** one listener mutes or adjusts local volume, **Then** only that listener's output changes. Game master volume and the documented sound-category volume also apply.
5. **Given** a patch owned by another player, **When** a non-owner attempts an edit without operator permission, **Then** the edit is rejected, the patch remains unchanged, and listening remains available.

---

### User Story 5 - Keep a patch and recover safely (Priority: P2)

A player can leave, reload the world, and return to the same module settings and cabling. Interruptions stop affected sound, preserve valid saved work, and explain how to resume.

**Why this priority**: A functional Minecraft instrument must survive normal world and multiplayer lifecycles.

**Independent Test**: Save RP-001 with changed values; exercise world restart, chunk unload/reload, block removal, reconnect, and audio-device interruption, then compare settings and connections.

**Acceptance Scenarios**:

1. **Given** a saved patch, **When** the world restarts, **Then** module identities, positions, owners, parameters, and surviving cable endpoints are restored exactly. Speakers start disabled, manual gates start released, and enabling a speaker resumes from a fresh envelope according to current redstone power.
2. **Given** a sounding patch, **When** any module's chunk unloads, **Then** the connected patch becomes silent without loading chunks to keep it active. Once all its chunks return, cabling and settings remain and its speakers require explicit re-enabling.
3. **Given** local playback, **When** the player disconnects, changes dimension, or loses the audio device, **Then** obsolete local sound stops. Recovering local playback resynchronizes to current world state without replaying missed notes or altering other listeners.
4. **Given** a stored patch with corrupt data, unknown modules, or an unsupported format, **When** it is loaded, **Then** the affected patch stays silent, its original stored data is preserved, and a bounded diagnostic identifies the recovery action; unrelated valid patches still work.
5. **Given** supported capacity is already reached, **When** another placement, cable, or activation is attempted, **Then** it is rejected before partial changes, existing patches keep working, and the applicable limit is displayed.

### Edge Cases

- Empty or partially wired patches are valid edits; disconnected audio inputs produce silence, disconnected gain control means full control gain, and disconnected filter control means no modulation.
- Removing an envelope cable while a gate is released deliberately changes the amplifier from envelope-controlled silence to a sustained tone if audio is present; the transition is smoothed and the unconnected-input behavior is shown before removal.
- Self-connections, cycles, implicit mixing, and replacement of occupied inputs are rejected. Splitting or merging patches checks resulting capacities before accepting the edit.
- Very short gates, gate changes during any envelope stage, redstone changes while manual gate is held, closing the manual-gate screen, and the controlling player leaving follow MU-003; no manual gate remains stuck.
- Rapid edits and simultaneous edits from an owner and an operator produce a single accepted ordering. A stale edit is rejected with refreshed values rather than overwriting newer work.
- Parameter extremes, silence, maximum resonance, and several overlapping speakers remain finite and within the final output ceiling; invalid values never become audible state.
- Chunk boundaries, listeners crossing the audible radius, world unloads, resource reloads, pause/resume, and reconnects do not duplicate playback or accumulate missed events. A true singleplayer pause freezes musical progression; closing a menu on a running multiplayer server does not pause it.
- Breaking and replacing a module at the same position does not reconnect stale cables. A newly placed module has a new identity and defaults; removing a module does not implicitly restore it from saved patch data.
- A late listener or listener recovering after local mute hears the current shared state. A local failure cannot stop or restart the shared instrument for others.
- Global limits apply across dimensions and players. Rejection or overload never erases stored patches or causes unbounded catch-up work. If unavoidable world destruction splits a patch into more active components than capacity allows, the resulting affected components are suspended and require explicit re-enabling; unrelated patches continue and world cleanup is never blocked.
- Tempo, clocks, sequencers, patch-file import/export, polyphonic note allocation, and older-version migration are outside the MVP. The first release still identifies its save format and safely refuses unknown/newer data.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Provide exactly five synth module block types: Saw Oscillator, Low-Pass Filter, Envelope Generator, Amplifier/Speaker, and Keyboard. Each MUST have its own recognizable in-world appearance, inventory representation, name, and accessible control surface.
- **FR-002**: Provide Creative inventory access to all modules and a patching tool, plus a concise guide containing the complete RP-001 placement, cabling, control, enable, play, release, and mute instructions. A fresh installation MUST support this journey without development commands or external audio software.
- **FR-003**: Players MUST be able to place, inspect, adjust, and break modules using ordinary in-world interactions. Right-clicking a module block without using the patching tool on a port MUST open that module's control panel. All module parameters, the envelope's momentary manual gate, and the speaker's enable/disable control MUST be operated through these panels. Controls MUST expose the ranges, units, defaults, and precise numeric entry or keyboard adjustment defined in MU-001. Panels MUST keep the world and musical playback running, including in singleplayer, so edits can be heard immediately. Edit permission and interaction eligibility remain subject to SS-001.
- **FR-004**: The patching tool MUST support source/destination selection at in-world ports on block faces, cancellation, explicit cable removal, endpoint inspection, and visible established cables. Using the tool on a port MUST select that port rather than open a module control panel. Cable direction and signal role MUST be conveyed through labels or shapes as well as any color coding.
- **FR-005**: Cabling MUST follow MU-002, connect modules in the same dimension at a maximum distance of 16 blocks between block centers, and require both endpoints to be loaded when created. Cables may cross other cables or intervening blocks without attaching to them; physical cable routing and collision are outside scope.
- **FR-006**: The oscillator MUST generate a continuous saw waveform at the selected frequency and level. The filter MUST process its connected audio rather than substitute a fixed sample. Live control edits MUST audibly affect the existing performance within SC-003.
- **FR-007**: The envelope MUST generate an attack/decay/sustain/release (ADSR) control signal, using both a momentary manual gate and redstone power at the envelope block. The amplifier MUST multiply its audio by the connected envelope control and its own volume setting before playback.
- **FR-008**: Every speaker MUST provide an enable/disable control and volume control. Newly placed speakers MUST be disabled. An enabled speaker without a valid audio path MUST be silent and show why; only speakers emit sound.
- **FR-009**: Sound MUST originate at each speaker, use the distance and dimension behavior in MU-006, and reflect shared patch state for all in-range listeners. Two speakers connected to the same source MUST act as two separate sound locations.
- **FR-010**: Every listener MUST have a persistent local master mute and 0–100% ModularCraft volume control, initially 100% of the conservative module output levels. Remote edits MUST NOT change these preferences. Minecraft master and the documented sound-category controls MUST also apply.
- **FR-011**: Module state, ownership, and valid connections MUST persist across saves and reloads under SS-004. Runtime envelope progress and manual gates MUST NOT be restored as a stuck held note. Restart and unload behavior MUST follow User Story 5.
- **FR-012**: Module placement, edits, cable changes, and activation MUST respect ownership, permission, interaction range, revision, and capacity rules. Accepted changes MUST become visible to other inspecting players within one second under SC-004 conditions; rejected changes MUST leave accepted state intact.
- **FR-013**: Feedback MUST identify missing inputs, invalid cable attempts, stale edits, unavailable modules, suspended playback, and capacity limits, with an action the player can take. Text and controls MUST remain usable without sound or color discrimination and support localization.
- **FR-014**: The five blocks MUST have finished, distinct module faces, readable port identities, and controls that match their function. Visual delivery MUST include editable source assets and asset provenance so appearances can be revised. Visual production methods belong in the plan; placeholders alone do not meet release acceptance.
- **FR-015**: Deliver an installable mod, a documented supported client/server compatibility matrix, installation and troubleshooting instructions, and both reproducible reference patches. Release acceptance requires packaged-mod singleplayer, dedicated-server, and two-client play with audible output, not merely a successful build.

### Musical and Signal Requirements *(mandatory review)*

- **MU-001 — Module contract**: All five modules are single-channel. Values below are product defaults, not choices of synthesis algorithm. Percentages are shown as percentages to players. Continuous controls accept values throughout their stated ranges without musical scale snapping; keyboard octave is an integer and piano keys use equal-tempered chromatic notes. Frequency and cutoff allow at least 0.1 Hz precision, time controls 1 ms precision, and percentages 1 percentage-point precision.

| Module | Adjustable controls: range; default | Ports and behavior |
| --- | --- | --- |
| Saw Oscillator | Frequency: 20–8,000 Hz; 220 Hz. Level: 0–100%; 50%. | One Pitch CV In accepting Keyboard CV Out and one Audio Out carrying a bipolar saw signal with a peak magnitude no greater than the selected level. It runs continuously while its patch is active. Zero level is silence. |
| Low-Pass Filter | Cutoff: 20–20,000 Hz; 5,000 Hz. Resonance: 0–95%; 0%. Envelope Amount: 0–100%; 0%. | One Audio In, one Cutoff Control In, one Audio Out. Higher cutoff passes more upper harmonics; higher resonance increases emphasis near cutoff. No self-oscillation is required. Unconnected audio is silence; unconnected control is zero. |
| Envelope Generator | Attack: 0.001–10 s; 0.010 s. Decay: 0.001–10 s; 0.200 s. Sustain: 0–100%; 70%. Release: 0.001–10 s; 0.300 s. Momentary manual Gate; released. | One Gate In accepting Keyboard Gate Out and one Envelope Out carrying a unipolar 0–100% control signal. The manual gate and redstone at the block drive the same ADSR behavior. It emits no audio. |
| Keyboard | Octave: integers 1–7; 4. Twelve momentary chromatic keys C–B; released. | CV Out (1 V/octave, 0 V = C4) and Gate Out (0/1). Concert tuning, last-note priority, retrigger on new active notes; no audio. See KB-001–KB-006 and MI-001–MI-006. |
| Amplifier/Speaker | Volume: 0–100%; 10%. Enabled: on/off; off. | One Audio In and one Gain Control In. Output is input audio × volume × control gain before final output protection and distance attenuation. An unconnected gain input means 100%; an unconnected audio input means silence. |

- **MU-002 — Connection contract**: Audio Out connects only to Audio In. Envelope Out connects only to Cutoff Control In or Gain Control In. Keyboard CV Out connects only to oscillator Pitch CV In; Keyboard Gate Out connects only to envelope Gate In. Each input accepts one cable; each output may fan out to four inputs, subject to patch limits. There is no implicit summing, role conversion, polyphony within a module, or feedback. Audio is a continuously varying bipolar signal nominally spanning −1 to +1; envelope control spans 0 to 1. Multiple independent oscillator blocks are allowed within capacity. Audio and envelope control MUST support continuous audible evolution rather than steps tied to world updates; exact processing rates belong in the plan.
- **MU-003 — Envelope contract**: The effective gate is active while manual gate OR redstone power OR connected keyboard Gate Out is active; redstone strength above zero counts as active. Its inactive-to-active transition starts attack from the current envelope level to 100% over the attack time, followed by decay to sustain over the decay time. Sustain lasts while the gate is active. Its active-to-inactive transition starts release from the current level to zero over the release time. Attack, decay, and release MUST each use a fixed linear progression from the stage's starting level to its target over the configured duration, including attack or release entered from an intermediate level. There is no selectable curve control in the MVP. A brief gate can therefore end attack early. A second gate source becoming active while manual or redstone is held does not retrigger. With those sources inactive, keyboard note onsets retrigger according to KB-002. The manual gate releases on key/button release, control-screen close, loss of interaction eligibility, or player disconnect. Time-control edits apply to the next entered stage; sustain edits update the held level through a smooth transition. Initial activation starts at zero and treats currently active redstone as a new gate.
- **MU-004 — Cutoff modulation**: Envelope Amount determines an upward cutoff sweep. Effective cutoff in Hz is base cutoff + (20,000 − base cutoff) × envelope fraction × amount fraction, bounded to the cutoff range. For example, base 500 Hz and amount 50% reaches 10,250 Hz at full envelope. Zero amount or a disconnected control input leaves the base cutoff unchanged. This audible mapping MUST be documented and tested.
- **MU-005 — Musical continuity**: The same accepted patch settings and gate sequence MUST produce the same musical progression within declared tolerances. Pitch, envelope stage duration, and sustained performance MUST remain independent of frame rate and normal world-update jitter. Shared changes have one accepted order; stale updates cannot rewind playback. Late listeners join the current progression without replaying history. Patch edits, volume changes, activation, disconnection, and local recovery MUST avoid unintended clicks; intentional saw-wave harmonics are preserved.
- **MU-006 — Spatial and output behavior**: A speaker has full distance gain through two blocks, then linearly attenuates to zero at 32 blocks, measured from its block center to the listener. Sound is confined to the same dimension and positioned relative to the listener's orientation. Walls do not occlude sound in this MVP. Combined ModularCraft output MUST remain finite and no greater than full scale, including overlapping speakers at maximum controls. At default controls RP-001 MUST have at least 6 dB of peak headroom. Local mute stops local sound within 100 ms; explicit speaker disable stops that speaker within 100 ms locally and 250 ms for connected remote listeners under SC-004 conditions.
- **MU-007 — Timing and lifecycle**: Normal playback MUST meet SC-003/SC-004. A real singleplayer pause freezes envelope progress and silences output; resume continues from the paused position with a smooth transition. Module control panels do not pause the world or playback. Shared worlds continue while a listener opens a menu. Local mute does not stop the shared timeline. Resource reload or audio-device recovery resynchronizes local output; world restart or patch chunk unload instead suspends the patch and clears transient gates as defined in User Story 5. Recovery MUST never play a backlog of missed gates.
- **MU-008 — Reference patches**: RP-001 is Saw Oscillator Audio Out → Filter Audio In → Amplifier/Speaker Audio In, with Envelope Out → Gain Control In. Use all defaults, enable the speaker, hold gate for two seconds, then release. The expected result is a 220 Hz saw note with an audible attack, decay to a held level, and fade to silence. Also demonstrate a sustained drone by explicitly removing the gain cable, an octave change to 440 Hz, the 8,000-to-500 Hz cutoff comparison, and resonance at 0% versus 75%. RP-002 adds Envelope Out → Cutoff Control In, sets base cutoff to 500 Hz and Envelope Amount to 50%, and uses attack 0.500 s, decay 0.500 s, sustain 30%, release 1.000 s. The expected result is a note that brightens and grows louder, settles darker and quieter while held, then fades. Guides MUST list every connection and setting; captures and audition records MUST identify which variation was used.
- **MU-009 — Measurable sound behavior**: Isolated oscillator frequency at 55, 220, 880, and 3,520 Hz MUST stay within 5 cents of the requested frequency over 60 seconds per setting. Envelope stage durations MUST be within the greater of 20 ms or 5% of the setting, and settled sustain within two percentage points of its setting. For uninterrupted attack, decay, and release stages with unchanged controls and a configured duration of one second, the envelope at 25%, 50%, and 75% of the stage duration MUST be within two percentage points of the straight-line progression between the starting and target levels. Include attack and release starting from intermediate levels in these checks. With a 220 Hz saw at fixed level and zero resonance, filter output energy between 2,000 and 8,000 Hz at cutoff 500 Hz MUST be at least 12 dB below that at cutoff 8,000 Hz. Positive resonance MUST measurably increase the near-cutoff response relative to zero resonance. The plan MUST add justified measurement procedures and thresholds for aliasing, unwanted DC offset, transitions, and long-session timing before implementation; measurements and in-game listening are both required.

### Safety, Stability and Compatibility *(mandatory)*

- **SS-001 — Authority and permission**: The world owns accepted patch state. A player owns the modules they place; only that owner or a server operator can edit, connect, disconnect, enable, or break them through mod interactions, additionally respecting host-world protections. Connections between different owners are rejected in this MVP, including operator-created connections. Each port selection or module edit requires the acting player to be in the same dimension, within six blocks of that target's center, and the target to be loaded; completing a cable revalidates both endpoints and ownership. Ordinary destructive world events may remove modules and MUST trigger normal cleanup. Two competing valid edits have one accepted order, and a stale edit cannot overwrite the newer value. A dedicated server MUST run without an audio device or display.
- **SS-002 — Invalid or excessive requests**: Invalid values, unknown identifiers, malformed requests, stale state, and forbidden edits MUST be rejected before changing accepted state. Player-facing continuous editing MAY coalesce intermediate changes, but sustained requests above 20 changes per second per player MUST be bounded with visible throttling and a current-state refresh. The plan MUST define aggregate traffic, stored-data size, queue, and work limits before implementation. Error reporting MUST itself be bounded; request floods MUST NOT freeze gameplay or emit unbounded logs.
- **SS-003 — Capacity and overload**: A patch is the connected set of modules; an isolated module is a one-module patch. Each patch permits at most 16 modules, 24 cables, and two speakers. Each player may own up to 64 placed modules and 128 cables; a world permits up to 256 placed modules and 512 cables across all dimensions, including unloaded modules. At most four patches and eight speakers may be active in one world; a patch is active when at least one of its speakers is enabled and all its modules are loaded. Enabling an additional speaker in an already active patch counts toward the speaker limit. Placement, connection, and activation beyond limits MUST be rejected with the applicable count; existing valid work is preserved. On unexpected playback overload the affected listener's output is silenced with a recovery message, without changing shared compositions or stalling the world. Further internal resource budgets and qualification hardware MUST be fixed in the plan.
- **SS-004 — Persistence and compatibility**: Saved module identities, ownership, settings, and cable endpoints MUST remain stable across supported save/reload operations; position alone cannot identify a replacement module. Validation failure MUST preserve original stored data and isolate the affected patch. Unknown or newer formats MUST be refused with a recovery message rather than silently retuned or overwritten. No earlier public save format exists for the MVP; future incompatible formats or musical changes require explicit migration or safe refusal. Speaker enabled state and momentary gate state are deliberately transient; user-facing documentation MUST explain restart behavior.
- **SS-005 — Lifecycle cleanup**: Breaking a module removes its cables; unloading a chunk preserves cabling and suspends its whole connected patch. Local obsolete playback MUST cease within 250 ms after a lifecycle event is observed locally. Disconnect, dimension change, world exit, resource reload, and audio-device loss MUST release associated playback resources or recover them without duplicate voices or retained worlds. After 20 repeated unload/reload and reconnect cycles, active playback/resource counts MUST return to their idle baselines and no old sound may remain. Chunk loading MUST NOT be forced to sustain instruments. State resynchronization MUST complete within one second under SC-004 conditions once needed data and the audio device are available.
- **SS-006 — Listener control and diagnostics**: Local mute MUST remain available even when a patch fails or the player lacks edit permission. Controls MUST show accepted values, meaningful units, and ranges, support precise keyboard operation where the host permits it, and use localizable text. Every rejected edit or suspended patch MUST identify the affected target and a recovery action. Players MUST be able to distinguish a disabled speaker, missing audio, released envelope, out-of-range source, and local mute without relying solely on hearing.
- **SS-007 — Supported installation and assets**: The first plan MUST select and document one exact Minecraft/loader compatibility target and supported desktop environments, using current official documentation; multi-loader and multi-version support are outside this MVP. Both participating clients and server install compatible ModularCraft versions. No external account, service, telemetry, internet audio stream, or audio hardware attached to the server is required. The mod MUST work after installation without runtime downloads. Distributed assets and dependencies MUST have recorded provenance and compatible redistribution terms. Installation, dependency inventory, build provenance, and release checks follow the constitution.

### Test Driven Development and Evidence *(mandatory)*

**TDD is explicitly required for every new or changed executable behavior.** Tasks MUST include behavior/regression tests and observed expected failure before implementation, followed by green and refactor validation. Test omissions cannot be inferred from a brief user prompt or generic command wording that makes tests optional.

- **Behavior tests**: Cover module contracts, connection compatibility, envelope stages, modulation, timing, and output limits (FR-003–FR-009, MU-001–MU-009); use affected host integration tests for placement, interactions, redstone, ownership, networking, saves, and lifecycle (FR-001–FR-005, FR-010–FR-013, SS-001–SS-006). Verify visual/inventory/guide coverage against FR-014–FR-015 in packaged play.
- **Negative/property tests**: Exercise malformed and non-finite input, stale and unauthorized edits, graph cycles, occupied inputs, distance rules, split/merge capacity, per-player/world quotas, excessive requests, corrupt/unknown saves, and bounded output at parameter extremes. Every rejected edit MUST preserve accepted state.
- **Determinism**: Use controlled time and event ordering, isolated test worlds/state, and declared numeric tolerances. Repeated reference-patch runs with the same settings and gate sequence MUST preserve pitch and envelope progression; exact waveform equality across different devices is not required.
- **Host acceptance**: Separately qualify packaged singleplayer, a dedicated server without a display/audio device, and two native clients. Record owner/operator permissions, positional listening, local mute, joining during notes, reconnect, world restart, resource reload, and audio-device recovery. Use the declared compatibility target and hardware.
- **Musical acceptance**: Measure MU-006/MU-009, SC-003/SC-004, output bounds, and the plan's additional sound-quality thresholds. Audition both reference patches in Minecraft, including starts, stops, all controls, cable changes, redstone gates, multiple speakers, and overload recovery. Listen for unintended clicks, sustained crackling, dropouts, stuck notes, and discrepancies between advertised and audible behavior.
- **Evidence**: Record revision/artifact, environment, workload, duration, commands or manual procedure, expected/actual result, and PASS/FAIL/BLOCKED/PENDING. Unit tests, headless host tests, dedicated-server tests, native gameplay, multiplayer, audio capture, human listening, performance runs, and hosted CI are separate evidence classes. Retain reference settings and capture provenance.
- **Non-behavior scope**: This invocation creates specification documents only. Validate links, structure, requirement consistency, placeholders, and whitespace now; no executable or audio acceptance is claimed. The future MVP contains executable behavior and requires the full TDD and release evidence above.

BLOCKED/PENDING evidence MUST remain visible; automated tests do not establish listening, native gameplay, or hosted CI acceptance.

### Key Entities *(include if feature involves data)*

- **Module Block**: A placed, uniquely identified instrument component with a stable type, owner, dimension/position, accepted revision, and bounded parameter values. Its identity/settings persist until removal; replacing its position creates a new identity.
- **Port**: A named module endpoint with a direction, signal role, single-channel range, and defined unconnected behavior. Port identity remains stable for saved cable references.
- **Patch Cable**: A persistent, directed connection between compatible endpoints of same-owner modules. It has no independent sound, enforces distance/fan-out/capacity rules, and is removed when explicitly disconnected or either endpoint is broken.
- **Patch**: The connected set of modules and cables for activation, capacity, and suspension. It can split or merge through accepted edits; ownership remains consistent. It is not an independently required fifth block.
- **Performance State**: The current effective gates, envelope stages, and shared musical progression. It has an ordered current state for listeners but is not saved as a held performance across world restarts.
- **Listener Preferences**: Persistent local mute and volume choices, independent of module ownership and remote player actions.
- **Reference Patch**: A reproducible set of blocks, connections, values, actions, and expected sound used by the guide and acceptance evidence.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In a fresh supported Creative installation, at least four of five first-time testers using only the supplied guide MUST place, wire, and play RP-001 within ten minutes, then release its note and locate local mute without assistance.
- **SC-002**: All five module types MUST be obtainable and visually distinguishable; every specified control, port, default, and connection rule MUST pass its acceptance cases. All three reference patches MUST produce the described audible results with objective MU-009 checks and a recorded in-game listening pass.
- **SC-003**: During a ten-minute singleplayer performance with one RP-001 patch, at least 95% of 100 measured gate/parameter actions MUST begin their audible response within 100 ms of input, excluding deliberately configured envelope duration. There MUST be zero stuck notes or unintended playback interruptions longer than 100 ms. Local mute MUST meet its 100 ms bound.
- **SC-004**: On a dedicated server with two clients, round-trip network delay at most 50 ms, delay variation at most 10 ms, and no induced packet loss, at least 95% of 100 gate events/control edits MUST affect both listeners within 150 ms of the originating input. Audible envelope onset between the clients MUST differ by no more than 50 ms for at least 95% of those events. Joining/resynchronizing MUST meet the one-second bound without restarting other listeners. Hardware, measurement uncertainty, and tested conditions MUST be recorded.
- **SC-005**: In a 30-minute run with four active patches, each containing 16 modules, 24 valid cables, and two speakers, two in-range clients MUST continue to meet SC-004 timing and output bounds. Each maximum-load patch contains one oscillator, ten filters in series, three envelopes, and two speakers. Audio passes through all ten filters and fans out to both speakers (12 audio cables); each envelope fans out to four distinct control inputs, collectively covering the ten filters and two speakers (12 control cables). All envelope amounts are 50%, speakers are enabled, and gates repeatedly exercise attack, sustain, and release. Against the same world/hardware without active synthesis, the 95th-percentile frame duration MUST worsen by no more than 20%, and the world's 95th-percentile update duration MUST remain below 50 ms. There MUST be zero crashes, stuck notes, or audible interruptions longer than 100 ms. The plan MUST fix placement, remaining control values, gate schedule, and hardware baseline before implementation.
- **SC-006**: Across 20 save/restart and lifecycle cycles, 100% of valid persisted module settings, owners, and surviving cable endpoints MUST be restored, with restart/suspension rules applied. All active playback counts MUST return to baseline after cleanup. Every invalid, unauthorized, stale, or over-limit case MUST preserve accepted state and provide the specified recovery feedback.
- **SC-007**: A listener MUST hear positional attenuation at sampled distances of 2, 8, 16, and 24 blocks, silence at 32 blocks and beyond or in another dimension, and independent local mute/volume. Packaged singleplayer, dedicated-server, two-client, asset review, and in-game listening evidence MUST all pass before calling the MVP fully functional.

Criteria are acceptance targets, not measured results. The plan supplies implementation choices, reproducible measurement methods, and benchmark environments. Completion requires applicable evidence; passing a build alone does not demonstrate a stable or musical feature.

## Assumptions

- **Access and scope**: Creative-mode construction is sufficient for the first MVP. Survival crafting recipes, progression/economy, power consumption, block-transport automation, and public distribution are separate work. All five named modules and multiplayer audio remain mandatory; story priority only determines implementation order.
- **Musical interpretation**: “Enveloper generator” means an ADSR envelope generator with fixed linear attack, decay, and release, as confirmed during clarification. “Filter” means one resonant low-pass filter. “Amplifier/speaker” is one combined block with envelope-controlled gain and positional output. Manual/redstone gating, cutoff modulation, control ranges, and conservative defaults are explicit proposed MVP product choices.
- **Modularity**: A patching tool and visible cables are supporting interaction assets, not additional synth module block types. The MVP permits multiple instances of the five types within limits. Sequencers, LFOs, other waveforms/filter modes, effects, mixers, recording/export, preset libraries, and feedback synthesis are excluded. MIDI input is defined in the extension below.
- **Access policy**: Owners and server operators may edit modules; other players may listen. A share/ACL editor and integration with third-party claim systems are excluded; built-in host protection rules still apply. Combining modules from different owners is not required.
- **Compatibility**: No current implementation plan or runtime sources existed when this spec was written. Exact Minecraft/loader/toolchain and sound-production choices are planning decisions, not unresolved product requirements. The first plan must resolve them and numeric engineering budgets before executable implementation.
- **Control interaction**: Module adjustments use right-click control panels, with in-world ports for cable patching. Direct adjustment of knobs on block faces is outside the MVP; depicted knobs may communicate module function or state.
- **Visuals**: Finished module art and editable provenance are in scope. The user's authorization to use Blender and image generation is available for asset production as needed; generating artwork is not required to complete this specification. Detailed visual style may be chosen during planning while preserving five recognizable module identities.


### Keyboard extension (approved 2026-11-04)

- **KB-001 — Instrument**: Fifth module `keyboard`, matching chassis, front sockets and patch-tool workflow. CV Out and Gate Out; no audio. Non-pausing right-click panel: twelve piano keys C–B, octave −/+ (integer 1–7, default 4), local audio and Done. Computer keys A W S E D F T G Y H U J map to chromatic semitones; left/right changes octave while piano is focused. Mouse drag supports black-key hit priority and releases outside the piano. Ignore key repeat; retain independent mouse/computer holds.
- **KB-002 — Performance**: Monophonic most-recent-held input wins. New active notes and return to an earlier held note retrigger linear attack from the current envelope level. Changing octave transposes held GUI notes and retriggers; absolute MIDI notes are unaffected. Manual/redstone sources retain priority over keyboard retriggering while held; all three gate sources are OR-combined. Gate is 0/1. Last pitch remains after release; fresh activation resets pitch to C in selected octave.
- **KB-003 — Pitch**: Pitch CV is 1 V/octave, 0 V = C4; equal temperament, A4 = 440 Hz. Pitch input overrides oscillator Frequency while connected and restores the saved Frequency when disconnected. Preserve phase with 10 ms pitch smoothing. Typed ports prohibit implicit pitch/gate/control conversions; existing cable and capacity limits apply.
- **KB-004 — Authority/lifecycle**: Server validates owner/operator, range, loaded module, revision, inspection session, monotonically increasing input sequence and existing traffic budgets. First eligible note obtains exclusive session lease; renew at 200 ms, expire at 750 ms. Ordinary releases preserve preceding queued presses and bypass edit throttling. Emergency cleanup, overload, focus loss, close, unload, permission/range loss, dimension change and disconnect clear held performance; stale sessions cannot revive it. Short accepted taps preserve receive-time spacing on the sample timeline (minimum one millisecond), using the existing 40 ms lead. Late recovery enters current state without replaying notes.
- **KB-005 — Compatibility**: Persist octave and cables, never held inputs/gates or last played pitch. Keep world-save version 1 and unknown-record preservation. Network and snapshot version 3 (MIDI extension) require matching clients/server. No new dependencies.
- **KB-006 — RP-003**: RP-001 with all default settings, plus Keyboard CV → Oscillator Pitch In and Keyboard Gate → Envelope Gate In; keyboard octave 4 and no envelope redstone. Press H for A4 (440 Hz), overlap A/S, return to A, transpose while held, release and hear a tail at the retained pitch. Disconnect CV and confirm the saved 220 Hz oscillator setting returns. Validate with the existing SC-003/SC-004 timing limits; native multiplayer and listening need separate evidence.

Velocity expression, pitch bend, polyphony, sequencers and recording remain excluded. MIDI sustain is specified below.


### MIDI input extension (approved 2026-11-04)

- **MI-001 — Activation/preferences**: MIDI is Off initially. The editable keyboard panel
  offers device selection, Refresh, All/1–16 channel selection (default All), localized
  status and Release Notes. Remember device descriptor/name/channel locally. Restore only
  unique matches; missing/ambiguous inputs require selection. Accept hardware/virtual input
  ports exposed by Java Sound, excluding internal synthesizers/sequencers. Input requires
  the panel to remain open and the Minecraft window focused.
- **MI-002 — Performance**: Play absolute MIDI notes 0–127 with A4=440 Hz. Panel octave
  affects GUI inputs only. Most recent held/sustained source wins across all inputs;
  fallback and repeated MIDI onsets retrigger according to KB-002. Note On velocity zero
  is Note Off; other velocity values do not affect gate level. One Note Off releases a
  repeated channel+pitch source. Retain KB-003 smoothing and released pitch.
- **MI-003 — Sustain/reset**: CC64 values >=64 sustain released MIDI notes on that channel;
  values <64 release latched notes. GUI input is unaffected. CC123 releases physical MIDI
  holds, respecting sustain; CC121 resets sustain while retaining physical holds; CC120
  clears channel holds/pedal. Release Notes clears every local source and pedal. Gate
  releases retain downstream envelope release tails; local mute remains available.
- **MI-004 — Lifecycle/bounds**: Close, focus loss, world/dimension exit, resource reload,
  eligibility loss and disconnect clear input, sustain and queued messages. Device/channel
  changes clear MIDI sources only. One worker/four pending tasks, one device, 64 discovered
  entries, a 256-event client queue, 64 events drained/tick, 13 GUI + 2,048 MIDI holds.
  Native connection lifetimes are isolated in at most one headless Java 21 helper (32 MiB
  heap, 64 MiB metaspace, separate 256-event ingress queue, fixed four-byte IPC frames).
  The same worker polls input between client ticks. Closing explicitly closes native
  resources and reaps the helper; a blocked close is terminated within the operation budget.
  No replacement opens before the old process exits. Two-second
  provider deadline, one-second device checks. Stale callbacks/results never revive notes;
  failures require Refresh/reselection, overflow invalidates the session and requires reopen.
  A stalled provider never creates additional workers or blocks game/audio threads.
- **MI-005 — Authority/compatibility**: Relative/absolute pitch mode is explicit in bounded
  v3 packets; v3 snapshots permit 0–127. Reject old peers and malformed modes/ranges.
  Retain all KB-004 authorization, ordering, timing and traffic limits; ignore device
  timestamps. Save schema stays v1; no MIDI holds, pedals, device handles or pitch mode
  are saved in worlds. No dependencies or platform claims are added.
- **MI-006 — Qualification**: TDD covers mixed sources, full range, channels, sustain,
  malformed messages, device failures/timeouts, queue overflow, stale callbacks, and 20
  cleanup cycles. Packaged GameTests cover authoritative MIDI pitch, ordering, permissions,
  protocol rejection and persistence. Extend RP-003 with native synthetic MIDI, screenshots,
  real-controller focus/unplug/pedal tests and SC-003/SC-004 measurements. Synthetic input
  does not establish hardware, listening or multiplayer acceptance.
