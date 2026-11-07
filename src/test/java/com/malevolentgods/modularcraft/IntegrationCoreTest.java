package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.domain.StateCodec;
import com.malevolentgods.modularcraft.domain.WorldState;
import com.malevolentgods.modularcraft.dsp.Envelope;
import com.malevolentgods.modularcraft.dsp.RenderGraph;
import com.malevolentgods.modularcraft.transport.Budgets;
import com.malevolentgods.modularcraft.transport.EventQueue;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class IntegrationCoreTest {
  private static final UUID OWNER = new UUID(0, 1);

  static PatchSnapshot reference(boolean gain) {
    ModuleState o = CoreTest.module(1, OWNER, ModuleKind.OSCILLATOR, 0),
        f = CoreTest.module(2, OWNER, ModuleKind.FILTER, 2);
    ModuleState e = CoreTest.module(3, OWNER, ModuleKind.ENVELOPE, 4),
        s = CoreTest.module(4, OWNER, ModuleKind.SPEAKER, 6).runtime(true, true),
        amp = CoreTest.module(5, OWNER, ModuleKind.AMP, 7).withValue(Parameter.DRONE, 1);
    var links =
        new java.util.ArrayList<>(
            List.of(
                new Cable(o.id(), Port.AUDIO_OUT, f.id(), Port.AUDIO_IN),
                new Cable(f.id(), Port.AUDIO_OUT, amp.id(), Port.AUDIO_IN),
                new Cable(amp.id(), Port.AUDIO_OUT, s.id(), Port.AUDIO_IN)));
    if (gain) links.add(new Cable(e.id(), Port.ENVELOPE_OUT, amp.id(), Port.GAIN_IN));
    Envelope env = new Envelope();
    env.gate(true, 0);
    env.gate(false, 96000);
    return new PatchSnapshot(
        o.id(),
        1,
        1,
        96000,
        List.of(o, f, e, s, amp),
        links,
        Map.of(e.id(), env.checkpoint(96000)));
  }

  @Test
  void referencePatchHasHeadroomAndReleaseSilence() {
    PatchSnapshot p = reference(true);
    RenderGraph graph = new RenderGraph(List.of(p));
    float[] out = new float[256];
    var listener = new RenderGraph.Listener("minecraft:overworld", 6.5, .5, .5, 0, 1);
    double peak = 0;
    for (long sample = 96000; sample < 120000; sample += 128) {
      graph.process(sample, listener, out);
      for (float v : out) peak = Math.max(peak, Math.abs(v));
    }
    Assertions.assertTrue(peak > .001 && peak < .5, "audible default with 6 dB headroom");
    graph.process(130000, listener, out);
    for (float v : out) Assertions.assertEquals(0, v, 1e-8);
  }

  @Test
  void disconnectedGainIsDroneButMissingAudioIsSilence() {
    PatchSnapshot p = reference(false);
    RenderGraph g = new RenderGraph(List.of(p));
    float[] out = new float[256];
    var listener = new RenderGraph.Listener("minecraft:overworld", 6.5, .5, .5, 0, 1);
    for (int i = 0; i < 10; i++) g.process(200000 + i * 128L, listener, out);
    double energy = 0;
    for (float v : out) energy += v * v;
    Assertions.assertTrue(energy > .001);
    g =
        new RenderGraph(
            List.of(
                new PatchSnapshot(p.id(), 2, 2, 200000, p.modules(), List.of(), p.envelopes())));
    g.process(200000, listener, out);
    for (float v : out) Assertions.assertEquals(0, v);
    g = new RenderGraph(List.of(p));
    g.process(200000, new RenderGraph.Listener("minecraft:the_nether", 6.5, .5, .5, 0, 1), out);
    for (float v : out) Assertions.assertEquals(0, v);
  }

  @Test
  void patchCodecRoundtripsAndRejectsTrailingOrHugeInput() {
    PatchSnapshot p = reference(true);
    byte[] encoded = StateCodec.encode(p);
    Assertions.assertEquals(p, StateCodec.decode(encoded));
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> StateCodec.decode(Arrays.copyOf(encoded, encoded.length + 1)));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> StateCodec.decode(new byte[32769]));
  }

  @Test
  void worldRoundtripClearsPerformanceAndRetainsUnknownBytes() {
    PatchSnapshot p = reference(true);
    WorldState w = new WorldState();
    for (ModuleState m : p.modules()) w.place(m.runtime(true, false));
    for (Cable c : p.cables())
      w.connect(OWNER, false, c, w.get(c.source()).revision(), w.get(c.destination()).revision());
    byte[] raw = {99, 23, 42};
    var loaded = StateCodec.load(StateCodec.save(w, List.of(raw)));
    Assertions.assertEquals(w.cables(), loaded.state().cables());
    Assertions.assertEquals(5, loaded.state().modules().size());
    Assertions.assertArrayEquals(raw, loaded.quarantined().getFirst());
    for (ModuleState m : loaded.state().modules())
      Assertions.assertFalse(m.enabled() || m.loaded());
    for (int i = 0; i < 20; i++)
      loaded = StateCodec.load(StateCodec.save(loaded.state(), loaded.quarantined()));
    Assertions.assertArrayEquals(raw, loaded.quarantined().getFirst());
  }

  @Test
  void fuzzNeverAcceptsMalformedSnapshot() {
    Random r = new Random(9217);
    int rejected = 0;
    for (int i = 0; i < 1000; i++) {
      byte[] bytes = new byte[r.nextInt(1024)];
      r.nextBytes(bytes);
      try {
        StateCodec.decode(bytes);
      } catch (IllegalArgumentException expected) {
        rejected++;
      }
    }
    Assertions.assertEquals(1000, rejected);
  }

  @Test
  void stableBoundedEventOrdering() {
    EventQueue<String> queue = new EventQueue<>();
    Assertions.assertTrue(queue.offer(10, 2, "second"));
    Assertions.assertTrue(queue.offer(10, 1, "first"));
    Assertions.assertNull(queue.poll(9));
    Assertions.assertEquals("first", queue.poll(10).value());
    Assertions.assertEquals("second", queue.poll(10).value());
    for (int i = 0; i < 1024; i++) Assertions.assertTrue(queue.offer(100, i, "bounded"));
    Assertions.assertFalse(queue.offer(100, 1025, "overflow"));
    Assertions.assertEquals(1024, queue.size());
    queue.clear();
    Assertions.assertEquals(0, queue.size());
  }

  @Test
  void perPlayerAndWorldThrottlesNeverBlockRelease() {
    Budgets b = new Budgets();
    for (int i = 0; i < 20; i++) Assertions.assertTrue(b.edit(OWNER, 0, false));
    Assertions.assertFalse(b.edit(OWNER, 0, false));
    Assertions.assertTrue(b.edit(OWNER, 0, true));
    Assertions.assertTrue(b.edit(OWNER, 1_000_000_000L, false));
    Assertions.assertFalse(b.bytes(OWNER, 0, 131073));
  }

  @Test
  void capacitiesAcrossUnloadedModulesAndActiveSplits() {
    WorldState w = new WorldState();
    for (int i = 0; i < 64; i++) w.place(CoreTest.module(i, OWNER, ModuleKind.OSCILLATOR, i));
    w.loaded(w.modules().getFirst().id(), false);
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> w.place(CoreTest.module(100, OWNER, ModuleKind.OSCILLATOR, 100)));
    Assertions.assertEquals(64, w.modules().size());
    WorldState active = new WorldState();
    for (int i = 0; i < 5; i++) active.place(CoreTest.module(i, OWNER, ModuleKind.SPEAKER, i));
    active.refreshPlayback();
    Assertions.assertEquals(4, active.modules().stream().filter(ModuleState::enabled).count());
    Assertions.assertFalse(active.get(new UUID(1, 4)).enabled());
    Assertions.assertTrue(active.get(new UUID(1, 4)).wantsPlayback());
  }
}
