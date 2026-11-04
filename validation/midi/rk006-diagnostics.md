# RK006 input investigation — 2026-11-04

Environment: macOS, Eclipse Adoptium 21.0.12.1+1 aarch64, Minecraft 1.21.1,
NeoForge 21.1.218. Source: `b4f7377` plus the timeout regression fix. No dependency
or Java pin changes. The user's live client was not restarted or modified.

## Physical input capture: PASS at the Java Sound boundary only

The player confirmed a USB RK006, its `IN_ALL` omni-input endpoint, and channel 1.
A standalone Java Sound receiver selected that transmitting port by name, opened
the device/transmitter, accepted ShortMessage input for 90 seconds, and explicitly
closed its resources. The player played during this capture:

```text
NOTE command=144 channel=1 pitch=64 velocity=88 class=com.sun.media.sound.FastShortMessage
NOTE command=128 channel=1 pitch=67 velocity=73 class=com.sun.media.sound.FastShortMessage
RESULT messages=191 noteMessages=191 channelMask=1
```

This establishes that the endpoint and channel can deliver real notes to this JDK.
An earlier empty 45-second capture is inconclusive: the player missed that window.
Neither capture establishes audible MIDI playback inside Minecraft or pedal behavior.

## Live client connection: FAIL

The panel showed “Connecting MIDI input”. Two read-only `jcmd Thread.print`
samples, about 116 seconds apart, showed the same MIDI worker blocked at:

```text
com.sun.media.sound.MidiInDevice.nStop(Native Method)
com.sun.media.sound.MidiInDevice.implClose(MidiInDevice.java:79)
com.sun.media.sound.AbstractMidiDevice.doClose(AbstractMidiDevice.java:180)
com.sun.media.sound.AbstractMidiDevice.close(AbstractMidiDevice.java:147)
com.malevolentgods.modularcraft.client.JavaMidiBackend$Handle.close(JavaMidiBackend.java:125)
com.malevolentgods.modularcraft.client.MidiInput.closeConnection(MidiInput.java:239)
com.malevolentgods.modularcraft.client.MidiInput.reconcile(MidiInput.java:202)
```

These are pre-fix source line numbers. The path is the device-health failure close;
the dump does not establish why that earlier health check failed. Three Java Sound
MIDI input threads were also in native `nGetMessages`. The game/render thread was
not waiting for MIDI. Native close cannot safely be cancelled from the game thread.

## Isolated hardware lifecycle: FAIL resource baseline

Compiled the production MidiInput and JavaMidiBackend with a small standalone
harness. Selected the unique `IN_ALL` descriptor on channel 1, waited for ready,
ticked for 1.1 seconds (including availability discovery), disabled the target,
and ticked for 1.2 seconds for close completion. Repeated 20 times. The standalone
process exited afterward; no probe was left running.

All 20 cycles completed without a reported operation timeout. However, the count
of live `Java Sound MidiInDevice Thread` threads was 0 initially and 1 after every
close, including cycle 20. Thus this is **not** a resource-lifecycle pass. The
multi-minute native close hang in Minecraft was not reproduced by this short probe.
No notes were measured during these reopen cycles, so reconnect delivery is unproven.

## Timeout UI regression: PASS after red–green

`stalledWorkerKeepsTimeoutAcrossRefreshFocusAndSelectionChanges` holds a provider
job pending with a controllable executor/clock. After its two-second deadline,
20 Refresh/focus/channel changes must preserve the timeout, retain only one pending
operation, reject stale input, and avoid replacement opens. A late completion must
close the old resource; an explicit subsequent retry can reconnect.

Before the fix, `make test TEST='*MidiInputTest'` failed this assertion because the
status became `midi_connecting` (10 tests, 1 failure). After the fix, all 10 pass.
Full `make verify` passes 72 JUnit, 12 packaged GameTests, 12 launcher tests,
formatting, Checkstyle, PMD, compilation and artifact inspection.

The fix retains timeout status across restart requests while the worker is still
blocked. Localized guidance now mentions restarting Minecraft if Refresh cannot
recover. It does **not** repair the native Java Sound hang/thread retention.

Self-review: no additional workers, devices, queues, or dependencies; generations
still invalidate callbacks and queued notes. Late completion cannot revive a failed
performance. Device/channel preferences, GUI note behavior, protocols and world
persistence are unchanged. Native in-game MIDI listening, sustain, unplug/reconnect,
20-cycle resource qualification and two-client timing remain PENDING/FAIL as above.
