# Turn your Minecraft world into a synth studio

Build the instrument. Shape the sound. Find your groove.

**ModularCraft brings modular synthesis into Minecraft**, with nine musical blocks you can connect into your own playable instruments. Patch together oscillators, sculpt their sound with filters, build rhythmic patterns, and bring everything to life through speakers in your world.

Create a pulsing bass, a thick detuned lead, a slowly evolving drone, or interlocking rhythms. Play with your mouse or computer keyboard—or connect a MIDI controller and bring your real keys into Minecraft.

Every patch starts with a few blocks. Where it goes is up to you.

## Nine blocks. Endless experiments.

- **Saw Oscillator** — Start with a bright, harmonically rich tone. Spread three voices around the center pitch with adjustable unison detune for a thicker sound.
- **Square Oscillator** — Explore hollow square tones and sharper pulse sounds with adjustable pulse width. Tune by frequency or choose a note and octave.
- **Low-Pass Filter** — Sweep from dark and mellow to bright and resonant. Modulate cutoff and resonance to give your sound movement.
- **Envelope Generator** — Shape each note with attack, decay, sustain, and release. Create quick plucks, gentle swells, and lingering fades.
- **Amplifier / Speaker** — Turn your patch into sound in the world. Control its volume and connect an envelope to shape its loudness.
- **Keyboard** — Play notes using an on-screen piano, your computer keyboard, or MIDI input, including sustain-pedal support.
- **Euclidean Sequencer** — Build four independent rhythmic patterns by spreading hits across up to 16 steps. Adjust their length, rotation, and mute state, then send triggers and clock signals to other modules.
- **LFO** — Add repeating motion with sine, triangle, rising saw, falling saw, and square waves. Run freely or synchronize to the sequencer.
- **Audio Mixer** — Blend up to four audio sources with individual levels, mute, solo, a master level, and live meters.

## Follow the cables

Grab the **Patching Tool**, click an output, then click a compatible input. Visible cables connect your instrument right in the world.

Combine a saw and square oscillator through a mixer. Send the mix through a resonant filter. Let the sequencer trigger an envelope, and use a clocked LFO to sweep the filter in time. Add the keyboard to choose the pitch.

Experiment with modulation beyond volume and tone: control envelope stages, reverse modulation with signed depth controls, or trigger an envelope with redstone.

Right-click a block to open its controls. Adjust sliders, enter exact values, and hear your instrument change while the world keeps running.

## Build something your friends can hear

ModularCraft supports shared performances: nearby players hear the same patch, with sound fading as they move away from its speakers.

Your blocks, settings, and cables persist with the world. Ownership protects instruments from other players’ edits, while each listener has independent local volume and mute controls.

**M** mutes your own audio immediately. **K** opens local audio settings. Both keys can be rebound.

## Your first sound

1. Open the **ModularCraft Instruments** Creative inventory tab.
2. Place a Saw Oscillator, Low-Pass Filter, Envelope Generator, and Amplifier / Speaker.
3. Use the Patching Tool to connect:
   - **Saw Audio Out → Filter Audio In**
   - **Filter Audio Out → Speaker Audio In**
   - **Envelope Out → Speaker Gain In**
4. Put away the tool, open the speaker, and enable it.
5. Open the envelope and hold **Hold Gate**.

You have a playable synth. Turn the filter cutoff, change the envelope, or add a sequencer and start building a rhythm.

To remove a cable, **Shift-right-click its destination input** with the Patching Tool.

## Requirements and current scope

- **Minecraft Java Edition 1.21.1**
- **NeoForge 21.1.218**
- **Java 21**
- For multiplayer, install the same mod version on the server and every client.
- Blocks are currently available through Creative mode; survival recipes are not included.
- The Keyboard is currently monophonic. MIDI input works while its panel is open and Minecraft is focused.
- The sequencer creates rhythms; melodic sequencing and audio recording/export are not currently included.
- Speakers restart disabled after world reload. Enable them again when you return to your patch.

ModularCraft is in active development. macOS is the initial playback target; Windows/Linux playback and full multiplayer timing qualification remain pending. Back up your worlds before upgrading.

## Start patching

A few cables can turn a simple tone into something unexpected.

**Build a synth that belongs in your world—and make it sound like you.**

[Documentation and source code](https://github.com/oberones/ModularCraft)
