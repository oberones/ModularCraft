package com.malevolentgods.modularcraft.domain;

/** Transient concert pitch (MIDI note number) and normalized gate; never world-saved. */
public record KeyboardState(int note, boolean gate) {
  public KeyboardState {
    if (note < 0 || note > 127) throw new IllegalArgumentException("keyboard_note");
  }

  public static int note(int octave, int semitone) {
    Parameter.OCTAVE.validate(octave);
    if (semitone < 0 || semitone > 11) throw new IllegalArgumentException("keyboard_note");
    return 12 * (octave + 1) + semitone;
  }

  public static double volts(int note) {
    return (note - 60) / 12.0;
  }

  public static double frequency(int note) {
    return 440 * Math.pow(2, (note - 69) / 12.0);
  }
}
