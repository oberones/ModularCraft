package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.domain.SequencerSettings;
import com.malevolentgods.modularcraft.dsp.Envelope;
import com.malevolentgods.modularcraft.dsp.RenderGraph;
import com.malevolentgods.modularcraft.dsp.Sequencer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SequencerAudioTest {
  static PatchSnapshot fixture(int index) {
    UUID owner = new UUID(0, 1);
    ModuleState seq =
        ModuleState.create(
                new UUID(index, 1), owner, ModuleKind.EUCLIDEAN_SEQUENCER, index * 4, 64, 0)
            .withValue(Parameter.A_HITS, 16);
    ModuleState osc =
        ModuleState.create(new UUID(index, 2), owner, ModuleKind.OSCILLATOR, index * 4 + 1, 64, 0);
    ModuleState env =
        ModuleState.create(new UUID(index, 3), owner, ModuleKind.ENVELOPE, index * 4 + 2, 64, 0)
            .withValue(Parameter.RELEASE, .001);
    ModuleState amp =
        ModuleState.create(new UUID(index, 4), owner, ModuleKind.SPEAKER, index * 4 + 3, 64, 0)
            .runtime(true, true);
    Sequencer runtime = new Sequencer(SequencerSettings.from(seq), 0);
    runtime.start(0);
    Envelope envelope = new Envelope();
    envelope.configure(.01, .2, .7, .001, 0);
    return new PatchSnapshot(
        seq.id(),
        0,
        1,
        0,
        List.of(seq, osc, env, amp),
        List.of(
            new Cable(seq.id(), Port.TRIGGER_A, env.id(), Port.GATE_IN),
            new Cable(osc.id(), Port.AUDIO_OUT, amp.id(), Port.AUDIO_IN),
            new Cable(env.id(), Port.ENVELOPE_OUT, amp.id(), Port.GAIN_IN)),
        Map.of(env.id(), envelope.checkpoint(0)),
        Map.of(),
        Map.of(seq.id(), runtime.checkpoint(0)),
        Map.of(env.id(), false));
  }

  @Test
  void audioRendersSampleTimedPulsesWithoutFurtherSnapshots() {
    RenderGraph graph = new RenderGraph(List.of(fixture(0)));
    var listener = new RenderGraph.Listener("minecraft:overworld", 3.5, 64.5, .5, 0, 1);
    float[] block = new float[256];
    double first = 0, gap = 0, second = 0;
    for (int sample = 0; sample < 7000; sample += 128) {
      graph.process(sample, listener, block);
      for (int i = 0; i < 128; i++) {
        int at = sample + i;
        if (at < 480) first += Math.abs(block[2 * i]);
        if (at >= 1000 && at < 6000) gap += Math.abs(block[2 * i]);
        if (at >= 6000 && at < 6480) second += Math.abs(block[2 * i]);
      }
    }
    Assertions.assertTrue(first > .1);
    Assertions.assertEquals(0, gap, 1e-9);
    Assertions.assertTrue(second > .1);
  }
}
