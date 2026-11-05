package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.domain.SequencerSettings;
import com.malevolentgods.modularcraft.dsp.Envelope;
import com.malevolentgods.modularcraft.dsp.Sequencer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LfoLoadTest {
  @Test
  void fourMaximumPatchesWithClockAndAllEnvelopeCvStayWithinAudioBudget() {
    List<PatchSnapshot> patches = new ArrayList<>();
    for (int patch = 0; patch < 4; patch++) {
      List<ModuleState> modules = new ArrayList<>();
      List<Cable> cables = new ArrayList<>();
      Map<UUID, Envelope.Checkpoint> envelopes = new HashMap<>();
      Map<UUID, Boolean> bases = new HashMap<>();
      for (int i = 0; i < 16; i++) {
        ModuleKind kind =
            i == 0
                ? ModuleKind.EUCLIDEAN_SEQUENCER
                : i < 4
                    ? ModuleKind.LFO
                    : i == 4
                        ? ModuleKind.OSCILLATOR
                        : i < 12
                            ? ModuleKind.FILTER
                            : i < 14 ? ModuleKind.ENVELOPE : ModuleKind.SPEAKER;
        ModuleState m =
            ModuleState.create(
                new UUID(patch + 1, i + 1),
                new UUID(0, 1),
                kind,
                patch % 2 * 8 + i % 4 * 2,
                64,
                patch / 2 * 8 + i / 4 * 2);
        if (kind == ModuleKind.LFO)
          m = m.withValue(Parameter.LFO_WAVE, i).withValue(Parameter.LFO_DIVISION, 0);
        if (kind == ModuleKind.OSCILLATOR) m = m.withValue(Parameter.DETUNE, 100);
        if (kind == ModuleKind.FILTER)
          m = m.withValue(Parameter.AMOUNT, 50).withValue(Parameter.RESONANCE_DEPTH, 50);
        if (kind == ModuleKind.ENVELOPE) {
          for (Parameter p :
              List.of(
                  Parameter.ATTACK_DEPTH,
                  Parameter.DECAY_DEPTH,
                  Parameter.SUSTAIN_DEPTH,
                  Parameter.RELEASE_DEPTH)) m = m.withValue(p, 50);
          envelopes.put(m.id(), new Envelope().checkpoint(96000));
          bases.put(m.id(), false);
        }
        if (kind == ModuleKind.SPEAKER) m = m.runtime(true, true);
        if (i == 0)
          m =
              m.withValue(Parameter.TEMPO, 300)
                  .withValue(Parameter.A_HITS, 16)
                  .withValue(Parameter.B_HITS, 16);
        modules.add(m);
      }
      for (int i = 4; i < 11; i++) add(cables, modules, i, Port.AUDIO_OUT, i + 1, Port.AUDIO_IN);
      for (int i = 14; i < 16; i++) add(cables, modules, 11, Port.AUDIO_OUT, i, Port.AUDIO_IN);
      for (int i = 1; i < 4; i++) add(cables, modules, 0, Port.CLOCK_OUT, i, Port.CLOCK_IN);
      add(cables, modules, 0, Port.TRIGGER_A, 12, Port.GATE_IN);
      add(cables, modules, 0, Port.TRIGGER_B, 13, Port.GATE_IN);
      add(cables, modules, 12, Port.ENVELOPE_OUT, 14, Port.GAIN_IN);
      add(cables, modules, 13, Port.ENVELOPE_OUT, 15, Port.GAIN_IN);
      for (Port port : List.of(Port.ATTACK_IN, Port.DECAY_IN, Port.SUSTAIN_IN, Port.RELEASE_IN))
        add(cables, modules, 1, Port.CONTROL_OUT, 12, port);
      for (int i = 5; i < 9; i++)
        add(
            cables,
            modules,
            2,
            Port.CONTROL_OUT,
            i,
            i % 2 == 0 ? Port.RESONANCE_IN : Port.CUTOFF_IN);
      Assertions.assertEquals(24, cables.size());
      Sequencer seq = new Sequencer(SequencerSettings.from(modules.getFirst()), 96000);
      seq.start(96000);
      patches.add(
          new PatchSnapshot(
              modules.getFirst().id(),
              0,
              1,
              96000,
              modules,
              cables,
              envelopes,
              Map.of(),
              Map.of(modules.getFirst().id(), seq.checkpoint(96000)),
              bases));
    }
    MeasurementTest.benchmark(patches);
  }

  private static void add(
      List<Cable> cables, List<ModuleState> modules, int from, Port out, int to, Port in) {
    cables.add(new Cable(modules.get(from).id(), out, modules.get(to).id(), in));
  }
}
