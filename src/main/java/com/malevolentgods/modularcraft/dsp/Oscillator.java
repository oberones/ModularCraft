package com.malevolentgods.modularcraft.dsp;

/**
 * Phase-continuous PolyBLEP saw. Four internal samples plus a 95-tap Blackman decimator suppress
 * residual aliasing; the public signal rate remains 48 kHz. Filter coefficients are prepared at
 * class initialization, and history is owned by the audio thread. L1 normalization proves peak
 * magnitude never exceeds selected level.
 */
public final class Oscillator {
  private static final int FACTOR = 4;
  private static final double[] COEFFICIENTS = coefficients();
  private final double[] history = new double[95];
  private int cursor;
  private double phase;

  private static double[] coefficients() {
    double[] taps = new double[95];
    double norm = 0;
    for (int i = 0; i < taps.length; i++) {
      int offset = i - 47;
      double sinc =
          offset == 0 ? .225 : Math.sin(2 * Math.PI * .1125 * offset) / (Math.PI * offset);
      double window =
          .42 - .5 * Math.cos(2 * Math.PI * i / 94) + .08 * Math.cos(4 * Math.PI * i / 94);
      taps[i] = sinc * window;
      norm += Math.abs(taps[i]);
    }
    for (int i = 0; i < taps.length; i++) taps[i] /= norm;
    return taps;
  }

  public double next(double hz, double level) {
    double dt = hz / (48000.0 * FACTOR);
    for (int sub = 0; sub < FACTOR; sub++) {
      double value = 2 * phase - 1;
      if (phase < dt) {
        double t = phase / dt;
        value -= t + t - t * t - 1;
      } else if (phase > 1 - dt) {
        double t = (phase - 1) / dt;
        value -= t * t + t + t + 1;
      }
      history[cursor] = value;
      cursor = (cursor + 1) % history.length;
      phase += dt;
      phase -= Math.floor(phase);
    }
    double result = 0;
    int index = cursor;
    for (int tap = 0; tap < history.length; tap++) {
      if (--index < 0) index = history.length - 1;
      result += history[index] * COEFFICIENTS[tap];
    }
    return result * level;
  }

  public double phase() {
    return phase;
  }

  public void phase(double value) {
    phase = value - Math.floor(value);
  }

  public void copyFrom(Oscillator other) {
    phase = other.phase;
    cursor = other.cursor;
    System.arraycopy(other.history, 0, history, 0, history.length);
  }
}
