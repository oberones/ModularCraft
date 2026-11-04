package com.malevolentgods.modularcraft.domain;

public enum Port {
  AUDIO_OUT(true, Role.AUDIO),
  AUDIO_IN(false, Role.AUDIO),
  ENVELOPE_OUT(true, Role.CONTROL),
  CUTOFF_IN(false, Role.CONTROL),
  GAIN_IN(false, Role.CONTROL),
  CV_OUT(true, Role.PITCH),
  PITCH_IN(false, Role.PITCH),
  GATE_OUT(true, Role.GATE),
  GATE_IN(false, Role.GATE),
  CLOCK_IN(false, Role.GATE),
  RESET_IN(false, Role.GATE),
  CLOCK_OUT(true, Role.GATE),
  TRIGGER_A(true, Role.GATE),
  TRIGGER_B(true, Role.GATE),
  TRIGGER_C(true, Role.GATE),
  TRIGGER_D(true, Role.GATE);

  public enum Role {
    AUDIO,
    CONTROL,
    PITCH,
    GATE
  }

  public final boolean output, control;
  public final Role role;

  Port(boolean output, Role role) {
    this.output = output;
    this.role = role;
    control = role != Role.AUDIO;
  }

  public boolean connects(Port destination) {
    return output && !destination.output && role == destination.role;
  }
}
