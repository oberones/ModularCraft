package com.malevolentgods.modularcraft.domain;

public enum Parameter {
  FREQUENCY(20, 8000, 220, "Hz"),
  LEVEL(0, 100, 50, "%"),
  CUTOFF(20, 20000, 5000, "Hz"),
  RESONANCE(0, 95, 0, "%"),
  AMOUNT(0, 100, 0, "%"),
  ATTACK(.001, 10, .010, "s"),
  DECAY(.001, 10, .200, "s"),
  SUSTAIN(0, 100, 70, "%"),
  RELEASE(.001, 10, .300, "s"),
  VOLUME(0, 100, 10, "%"),
  OCTAVE(1, 7, 4, ""),
  DETUNE(0, 100, 0, "cents"),
  TEMPO(30, 300, 120, "BPM"),
  A_STEPS(1, 16, 16, ""),
  A_HITS(0, 16, 4, ""),
  A_ROTATION(0, 15, 0, ""),
  A_MUTE(0, 1, 0, ""),
  B_STEPS(1, 16, 16, ""),
  B_HITS(0, 16, 5, ""),
  B_ROTATION(0, 15, 0, ""),
  B_MUTE(0, 1, 0, ""),
  C_STEPS(1, 16, 16, ""),
  C_HITS(0, 16, 7, ""),
  C_ROTATION(0, 15, 0, ""),
  C_MUTE(0, 1, 0, ""),
  D_STEPS(1, 16, 16, ""),
  D_HITS(0, 16, 9, ""),
  D_ROTATION(0, 15, 0, ""),
  D_MUTE(0, 1, 0, ""),
  PULSE_WIDTH(5, 95, 50, "%");
  public final double min, max, initial;
  public final String unit;

  Parameter(double min, double max, double initial, String unit) {
    this.min = min;
    this.max = max;
    this.initial = initial;
    this.unit = unit;
  }

  private boolean integer() {
    return switch (this) {
      case OCTAVE,
          TEMPO,
          A_STEPS,
          A_HITS,
          A_ROTATION,
          A_MUTE,
          B_STEPS,
          B_HITS,
          B_ROTATION,
          B_MUTE,
          C_STEPS,
          C_HITS,
          C_ROTATION,
          C_MUTE,
          D_STEPS,
          D_HITS,
          D_ROTATION,
          D_MUTE ->
          true;
      default -> false;
    };
  }

  public double validate(double value) {
    if ((integer() && value != Math.rint(value))
        || !Double.isFinite(value)
        || value < min
        || value > max)
      throw new IllegalArgumentException("range:" + name() + ":" + min + ":" + max);
    return value;
  }
}
