package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.dsp.RenderGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LfoAudioTest {
  static PatchSnapshot patch(Port destination, double depth, int wave) {
    UUID owner = new UUID(0, 1);
    ModuleState osc = ModuleState.create(new UUID(1, 1), owner, ModuleKind.OSCILLATOR, 0, 0, 0);
    ModuleState filter =
        ModuleState.create(new UUID(1, 2), owner, ModuleKind.FILTER, 1, 0, 0)
            .withValue(Parameter.CUTOFF, 500)
            .withValue(Parameter.RESONANCE_DEPTH, depth)
            .withValue(Parameter.AMOUNT, 50);
    ModuleState speaker =
        ModuleState.create(new UUID(1, 3), owner, ModuleKind.SPEAKER, 2, 0, 0).runtime(true, true);
    ModuleState amp =
        ModuleState.create(new UUID(1, 5), owner, ModuleKind.AMP, 4, 0, 0)
            .withValue(Parameter.DRONE, 1);
    ModuleState lfo =
        ModuleState.create(new UUID(1, 4), owner, ModuleKind.LFO, 3, 0, 0)
            .withValue(Parameter.LFO_WAVE, wave)
            .withValue(Parameter.LFO_FREQUENCY, 1);
    List<Cable> cables =
        new ArrayList<>(
            List.of(
                new Cable(osc.id(), Port.AUDIO_OUT, filter.id(), Port.AUDIO_IN),
                new Cable(filter.id(), Port.AUDIO_OUT, amp.id(), Port.AUDIO_IN),
                new Cable(amp.id(), Port.AUDIO_OUT, speaker.id(), Port.AUDIO_IN)));
    if (destination != null)
      cables.add(
          new Cable(
              lfo.id(),
              Port.CONTROL_OUT,
              destination == Port.GAIN_IN ? amp.id() : filter.id(),
              destination));
    return new PatchSnapshot(
        osc.id(), 0, 1, 0, List.of(osc, filter, speaker, lfo, amp), cables, Map.of());
  }

  @Test
  void positiveGainPassesAudioAndNegativeGainClampsToSilence() {
    double[] actual =
        SquareOscillatorTest.render(new RenderGraph(List.of(patch(Port.GAIN_IN, 0, 4))), 48000);
    double energy = 0;
    for (int i = 1000; i < 20000; i++) energy += actual[i] * actual[i];
    Assertions.assertTrue(energy > .001);
    for (int i = 25000; i < 48000; i++) Assertions.assertEquals(0, actual[i], 1e-8);
  }

  @Test
  void cutoffAndResonanceInputsChangeRenderedSound() {
    double[] reference =
        SquareOscillatorTest.render(new RenderGraph(List.of(patch(null, 0, 4))), 8192);
    for (Port port : List.of(Port.CUTOFF_IN, Port.RESONANCE_IN)) {
      double[] changed =
          SquareOscillatorTest.render(new RenderGraph(List.of(patch(port, 50, 4))), 8192);
      double difference = 0;
      for (int i = 1000; i < 8192; i++) difference += Math.abs(reference[i] - changed[i]);
      Assertions.assertTrue(difference > .01, port.name());
    }
  }
}
