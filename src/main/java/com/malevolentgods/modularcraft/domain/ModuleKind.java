package com.malevolentgods.modularcraft.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public enum ModuleKind {
  OSCILLATOR(
      List.of(Parameter.FREQUENCY, Parameter.DETUNE, Parameter.LEVEL),
      List.of(Port.AUDIO_OUT, Port.PITCH_IN)),
  FILTER(
      List.of(Parameter.CUTOFF, Parameter.RESONANCE, Parameter.AMOUNT),
      List.of(Port.AUDIO_IN, Port.CUTOFF_IN, Port.AUDIO_OUT)),
  ENVELOPE(
      List.of(Parameter.ATTACK, Parameter.DECAY, Parameter.SUSTAIN, Parameter.RELEASE),
      List.of(Port.ENVELOPE_OUT, Port.GATE_IN)),
  SPEAKER(List.of(Parameter.VOLUME), List.of(Port.AUDIO_IN, Port.GAIN_IN)),
  KEYBOARD(List.of(Parameter.OCTAVE), List.of(Port.CV_OUT, Port.GATE_OUT)),
  EUCLIDEAN_SEQUENCER(
      List.of(
          Parameter.TEMPO,
          Parameter.A_STEPS,
          Parameter.A_HITS,
          Parameter.A_ROTATION,
          Parameter.A_MUTE,
          Parameter.B_STEPS,
          Parameter.B_HITS,
          Parameter.B_ROTATION,
          Parameter.B_MUTE,
          Parameter.C_STEPS,
          Parameter.C_HITS,
          Parameter.C_ROTATION,
          Parameter.C_MUTE,
          Parameter.D_STEPS,
          Parameter.D_HITS,
          Parameter.D_ROTATION,
          Parameter.D_MUTE),
      List.of(
          Port.CLOCK_IN,
          Port.RESET_IN,
          Port.CLOCK_OUT,
          Port.TRIGGER_A,
          Port.TRIGGER_B,
          Port.TRIGGER_C,
          Port.TRIGGER_D));
  public final List<Parameter> parameters;
  public final List<Port> ports;

  ModuleKind(List<Parameter> parameters, List<Port> ports) {
    this.parameters = parameters;
    this.ports = ports;
  }

  public Map<Parameter, Double> defaults() {
    Map<Parameter, Double> values = new EnumMap<>(Parameter.class);
    for (Parameter p : parameters) values.put(p, p.initial);
    return Map.copyOf(values);
  }
}
