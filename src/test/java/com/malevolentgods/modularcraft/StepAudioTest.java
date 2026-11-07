package com.malevolentgods.modularcraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.domain.StateCodec;
import com.malevolentgods.modularcraft.domain.StepSequencerSettings;
import com.malevolentgods.modularcraft.dsp.RenderGraph;
import com.malevolentgods.modularcraft.dsp.StepSequencer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StepAudioTest {
  @Test
  void pitchAdvancesWithoutNetworkSnapshotsOnBothOscillators() {
    for (ModuleKind kind : List.of(ModuleKind.OSCILLATOR, ModuleKind.SQUARE_OSCILLATOR)) {
      var seq =
          StepSequencerTest.module()
              .withValue(Parameter.STEP_1, 57)
              .withValue(Parameter.STEP_2, 69);
      var osc = ModuleState.create(UUID.randomUUID(), seq.owner(), kind, 1, 64, 0);
      var speaker =
          ModuleState.create(UUID.randomUUID(), seq.owner(), ModuleKind.SPEAKER, 2, 64, 0)
              .runtime(true, true);
      var runtime = new StepSequencer(StepSequencerSettings.from(seq), 0);
      runtime.start(0);
      var snapshot =
          new PatchSnapshot(
              seq.id(),
              1,
              1,
              0,
              List.of(seq, osc, speaker),
              List.of(
                  new Cable(seq.id(), Port.CV_OUT, osc.id(), Port.PITCH_IN),
                  new Cable(osc.id(), Port.AUDIO_OUT, speaker.id(), Port.AUDIO_IN)),
              Map.of(),
              Map.of(),
              Map.of(),
              Map.of(),
              Map.of(),
              Map.of(seq.id(), runtime.checkpoint(0)));
      var graph = new RenderGraph(List.of(snapshot));
      var listener = new RenderGraph.Listener("minecraft:overworld", 2.5, 64.5, .5, 0, 1);
      float[] block = new float[256];
      double previous = 0;
      int low = 0, high = 0;
      for (int sample = 0; sample < 12000; sample += 128) {
        graph.process(sample, listener, block);
        for (int i = 0; i < 128; i++) {
          double value = block[i * 2];
          assertTrue(Double.isFinite(value));
          int at = sample + i;
          if (previous <= 0 && value > 0) {
            if (at >= 1500 && at < 5000) low++;
            if (at >= 7500 && at < 11000) high++;
          }
          previous = value;
        }
      }
      assertTrue(low > 10);
      assertEquals(low * 2, high, 2, "Octave change must double pitch in " + kind);
    }
  }

  @Test
  void maximumMelodicSnapshotFitsExistingBudget() {
    List<ModuleState> modules = new ArrayList<>();
    for (int i = 0; i < 20; i++)
      modules.add(
          ModuleState.create(
              new UUID(0, i + 1), new UUID(1, 1), ModuleKind.STEP_SEQUENCER, i, 64, 0));
    var snapshot =
        new PatchSnapshot(modules.getFirst().id(), 1, 1, 0, modules, List.of(), Map.of());
    byte[] bytes = StateCodec.encode(snapshot);
    assertTrue(bytes.length <= 32768);
    assertEquals(snapshot, StateCodec.decode(bytes));
  }
}
