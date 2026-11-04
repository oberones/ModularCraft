package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.dsp.AudioEngine;
import com.malevolentgods.modularcraft.dsp.RenderGraph;
import com.malevolentgods.modularcraft.transport.ClockSync;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AudioRuntimeTest {
  @Test
  void clockMidpointCompensationAndHourDrift() {
    ClockSync clock = new ClockSync();
    clock.observe(1_000_000_000L, 1_050_000_000L, 48000);
    Assertions.assertTrue(clock.ready());
    Assertions.assertEquals(49200, clock.sample(1_050_000_000L));
    Assertions.assertEquals(172849200, clock.sample(3_601_050_000_000L));
  }

  @Test
  void futureGraphDoesNotPlayEarlyAndOutputIsBounded() {
    AudioEngine engine = new AudioEngine();
    float[] out = new float[256];
    var listener = new RenderGraph.Listener("minecraft:overworld", 6.5, .5, .5, 0, 1);
    Assertions.assertTrue(
        engine.submit(200064, 1, new RenderGraph(List.of(IntegrationCoreTest.reference(false)))));
    engine.render(199936, listener, out);
    for (float v : out) Assertions.assertEquals(0, v);
    engine.render(200064, listener, out);
    double energy = 0;
    for (int i = 0; i < 10; i++) {
      engine.render(200192 + i * 128L, listener, out);
      for (float v : out) {
        Assertions.assertTrue(Float.isFinite(v) && Math.abs(v) <= 1);
        energy += v * v;
      }
    }
    Assertions.assertTrue(energy > .001);
  }

  @Test
  void preparationHandoffOverflowFailsSilent() {
    AudioEngine engine = new AudioEngine();
    RenderGraph graph = new RenderGraph(List.of());
    for (int i = 0; i < 8; i++) Assertions.assertTrue(engine.submit(i, i, graph));
    Assertions.assertFalse(engine.submit(9, 9, graph));
    Assertions.assertTrue(engine.failed());
    float[] output = new float[256];
    engine.render(0, new RenderGraph.Listener("minecraft:overworld", 0, 0, 0, 0, 1), output);
    for (float sample : output) Assertions.assertEquals(0, sample);
  }
}
