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

class SequencerLoadTest {
  @Test
  void fourMaximumPatchesWithEveryChannelActiveStayWithinAudioBudget() {
    MeasurementTest.benchmark(fixtures(false));
  }

  @Test
  void fourMaximumPatchesWithClockAndResetChainsStayWithinAudioBudget() {
    MeasurementTest.benchmark(fixtures(true));
  }

  private static List<PatchSnapshot> fixtures(boolean chain) {
    List<PatchSnapshot> patches = new ArrayList<>();
    for (int patch = 0; patch < 4; patch++) patches.add(fixture(patch, chain));
    return patches;
  }

  private static PatchSnapshot fixture(int patch, boolean chain) {
    int rhythms = chain ? 4 : 1, filters = chain ? 5 : 8;
    List<ModuleState> modules = new ArrayList<>();
    List<Cable> cables = new ArrayList<>();
    Map<UUID, Sequencer.Checkpoint> sequencers = new HashMap<>();
    Map<UUID, Envelope.Checkpoint> envelopes = new HashMap<>();
    Map<UUID, Boolean> bases = new HashMap<>();
    for (int i = 0; i < 16; i++) {
      ModuleKind kind =
          i < rhythms
              ? ModuleKind.EUCLIDEAN_SEQUENCER
              : i == rhythms
                  ? ModuleKind.OSCILLATOR
                  : i <= rhythms + filters
                      ? ModuleKind.FILTER
                      : i < 14 ? ModuleKind.ENVELOPE : ModuleKind.SPEAKER;
      ModuleState module =
          ModuleState.create(
              new UUID(patch + 1, i + 1),
              new UUID(0, 1),
              kind,
              patch % 2 * 8 + i % 4 * 2,
              64,
              patch / 2 * 8 + i / 4 * 2);
      if (kind == ModuleKind.EUCLIDEAN_SEQUENCER) {
        module = module.withValue(Parameter.TEMPO, 300);
        for (int c = 0; c < 4; c++)
          module = module.withValue(SequencerSettings.parameter(c, 1), 16);
        Sequencer runtime = new Sequencer(SequencerSettings.from(module), 96000);
        if (i > 0) runtime.connect(true, false, false, 96000);
        runtime.start(96000);
        sequencers.put(module.id(), runtime.checkpoint(96000));
      }
      if (kind == ModuleKind.OSCILLATOR) module = module.withValue(Parameter.DETUNE, 100);
      if (kind == ModuleKind.ENVELOPE) {
        Envelope runtime = new Envelope();
        envelopes.put(module.id(), runtime.checkpoint(96000));
        bases.put(module.id(), false);
      }
      if (kind == ModuleKind.SPEAKER) module = module.runtime(true, true);
      modules.add(module);
    }
    for (int i = rhythms; i < rhythms + filters; i++)
      cables.add(
          new Cable(modules.get(i).id(), Port.AUDIO_OUT, modules.get(i + 1).id(), Port.AUDIO_IN));
    for (int i = 14; i < 16; i++)
      cables.add(
          new Cable(
              modules.get(rhythms + filters).id(),
              Port.AUDIO_OUT,
              modules.get(i).id(),
              Port.AUDIO_IN));
    Port[] outputs = {Port.TRIGGER_A, Port.TRIGGER_B, Port.TRIGGER_C, Port.TRIGGER_D};
    for (int c = 0; c < 4; c++)
      cables.add(
          new Cable(modules.getFirst().id(), outputs[c], modules.get(10 + c).id(), Port.GATE_IN));
    for (int i = 0; i < filters + 2; i++) {
      int destination = i < filters ? rhythms + 1 + i : 14 + i - filters;
      cables.add(
          new Cable(
              modules.get(10 + i % 4).id(),
              Port.ENVELOPE_OUT,
              modules.get(destination).id(),
              i < filters ? Port.CUTOFF_IN : Port.GAIN_IN));
    }
    for (int i = 1; i < rhythms; i++) {
      cables.add(
          new Cable(modules.get(i - 1).id(), Port.CLOCK_OUT, modules.get(i).id(), Port.CLOCK_IN));
      cables.add(
          new Cable(modules.getFirst().id(), outputs[i - 1], modules.get(i).id(), Port.RESET_IN));
    }
    Assertions.assertEquals(24, cables.size());
    return new PatchSnapshot(
        modules.getFirst().id(),
        0,
        1,
        96000,
        modules,
        cables,
        envelopes,
        Map.of(),
        sequencers,
        bases);
  }
}
