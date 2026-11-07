package com.malevolentgods.modularcraft.domain;

import java.util.Map;
import java.util.UUID;

public record ModuleState(
    UUID id,
    UUID owner,
    ModuleKind kind,
    String dimension,
    int x,
    int y,
    int z,
    long revision,
    Map<Parameter, Double> values,
    boolean loaded,
    boolean enabled) {
  public ModuleState {
    java.util.Objects.requireNonNull(id);
    java.util.Objects.requireNonNull(owner);
    java.util.Objects.requireNonNull(kind);
    if (!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
        || dimension.length() > 128
        || revision < 0
        || Math.abs((long) x) > 30000000
        || Math.abs((long) z) > 30000000
        || y < -2048
        || y > 2048) throw new IllegalArgumentException("module_metadata");
    values = Map.copyOf(values);
    if (!values.keySet().equals(java.util.Set.copyOf(kind.parameters)))
      throw new IllegalArgumentException("parameters");
    for (var entry : values.entrySet()) entry.getKey().validate(entry.getValue());
    if (kind == ModuleKind.EUCLIDEAN_SEQUENCER) SequencerSettings.from(values);
    if (kind == ModuleKind.STEP_SEQUENCER) StepSequencerSettings.from(values);
    if (enabled && kind != ModuleKind.SPEAKER) throw new IllegalArgumentException("not_speaker");
  }

  public static ModuleState create(UUID id, UUID owner, ModuleKind kind, int x, int y, int z) {
    return new ModuleState(
        id, owner, kind, "minecraft:overworld", x, y, z, 0, kind.defaults(), true, false);
  }

  public boolean wantsPlayback() {
    return kind == ModuleKind.SPEAKER && value(Parameter.SPEAKER_ENABLED) == 1;
  }

  public boolean suspended() {
    return kind == ModuleKind.SPEAKER && value(Parameter.SPEAKER_SUSPENDED) == 1;
  }

  public double value(Parameter p) {
    return values.get(p);
  }

  public ModuleState withValue(Parameter p, double v) {
    if (!kind.parameters.contains(p)) throw new IllegalArgumentException("parameter");
    java.util.Map<Parameter, Double> next = new java.util.EnumMap<>(values);
    next.put(p, p.validate(v));
    if (kind == ModuleKind.EUCLIDEAN_SEQUENCER) {
      for (int channel = 0; channel < 4; channel++)
        if (p == SequencerSettings.parameter(channel, 0)) {
          Parameter hits = SequencerSettings.parameter(channel, 1),
              rotation = SequencerSettings.parameter(channel, 2);
          next.put(hits, Math.min(next.get(hits), v));
          next.put(rotation, next.get(rotation) % v);
        }
    }
    return new ModuleState(
        id, owner, kind, dimension, x, y, z, Math.addExact(revision, 1), next, loaded, enabled);
  }

  public ModuleState bump() {
    return new ModuleState(
        id, owner, kind, dimension, x, y, z, Math.addExact(revision, 1), values, loaded, enabled);
  }

  public ModuleState runtime(boolean loaded, boolean enabled) {
    return new ModuleState(id, owner, kind, dimension, x, y, z, revision, values, loaded, enabled);
  }
}
