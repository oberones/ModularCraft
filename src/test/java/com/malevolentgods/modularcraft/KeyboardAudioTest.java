package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.KeyboardState;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.domain.StateCodec;
import com.malevolentgods.modularcraft.domain.WorldState;
import com.malevolentgods.modularcraft.dsp.RenderGraph;
import com.malevolentgods.modularcraft.transport.EditInbox;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class KeyboardAudioTest {
  private static final UUID OWNER = new UUID(0, 1);

  private static ModuleState module(int id, ModuleKind kind) {
    return ModuleState.create(new UUID(1, id), OWNER, kind, id, 0, 0);
  }

  private static PatchSnapshot patch(boolean connected, boolean gate, int note) {
    ModuleState keyboard = module(0, ModuleKind.KEYBOARD);
    ModuleState oscillator = module(1, ModuleKind.OSCILLATOR);
    ModuleState speaker = module(2, ModuleKind.SPEAKER).runtime(true, true);
    List<Cable> cables =
        new ArrayList<>(
            List.of(new Cable(oscillator.id(), Port.AUDIO_OUT, speaker.id(), Port.AUDIO_IN)));
    if (connected)
      cables.add(new Cable(keyboard.id(), Port.CV_OUT, oscillator.id(), Port.PITCH_IN));
    return new PatchSnapshot(
        keyboard.id(),
        0,
        1,
        0,
        List.of(keyboard, oscillator, speaker),
        cables,
        Map.of(),
        Map.of(keyboard.id(), new KeyboardState(note, gate)));
  }

  private static double[] render(RenderGraph graph) {
    var listener = new RenderGraph.Listener("minecraft:overworld", 2.5, .5, .5, 0, 1);
    float[] block = new float[256];
    double[] samples = new double[48000];
    for (int sample = 0; sample < 96000; sample += 128) {
      graph.process(sample, listener, block);
      for (int i = 0; i < 128 && sample + i < 96000; i++)
        if (sample + i >= 48000) samples[sample + i - 48000] = block[2 * i];
    }
    return samples;
  }

  @Test
  void connectedPitchIsExactAndRetainedAfterGateRelease() {
    for (boolean gate : new boolean[] {true, false}) {
      double[] samples = render(new RenderGraph(List.of(patch(true, gate, 69))));
      Assertions.assertTrue(
          MeasurementTest.power(samples, 440) > 100 * MeasurementTest.power(samples, 220));
    }
  }

  @Test
  void disconnectRestoresSavedFrequencyAcrossGraphAdoption() {
    RenderGraph before = new RenderGraph(List.of(patch(true, true, 69)));
    render(before);
    RenderGraph after = new RenderGraph(List.of(patch(false, false, 69)));
    after.adopt(before);
    double[] samples = render(after);
    Assertions.assertTrue(
        MeasurementTest.power(samples, 220) > MeasurementTest.power(samples, 440));
  }

  @Test
  void versionOneWorldStillLoadsButVersionOneNetworkIsRefused() {
    WorldState world = new WorldState();
    ModuleState old = module(1, ModuleKind.OSCILLATOR).withValue(Parameter.FREQUENCY, 330);
    world.place(old);
    byte[] original = StateCodec.save(world, List.of(new byte[] {99, 1, 2}));
    var loaded = StateCodec.load(original);
    Assertions.assertEquals(330, loaded.state().get(old.id()).value(Parameter.FREQUENCY));
    Assertions.assertArrayEquals(original, StateCodec.save(loaded.state(), loaded.quarantined()));
    byte[] network = StateCodec.encode(patch(true, true, 60));
    network[5] = 1;
    Assertions.assertThrows(IllegalArgumentException.class, () -> StateCodec.decode(network));
  }

  @Test
  void pitchFanOutIsFourAndGateCannotFeedAudio() {
    WorldState world = new WorldState();
    ModuleState keyboard = module(0, ModuleKind.KEYBOARD);
    world.place(keyboard);
    for (int i = 1; i <= 5; i++) world.place(module(i, ModuleKind.OSCILLATOR));
    for (int i = 1; i <= 4; i++) {
      ModuleState target = world.get(new UUID(1, i));
      world.connect(
          OWNER,
          false,
          new Cable(keyboard.id(), Port.CV_OUT, target.id(), Port.PITCH_IN),
          world.get(keyboard.id()).revision(),
          target.revision());
    }
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            world.connect(
                OWNER,
                false,
                new Cable(keyboard.id(), Port.CV_OUT, new UUID(1, 5), Port.PITCH_IN),
                world.get(keyboard.id()).revision(),
                0));
    world.place(module(6, ModuleKind.SPEAKER));
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            world.connect(
                OWNER,
                false,
                new Cable(keyboard.id(), Port.GATE_OUT, new UUID(1, 6), Port.AUDIO_IN),
                world.get(keyboard.id()).revision(),
                0));
  }

  @Test
  void ordinaryKeyboardReleasePreservesPressAndEmergencyCleanupOvertakesIt() {
    EditInbox<String> queue = new EditInbox<>();
    queue.offer(OWNER, "", false, false, "note_on");
    queue.offer(OWNER, "", false, false, "note_off");
    Assertions.assertEquals("note_on", queue.poll());
    Assertions.assertEquals("note_off", queue.poll());
    for (int i = 0; i < 64; i++) queue.offer(OWNER, "", false, false, "note");
    Assertions.assertFalse(queue.offer(OWNER, "", false, false, "overflow"));
    Assertions.assertTrue(queue.offer(OWNER, "", false, true, "close_session"));
    Assertions.assertEquals("close_session", queue.poll());
  }
}
