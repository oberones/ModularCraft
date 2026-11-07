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
  PULSE_WIDTH(5, 95, 50, "%"),
  LFO_FREQUENCY(.01, 20, 1, "Hz"),
  LFO_WAVE(0, 4, 0, ""),
  LFO_POLARITY(0, 1, 0, ""),
  LFO_DIVISION(0, 6, 2, ""),
  RESONANCE_DEPTH(-100, 100, 0, "%"),
  ATTACK_DEPTH(-100, 100, 0, "%"),
  DECAY_DEPTH(-100, 100, 0, "%"),
  SUSTAIN_DEPTH(-100, 100, 0, "%"),
  RELEASE_DEPTH(-100, 100, 0, "%"),
  MIX_1_LEVEL(0, 100, 100, "%"),
  MIX_1_MUTE(0, 1, 0, ""),
  MIX_1_SOLO(0, 1, 0, ""),
  MIX_2_LEVEL(0, 100, 100, "%"),
  MIX_2_MUTE(0, 1, 0, ""),
  MIX_2_SOLO(0, 1, 0, ""),
  MIX_3_LEVEL(0, 100, 100, "%"),
  MIX_3_MUTE(0, 1, 0, ""),
  MIX_3_SOLO(0, 1, 0, ""),
  MIX_4_LEVEL(0, 100, 100, "%"),
  MIX_4_MUTE(0, 1, 0, ""),
  MIX_4_SOLO(0, 1, 0, ""),
  MIX_MASTER_LEVEL(0, 100, 25, "%"),
  MIX_MASTER_MUTE(0, 1, 0, ""),
  AMP_LEVEL(0, 100, 100, "%"),
  DRONE(0, 1, 0, ""),
  SPEAKER_ENABLED(0, 1, 1, ""),
  SPEAKER_SUSPENDED(0, 1, 0, ""),
  REDSTONE_MODE(0, 1, 0, "");
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
      case REDSTONE_MODE,
          DRONE,
          SPEAKER_ENABLED,
          SPEAKER_SUSPENDED,
          MIX_1_MUTE,
          MIX_1_SOLO,
          MIX_2_MUTE,
          MIX_2_SOLO,
          MIX_3_MUTE,
          MIX_3_SOLO,
          MIX_4_MUTE,
          MIX_4_SOLO,
          MIX_MASTER_MUTE,
          LFO_WAVE,
          LFO_POLARITY,
          LFO_DIVISION,
          OCTAVE,
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
