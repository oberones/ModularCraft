package com.malevolentgods.modularcraft.dsp;

/** Five Hz DC blocker and finite hard output ceiling; one instance per output channel. */
public final class Output {
  private static final double R = Math.exp(-2 * Math.PI * 5 / 48000);
  private double previous, filtered;

  public double next(double input) {
    if (!Double.isFinite(input)) {
      previous = 0;
      filtered = 0;
      return 0;
    }
    filtered = input - previous + R * filtered;
    previous = input;
    if (Math.abs(filtered) < 1e-30) filtered = 0;
    return Math.clamp(filtered, -1, 1);
  }

  public static double distance(double blocks) {
    return Math.clamp((32 - blocks) / 30, 0, 1);
  }

  public static double pan(double right, double forward) {
    double distance = Math.hypot(right, forward);
    return distance < 1e-9 ? 0 : Math.clamp(right / distance, -1, 1);
  }
}
