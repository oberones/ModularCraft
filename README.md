# ModularCraft

**Build a modular synthesizer inside Minecraft.** Place instruments as blocks, connect
their ports with cables, and turn oscillators, filters, and rhythms into a synth you can
play in your world. Nearby players hear the same shared performance, with their own
volume and mute controls.

![Four articulated synth voices feeding one Mixer and Speaker](docs/images/four-voice-amp.png)

Make a pulsing bass line, a slowly shifting drone, or a rhythm you can play over with a
keyboard. Sound is synthesized live—no prerecorded loops, accounts, external audio
services, or extra audio software are required. An optional MIDI controller can play
the Keyboard block too.

## Meet the blocks

| Sound sources | Tone and motion | Playing and output |
|---|---|---|
| **Saw Oscillator**<br>![Saw Oscillator block with its labeled front and ports](docs/images/saw-oscillator.png)<br>A bright starting point for basses, leads, and drones. Add unison detune for a thicker sound. | **Low-Pass Filter**<br>![Low-Pass Filter block with cutoff and resonance ports](docs/images/low-pass-filter.png)<br>Soften a bright tone or add a resonant edge. Modulate cutoff and resonance to make it move. | **Keyboard**<br>![Keyboard block with pitch and gate outputs](docs/images/keyboard.png)<br>Play notes with your mouse, computer keyboard, or a MIDI controller. Supplies pitch and gate signals. |
| **Square Oscillator**<br>![Square Oscillator block with its pulse-wave front panel](docs/images/square-oscillator.png)<br>Square and pulse tones with adjustable pulse width, from a hollow sound to a thinner buzz. | **Envelope Generator**<br>![Envelope Generator block with its ADSR diagram and control ports](docs/images/envelope-generator.png)<br>Shape how notes begin, sustain, and fade with attack, decay, sustain, and release. | **Euclidean Sequencer**<br>![Euclidean Sequencer block with four circular rhythm displays](docs/images/euclidean-sequencer.png)<br>Create four rhythmic trigger patterns by spreading hits across steps. Clock other modules from the same rhythm. |
| **Audio Mixer**<br>![Audio Mixer block with four numbered inputs and an audio output](docs/images/audio-mixer.png)<br>Blend up to four audio sources. Adjust each level, mute parts, or solo a combination. | **LFO**<br>![LFO block with waveform display and control output](docs/images/lfo.png)<br>Add repeating motion with five wave shapes. Run freely or synchronize to the sequencer's clock. | **Amp**<br>![Amp panel with Level, ADSR, and Drone](docs/images/amp-panel.png)<br>Shape each voice with built-in ADSR, then send Audio Out to a mixer or Speaker. |

**Speaker**<br>![Speaker panel](docs/images/speaker-panel.png)<br>Emit spatial audio from a mixer or Amp, with remembered enable and volume controls.

The **Patching Tool** joins the blocks: right-click an output port, then a compatible
input. Cables carry audio, modulation, pitch, or timing signals. Right-click a block
without the tool to open its controls.

In the opening screenshot, four oscillators each pass through a filter and Amp into the
Mixer, then one Speaker. The sequencer triggers each Amp independently.
[Build this four-voice synth](docs/user-guide.md#four-voice-amp-patch-rp-008).

## Install and start playing

You need **Minecraft Java Edition 1.21.1**, **NeoForge 21.1.218**, and **Java 21**.

1. Get the installable `modularcraft-<version>.jar` from
   [GitHub Releases](https://github.com/oberones/ModularCraft/releases), or
   [build it from source](docs/development.md). The `-sources.jar` is not the playable mod.
2. Put the JAR in your Minecraft instance's `mods` folder. For multiplayer, install the
   same version on the server and every player's client.
3. Enter a world in Creative mode and open the **ModularCraft Instruments** inventory
   tab. All ten blocks and the Patching Tool are there. Survival recipes are not yet included.

ModularCraft is a **development candidate**. macOS is the initial playback target;
Windows/Linux playback is not yet qualified. See the [validation ledger](validation/ledger.md)
for current testing and outstanding release checks. Back up worlds before upgrading.

**Speaker** is now a separate output block. Existing combined blocks become Amps; add a
Speaker and wire Amp Audio Out to it. Back up your world first; see [migration instructions](docs/user-guide.md#upgrading-existing-patches).

## Your first instrument

Start with a **Saw Oscillator**, **Low-Pass Filter**, **Amp**, **Speaker**, and
**Patching Tool**. Place the four blocks two blocks apart, with their fronts facing you.

1. Connect **Saw Audio Out → Filter Audio In**, then **Filter Audio Out → Amp Audio In**.
2. Connect **Amp Audio Out → Speaker Audio In**.
3. The Speaker starts enabled at Volume 10%. Open Amp and hold **Hold Gate**, then release.
   Its built-in ADSR shapes the note. Change the filter cutoff to change its tone.
4. For a sustained tone, turn on **Drone** in Amp. Turn it off to return to gated notes.

For four sequencer voices, wire each trigger to its own Amp Gate In, route four
oscillators through those Amps into the Mixer, then connect Mixer Audio Out to one or
two Speakers. Amps do not consume speaker slots. See the [four-voice patch](docs/user-guide.md#four-voice-amp-patch-rp-008).

**Press M to mute your own sound immediately.** Press it again to unmute. Open local
audio settings with the **ModularCraft Audio** button on the title screen, pause menu,
or module panels. Minecraft's Master and Jukebox/Note Blocks volume sliders also apply.
You can rebind M in Minecraft's controls. Local mute does not stop the music for other players.

To remove a cable, hold the Patching Tool and **Shift-right-click its destination input**.
An input accepts one cable; an output can feed four destinations. Speakers remember their on/off setting and resume when the whole patch is loaded and
playback capacity is available. Sequencers still need Start after reloading.

## Keep exploring

- [User guide](docs/user-guide.md): detailed controls, playable patches, MIDI, saving,
  multiplayer ownership, and troubleshooting.
- [All-ten-block synth](docs/user-guide.md#showcase-all-ten-blocks-together): the wiring
  and settings used in the opening screenshot.
- [Development guide](docs/development.md): build, run, test, and capture screenshots.
- [Architecture](docs/architecture.md) and [validation ledger](validation/ledger.md):
  how the mod works and what has been tested.
