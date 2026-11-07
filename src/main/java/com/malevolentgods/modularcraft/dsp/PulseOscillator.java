package com.malevolentgods.modularcraft.dsp;

/**
 * Single phase-continuous pulse voice: Hz pitch, 0.05..0.95 duty fraction, 0..1 level. Both edges
 * use four-sample PolyBLEP at 4x 48 kHz, followed by an L1-normalized 95-tap, 18 kHz decimator.
 * Subtracting the pulse's mean and dividing by twice its longer duty fraction bounds the
 * compensated signal to +/-1 without boosting narrow pulses. Preparation owns construction; the
 * audio thread owns next/adopt with bounded, allocation-free work and 10 ms control ramps.
 */
public final class PulseOscillator {
  private static final double[] COEFFICIENTS = Oscillator.coefficients(.09375);
  private final double[] history = new double[95];
  private final Smoother frequency, width;
  private int cursor;
  private double phase;

  public PulseOscillator(double hz, double duty, long sample) {
    frequency = new Smoother(hz);
    width = new Smoother(duty);
    phase = sample * hz / 48000.0;
    phase -= Math.floor(phase);
  }

  /** Copies continuous state, then ramps to this prepared graph's targets. */
  public void adopt(PulseOscillator previous) {
    phase = previous.phase;
    cursor = previous.cursor;
    System.arraycopy(previous.history, 0, history, 0, history.length);
    double hz = frequency.current(), duty = width.current();
    frequency.reset(previous.frequency.current());
    frequency.target(hz);
    width.reset(previous.width.current());
    width.target(duty);
  }

  /** Set a new concert pitch with the existing 10 ms ramp; audio-owner only. */
  public void pitch(double hz) {
    frequency.target(hz);
  }

  public double next(double level) {
    double dt = frequency.next() / 192000.0;
    double duty = width.next();
    double mean = 2 * duty - 1, scale = 2 * Math.max(duty, 1 - duty);
    for (int sub = 0; sub < 4; sub++) {
      double falling = phase - duty;
      if (falling < 0) falling += 1;
      double value = (phase < duty ? 1 : -1) + blep(phase, dt) - blep(falling, dt);
      history[cursor] = (value - mean) / scale;
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

  private static double blep(double phase, double dt) {
    // Integrated cubic B-spline step (four-sample support). Its nonnegative kernel
    // preserves the pulse bounds even when narrow pulses have overlapping edges.
    // The wider correction suppresses internal-rate aliases at high pitches.
    double distance = Math.min(phase, 1 - phase) / dt;
    if (distance >= 2) return 0;
    double a = 2 - distance;
    double tail = a * a * a * a;
    if (distance < 1) {
      double b = 1 - distance;
      tail -= 4 * b * b * b * b;
    }
    return (phase < .5 ? -1 : 1) * tail / 12;
  }
}
