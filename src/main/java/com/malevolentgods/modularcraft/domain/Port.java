package com.malevolentgods.modularcraft.domain;

public enum Port {
  AUDIO_OUT(true, false),
  AUDIO_IN(false, false),
  ENVELOPE_OUT(true, true),
  CUTOFF_IN(false, true),
  GAIN_IN(false, true);
  public final boolean output, control;

  Port(boolean output, boolean control) {
    this.output = output;
    this.control = control;
  }

  public boolean connects(Port destination) {
    return output && !destination.output && control == destination.control;
  }
}
