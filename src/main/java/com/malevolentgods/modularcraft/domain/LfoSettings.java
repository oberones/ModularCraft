package com.malevolentgods.modularcraft.domain;

/** Immutable LFO controls; phase runs on the shared 48 kHz transport. */
public record LfoSettings(double frequency, int wave, boolean unipolar, int division) {
  public LfoSettings {
    Parameter.LFO_FREQUENCY.validate(frequency);
    Parameter.LFO_WAVE.validate(wave);
    Parameter.LFO_DIVISION.validate(division);
  }

  public static LfoSettings from(ModuleState module) {
    return new LfoSettings(
        module.value(Parameter.LFO_FREQUENCY),
        (int) module.value(Parameter.LFO_WAVE),
        module.value(Parameter.LFO_POLARITY) == 1,
        (int) module.value(Parameter.LFO_DIVISION));
  }

  public int pulses() {
    return 1 << division;
  }
}
