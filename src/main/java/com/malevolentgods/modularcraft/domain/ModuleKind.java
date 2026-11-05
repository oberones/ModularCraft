package com.malevolentgods.modularcraft.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public enum ModuleKind {
  OSCILLATOR(
      List.of(Parameter.FREQUENCY, Parameter.DETUNE, Parameter.LEVEL),
      List.of(Port.AUDIO_OUT, Port.PITCH_IN)),
  FILTER(
      List.of(Parameter.CUTOFF, Parameter.AMOUNT, Parameter.RESONANCE, Parameter.RESONANCE_DEPTH),
      List.of(Port.AUDIO_IN, Port.CUTOFF_IN, Port.AUDIO_OUT, Port.RESONANCE_IN)),
  ENVELOPE(
      List.of(
          Parameter.ATTACK,
          Parameter.ATTACK_DEPTH,
          Parameter.DECAY,
          Parameter.DECAY_DEPTH,
          Parameter.SUSTAIN,
          Parameter.SUSTAIN_DEPTH,
          Parameter.RELEASE,
          Parameter.RELEASE_DEPTH),
      List.of(
          Port.ENVELOPE_OUT,
          Port.GATE_IN,
          Port.ATTACK_IN,
          Port.DECAY_IN,
          Port.SUSTAIN_IN,
          Port.RELEASE_IN)),
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
          Port.TRIGGER_D)),
  SQUARE_OSCILLATOR(
      List.of(Parameter.FREQUENCY, Parameter.PULSE_WIDTH, Parameter.LEVEL),
      List.of(Port.AUDIO_OUT, Port.PITCH_IN)),
  LFO(
      List.of(
          Parameter.LFO_WAVE,
          Parameter.LFO_FREQUENCY,
          Parameter.LFO_POLARITY,
          Parameter.LFO_DIVISION),
      List.of(Port.CONTROL_OUT, Port.CLOCK_IN));
  public final List<Parameter> parameters;
  public final List<Port> ports;

  ModuleKind(List<Parameter> parameters, List<Port> ports) {
    this.parameters = parameters;
    this.ports = ports;
  }

  public boolean isOscillator() {
    return this == OSCILLATOR || this == SQUARE_OSCILLATOR;
  }

  public Map<Parameter, Double> defaults() {
    Map<Parameter, Double> values = new EnumMap<>(Parameter.class);
    for (Parameter p : parameters) values.put(p, p.initial);
    return Map.copyOf(values);
  }
}
