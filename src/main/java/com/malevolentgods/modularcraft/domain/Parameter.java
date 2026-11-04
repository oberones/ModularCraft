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
  DETUNE(0, 100, 0, "cents");
  public final double min, max, initial;
  public final String unit;

  Parameter(double min, double max, double initial, String unit) {
    this.min = min;
    this.max = max;
    this.initial = initial;
    this.unit = unit;
  }

  public double validate(double value) {
    if ((this == OCTAVE && value != Math.rint(value))
        || !Double.isFinite(value)
        || value < min
        || value > max)
      throw new IllegalArgumentException("range:" + name() + ":" + min + ":" + max);
    return value;
  }
}
