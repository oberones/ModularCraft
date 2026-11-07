package com.malevolentgods.modularcraft.dsp;

/** Linear 10 ms ramp; ordinary controls only, never ADSR stage curves. */
public final class Smoother {
  private double value, target, step;
  private int left;

  public Smoother(double initial) {
    value = initial;
    target = initial;
  }

  public void target(double next) {
    if (next != target) {
      target = next;
      left = 480;
      step = (target - value) / left;
    }
  }

  public double next() {
    if (left > 0) {
      value += step;
      if (--left == 0) value = target;
    }
    return value;
  }

  public void reset(double initial) {
    value = initial;
    target = initial;
    left = 0;
  }

  public void copyFrom(Smoother previous) {
    value = previous.value;
    target = previous.target;
    step = previous.step;
    left = previous.left;
  }

  public double current() {
    return value;
  }
}
