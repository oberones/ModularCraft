package com.malevolentgods.modularcraft.dsp;

import com.malevolentgods.modularcraft.domain.MixerSettings;
import com.malevolentgods.modularcraft.domain.ModuleState;

/** Audio-thread-owned four-channel linear summing, with 480-sample effective gain ramps. */
public final class Mixer {
  public final MixerMeter meter = new MixerMeter();
  private final Smoother[] gains = new Smoother[5];
  private final double[] targets = new double[5];
  private final double[] levels = new double[5];

  public Mixer(ModuleState state) {
    for (int i = 0; i < 5; i++) {
      targets[i] = MixerSettings.gain(state, i);
      gains[i] = new Smoother(targets[i]);
    }
  }

  public void adopt(Mixer old) {
    meter.adopt(old.meter);
    for (int i = 0; i < 5; i++) {
      gains[i].reset(old.gains[i].current());
      gains[i].target(targets[i]);
    }
  }

  public double next(double[] signals, int[] inputs) {
    double sum = 0;
    for (int i = 0; i < 4; i++) {
      levels[i] = (inputs[i] < 0 ? 0 : signals[inputs[i]]) * gains[i].next();
      sum += levels[i];
    }
    levels[4] = sum * gains[4].next();
    meter.sample(levels);
    return levels[4];
  }

  public double level(int channel) {
    return levels[channel];
  }
}
