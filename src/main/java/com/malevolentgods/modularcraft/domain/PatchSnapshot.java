package com.malevolentgods.modularcraft.domain;

import com.malevolentgods.modularcraft.dsp.Envelope;
import com.malevolentgods.modularcraft.dsp.Lfo;
import com.malevolentgods.modularcraft.dsp.Sequencer;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PatchSnapshot(
    UUID id,
    long revision,
    long sequence,
    long sample,
    List<ModuleState> modules,
    List<Cable> cables,
    Map<UUID, Envelope.Checkpoint> envelopes,
    Map<UUID, KeyboardState> keyboards,
    Map<UUID, Sequencer.Checkpoint> sequencers,
    Map<UUID, Boolean> baseGates,
    Map<UUID, Lfo.Checkpoint> lfos) {
  public PatchSnapshot(
      UUID id,
      long revision,
      long sequence,
      long sample,
      List<ModuleState> modules,
      List<Cable> cables,
      Map<UUID, Envelope.Checkpoint> envelopes,
      Map<UUID, KeyboardState> keyboards,
      Map<UUID, Sequencer.Checkpoint> sequencers,
      Map<UUID, Boolean> baseGates) {
    this(
        id,
        revision,
        sequence,
        sample,
        modules,
        cables,
        envelopes,
        keyboards,
        sequencers,
        baseGates,
        initialLfos(modules, cables, sample));
  }

  private static Map<UUID, Lfo.Checkpoint> initialLfos(
      List<ModuleState> modules, List<Cable> cables, long sample) {
    Map<UUID, Lfo.Checkpoint> result = new java.util.HashMap<>();
    for (ModuleState module : modules)
      if (module.kind() == ModuleKind.LFO) {
        Lfo lfo = new Lfo(LfoSettings.from(module), sample);
        if (cables.stream()
            .anyMatch(c -> c.destination().equals(module.id()) && c.input() == Port.CLOCK_IN))
          lfo.connect(true, false, sample);
        result.put(module.id(), lfo.checkpoint(sample));
      }
    return result;
  }

  public PatchSnapshot(
      UUID id,
      long revision,
      long sequence,
      long sample,
      List<ModuleState> modules,
      List<Cable> cables,
      Map<UUID, Envelope.Checkpoint> envelopes,
      Map<UUID, KeyboardState> keyboards) {
    this(
        id,
        revision,
        sequence,
        sample,
        modules,
        cables,
        envelopes,
        keyboards,
        stopped(modules, cables, sample),
        bases(envelopes, keyboards, cables));
  }

  private static Map<UUID, Sequencer.Checkpoint> stopped(
      List<ModuleState> modules, List<Cable> cables, long sample) {
    Map<UUID, Sequencer.Checkpoint> result = new java.util.HashMap<>();
    for (ModuleState module : modules)
      if (module.kind() == ModuleKind.EUCLIDEAN_SEQUENCER) {
        Sequencer runtime = new Sequencer(SequencerSettings.from(module), sample);
        boolean external =
            cables.stream()
                .anyMatch(c -> c.destination().equals(module.id()) && c.input() == Port.CLOCK_IN);
        runtime.connect(external, false, false, sample);
        result.put(module.id(), runtime.checkpoint(sample));
      }
    return result;
  }

  private static Map<UUID, Boolean> bases(
      Map<UUID, Envelope.Checkpoint> envelopes,
      Map<UUID, KeyboardState> keyboards,
      List<Cable> cables) {
    Map<UUID, Boolean> result = new java.util.HashMap<>();
    envelopes.forEach(
        (id, checkpoint) ->
            result.put(
                id,
                checkpoint.gate()
                    && cables.stream()
                        .noneMatch(
                            c ->
                                c.destination().equals(id)
                                    && c.input() == Port.GATE_IN
                                    && keyboards.containsKey(c.source())
                                    && keyboards.get(c.source()).gate())));
    return result;
  }

  public PatchSnapshot(
      UUID id,
      long revision,
      long sequence,
      long sample,
      List<ModuleState> modules,
      List<Cable> cables,
      Map<UUID, Envelope.Checkpoint> envelopes) {
    this(id, revision, sequence, sample, modules, cables, envelopes, Map.of());
  }

  public PatchSnapshot {
    lfos = Map.copyOf(lfos);
    modules = List.copyOf(modules);
    cables = List.copyOf(cables);
    envelopes = Map.copyOf(envelopes);
    keyboards = Map.copyOf(keyboards);
    sequencers = Map.copyOf(sequencers);
    baseGates = Map.copyOf(baseGates);
    if (id == null
        || revision < 0
        || sequence < 0
        || sample < 0
        || modules.size() > 16
        || cables.size() > 24
        || envelopes.size() > 16
        || keyboards.size() > 16
        || sequencers.size() > 16
        || lfos.size() > 16
        || baseGates.size() > 16) throw new IllegalArgumentException("snapshot_bounds");
    java.util.Map<UUID, ModuleState> map = new java.util.HashMap<>();
    for (ModuleState m : modules)
      if (map.put(m.id(), m) != null) throw new IllegalArgumentException("duplicate_identity");
    WorldState.validate(map, cables);
    for (ModuleState module : modules)
      if ((module.kind() == ModuleKind.EUCLIDEAN_SEQUENCER) != sequencers.containsKey(module.id()))
        throw new IllegalArgumentException("sequencer_identity");
    for (var entry : sequencers.entrySet()) {
      if (!map.containsKey(entry.getKey())
          || entry.getValue().sample() != sample
          || (!map.get(entry.getKey()).loaded() && entry.getValue().running()))
        throw new IllegalArgumentException("sequencer_checkpoint");
      boolean external =
          cables.stream()
              .anyMatch(c -> c.destination().equals(entry.getKey()) && c.input() == Port.CLOCK_IN);
      if (entry.getValue().external() != external
          || (!entry.getValue().running()
              && !entry
                  .getValue()
                  .active()
                  .equals(SequencerSettings.from(map.get(entry.getKey())))))
        throw new IllegalArgumentException("sequencer_checkpoint");
    }
    for (ModuleState module : modules)
      if ((module.kind() == ModuleKind.LFO) != lfos.containsKey(module.id()))
        throw new IllegalArgumentException("lfo_identity");
    for (var entry : lfos.entrySet()) {
      boolean connected =
          cables.stream()
              .anyMatch(c -> c.destination().equals(entry.getKey()) && c.input() == Port.CLOCK_IN);
      if (!map.containsKey(entry.getKey())
          || entry.getValue().sample() != sample
          || entry.getValue().external() != connected)
        throw new IllegalArgumentException("lfo_checkpoint");
    }
    if (!baseGates.keySet().equals(envelopes.keySet()))
      throw new IllegalArgumentException("envelope_gate_identity");
    for (UUID key : keyboards.keySet())
      if (!map.containsKey(key) || map.get(key).kind() != ModuleKind.KEYBOARD)
        throw new IllegalArgumentException("keyboard_identity");
    for (UUID key : envelopes.keySet())
      if (!map.containsKey(key) || !map.get(key).kind().hasEnvelope())
        throw new IllegalArgumentException("envelope_identity");
  }
}
