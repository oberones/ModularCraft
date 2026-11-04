package com.malevolentgods.modularcraft.domain;

import java.util.List;
import java.util.Map;

/** Immutable, prepared musical settings. Steps and rotation are zero-based internally. */
public record SequencerSettings(int tempo, List<Channel> channels) {
  private static final Parameter[][] PARAMETERS = {
    {Parameter.A_STEPS, Parameter.A_HITS, Parameter.A_ROTATION, Parameter.A_MUTE},
    {Parameter.B_STEPS, Parameter.B_HITS, Parameter.B_ROTATION, Parameter.B_MUTE},
    {Parameter.C_STEPS, Parameter.C_HITS, Parameter.C_ROTATION, Parameter.C_MUTE},
    {Parameter.D_STEPS, Parameter.D_HITS, Parameter.D_ROTATION, Parameter.D_MUTE}
  };

  public record Channel(int steps, int hits, int rotation, boolean muted) {
    public Channel {
      if (steps < 1 || steps > 16 || hits < 0 || hits > steps || rotation < 0 || rotation >= steps)
        throw new IllegalArgumentException("sequencer_pattern");
    }

    public boolean hit(int step) {
      return hits > 0 && Math.floorMod(step - rotation, steps) * hits % steps < hits;
    }
  }

  public SequencerSettings {
    Parameter.TEMPO.validate(tempo);
    channels = List.copyOf(channels);
    if (channels.size() != 4) throw new IllegalArgumentException("sequencer_channels");
  }

  public static Parameter parameter(int channel, int control) {
    return PARAMETERS[channel][control];
  }

  public static SequencerSettings from(ModuleState module) {
    return from(module.values());
  }

  public static SequencerSettings from(Map<Parameter, Double> values) {
    Channel[] channels = new Channel[4];
    for (int c = 0; c < 4; c++) {
      int[] numbers = new int[4];
      for (int p = 0; p < 4; p++) {
        Parameter parameter = parameter(c, p);
        numbers[p] = (int) parameter.validate(values.get(parameter));
      }
      channels[c] = new Channel(numbers[0], numbers[1], numbers[2], numbers[3] == 1);
    }
    return new SequencerSettings(
        (int) Parameter.TEMPO.validate(values.get(Parameter.TEMPO)), List.of(channels));
  }
}
