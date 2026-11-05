package com.malevolentgods.modularcraft.dsp;

/** Topology-preserving state-variable low-pass, 48 kHz; audio-thread ownership. */
public final class Filter {
  private double ic1, ic2;

  public void copyFrom(Filter other) {
    ic1 = other.ic1;
    ic2 = other.ic2;
  }

  public static double cutoff(double base, double control, double amount) {
    return Math.clamp(
        base + (control >= 0 ? 20000 - base : base - 20) * control * amount, 20, 20000);
  }

  public static double q(double resonance) {
    return .707 + (10 - .707) * resonance / 95;
  }

  public double next(double input, double cutoff, double resonance) {
    double g = Math.tan(Math.PI * cutoff / 48000), k = 1 / q(resonance);
    double a1 = 1 / (1 + g * (g + k)), a2 = g * a1, a3 = g * a2;
    double v3 = input - ic2, v1 = a1 * ic1 + a2 * v3, v2 = ic2 + a2 * ic1 + a3 * v3;
    ic1 = 2 * v1 - ic1;
    ic2 = 2 * v2 - ic2;
    if (Math.abs(ic1) < 1e-30) ic1 = 0;
    if (Math.abs(ic2) < 1e-30) ic2 = 0;
    return v2;
  }
}
