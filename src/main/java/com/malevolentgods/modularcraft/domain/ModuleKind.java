package com.malevolentgods.modularcraft.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public enum ModuleKind {
  OSCILLATOR(List.of(Parameter.FREQUENCY, Parameter.LEVEL), List.of(Port.AUDIO_OUT)),
  FILTER(
      List.of(Parameter.CUTOFF, Parameter.RESONANCE, Parameter.AMOUNT),
      List.of(Port.AUDIO_IN, Port.CUTOFF_IN, Port.AUDIO_OUT)),
  ENVELOPE(
      List.of(Parameter.ATTACK, Parameter.DECAY, Parameter.SUSTAIN, Parameter.RELEASE),
      List.of(Port.ENVELOPE_OUT)),
  SPEAKER(List.of(Parameter.VOLUME), List.of(Port.AUDIO_IN, Port.GAIN_IN));
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
