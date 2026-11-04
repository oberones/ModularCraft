package com.malevolentgods.modularcraft.domain;

/**
 * Client-owned note draft; never persisted. Scientific octaves 0..8 use A4 = 440 Hz. Only a
 * matching, newer server revision acknowledges a submitted selection.
 */
public final class OscillatorNote {
  private static final String[] NAMES = {
    "C", "C#/Db", "D", "D#/Eb", "E", "F", "F#/Gb", "G", "G#/Ab", "A", "A#/Bb", "B"
  };
  private int note, submitted = -1;
  private long submittedRevision;
  private boolean dirty;

  public OscillatorNote(double frequency) {
    accept(frequency, 0);
  }

  public static String name(int semitone) {
    if (semitone < 0 || semitone > 11) throw new IllegalArgumentException("note");
    return NAMES[semitone];
  }

  public int semitone() {
    return note % 12;
  }

  public int octave() {
    return note / 12 - 1;
  }

  public double frequency() {
    return KeyboardState.frequency(note);
  }

  public boolean valid() {
    return frequency() >= Parameter.FREQUENCY.min && frequency() <= Parameter.FREQUENCY.max;
  }

  public boolean dirty() {
    return dirty;
  }

  public void select(int semitone, int octave) {
    if (semitone < 0 || semitone > 11 || octave < 0 || octave > 8)
      throw new IllegalArgumentException("note");
    note = 12 * (octave + 1) + semitone;
    dirty = true;
  }

  /** Returns the exact Hz value for the existing Frequency edit; does not accept it locally. */
  public double submit(long revision) {
    double frequency = Parameter.FREQUENCY.validate(frequency());
    dirty = true;
    submitted = note;
    submittedRevision = revision;
    return frequency;
  }

  public void accept(double frequency, long revision) {
    Parameter.FREQUENCY.validate(frequency);
    if (submitted >= 0 && revision > submittedRevision) {
      if (note == submitted && frequency == KeyboardState.frequency(submitted)) dirty = false;
      submitted = -1;
    }
    if (!dirty) {
      int nearest = (int) Math.round(69 + 12 * Math.log(frequency / 440) / Math.log(2));
      note = Math.clamp(nearest, 16, 119); // E0..B8 are the whole notes inside 20..8,000 Hz.
    }
  }
}
