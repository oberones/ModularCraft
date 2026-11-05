package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.Graph;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.domain.StateCodec;
import com.malevolentgods.modularcraft.domain.WorldState;
import com.malevolentgods.modularcraft.dsp.RenderGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MixerTest {
  static final UUID OWNER = new UUID(0, 1), MIXER = new UUID(0, 2);

  static Parameter parameter(int channel, String control) {
    return Parameter.valueOf("MIX_" + (channel == 4 ? "MASTER" : channel + 1) + "_" + control);
  }

  static Port input(int channel) {
    return Port.valueOf("AUDIO_IN_" + (channel + 1));
  }

  static ModuleState mixer() {
    return ModuleState.create(MIXER, OWNER, ModuleKind.valueOf("MIXER"), 1, 0, 0);
  }

  static PatchSnapshot patch(ModuleState mixer, int inputs) {
    List<ModuleState> modules = new ArrayList<>();
    List<Cable> cables = new ArrayList<>();
    modules.add(mixer);
    for (int i = 0; i < inputs; i++) {
      ModuleState source =
          ModuleState.create(new UUID(1, i), OWNER, ModuleKind.OSCILLATOR, 0, 0, i);
      modules.add(source);
      cables.add(new Cable(source.id(), Port.AUDIO_OUT, MIXER, input(i)));
    }
    ModuleState speaker =
        ModuleState.create(new UUID(2, 0), OWNER, ModuleKind.SPEAKER, 2, 0, 0).runtime(true, true);
    modules.add(speaker);
    cables.add(new Cable(MIXER, Port.AUDIO_OUT, speaker.id(), Port.AUDIO_IN));
    return new PatchSnapshot(MIXER, 0, 1, 0, modules, cables, Map.of());
  }

  @Test
  void defaultsValidationAndAuthority() {
    ModuleState m = mixer();
    Assertions.assertEquals(14, m.values().size());
    Assertions.assertEquals(5, m.kind().ports.size());
    for (int i = 0; i < 5; i++) {
      Assertions.assertEquals(i == 4 ? 25 : 100, m.value(parameter(i, "LEVEL")));
      Assertions.assertEquals(0, m.value(parameter(i, "MUTE")));
      if (i < 4) Assertions.assertEquals(0, m.value(parameter(i, "SOLO")));
      Assertions.assertEquals(25.125, parameter(i, "LEVEL").validate(25.125));
      for (double invalid : new double[] {-1, 101, Double.NaN, Double.POSITIVE_INFINITY})
        Assertions.assertThrows(
            IllegalArgumentException.class, () -> parameter(0, "LEVEL").validate(invalid));
      Assertions.assertThrows(
          IllegalArgumentException.class, () -> parameter(0, "MUTE").validate(.5));
      Assertions.assertThrows(
          IllegalArgumentException.class, () -> parameter(0, "SOLO").validate(.5));
    }
    WorldState world = new WorldState();
    world.place(m);
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> world.edit(new UUID(9, 9), false, MIXER, 0, parameter(0, "LEVEL"), 20));
    world.edit(OWNER, false, MIXER, 0, parameter(0, "LEVEL"), 20);
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> world.edit(OWNER, false, MIXER, 0, parameter(0, "LEVEL"), 30));
  }

  @Test
  void weightedSumMuteSoloSilenceAndNoAutomaticNormalization() {
    double[] reference = render(mixer(), 1);
    compare(reference, render(mixer(), 4), 4);
    compare(reference, render(mixer().withValue(parameter(0, "LEVEL"), 50), 1), .5);
    compare(reference, render(mixer().withValue(parameter(4, "LEVEL"), 50), 1), 2);
    compare(reference, render(mixer().withValue(parameter(1, "MUTE"), 1), 4), 3);
    ModuleState solo =
        mixer().withValue(parameter(0, "SOLO"), 1).withValue(parameter(1, "SOLO"), 1);
    compare(reference, render(solo, 4), 2);
    compare(reference, render(solo.withValue(parameter(1, "MUTE"), 1), 4), 1);
    compare(reference, render(solo.withValue(parameter(4, "MUTE"), 1), 4), 0);
    compare(reference, render(mixer(), 0), 0);
    compare(reference, render(mixer().withValue(parameter(3, "SOLO"), 1), 1), 0);
    Assertions.assertTrue(java.util.Arrays.stream(reference).anyMatch(v -> Math.abs(v) > .001));
  }

  static double[] render(ModuleState m, int inputs) {
    return SquareOscillatorTest.render(new RenderGraph(List.of(patch(m, inputs))), 2048);
  }

  static void compare(double[] reference, double[] actual, double gain) {
    for (int i = 0; i < reference.length; i++)
      Assertions.assertEquals(reference[i] * gain, actual[i], 1e-7);
  }

  @Test
  void persistenceProtocolAndConnectionRules() {
    ModuleState m =
        mixer().withValue(parameter(1, "SOLO"), 1).withValue(parameter(4, "LEVEL"), 13.125);
    Assertions.assertEquals(m.values(), StateCodec.moduleBytes(StateCodec.moduleBytes(m)).values());
    PatchSnapshot p = patch(m, 4);
    Assertions.assertEquals(p, StateCodec.decode(StateCodec.encode(p)));
    byte[] old = StateCodec.encode(p);
    old[5] = 7;
    Assertions.assertThrows(IllegalArgumentException.class, () -> StateCodec.decode(old));
    Assertions.assertEquals(p.modules().size(), Graph.compile(p.modules(), p.cables()).size());
    List<Cable> occupied = new ArrayList<>(p.cables());
    occupied.add(new Cable(p.modules().get(2).id(), Port.AUDIO_OUT, MIXER, input(0)));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> Graph.compile(p.modules(), occupied));
    Assertions.assertFalse(Port.CV_OUT.connects(input(0)));
    Assertions.assertFalse(Port.CONTROL_OUT.connects(input(0)));
  }

  @Test
  void partialAndForeignMixerSaveSchemasAreRejected() throws java.io.IOException {
    var parameters = mixer().kind().parameters;
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            StateCodec.moduleBytes(
                LfoCodecTest.legacy(mixer().kind(), parameters.subList(1, parameters.size()))));
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            StateCodec.moduleBytes(LfoCodecTest.legacy(mixer().kind(), List.of(Parameter.LEVEL))));
  }

  static List<PatchSnapshot> loadPatches() {
    List<PatchSnapshot> patches = new ArrayList<>();
    for (int patch = 0; patch < 4; patch++) {
      List<ModuleState> modules = new ArrayList<>();
      List<Cable> cables = new ArrayList<>();
      for (int i = 0; i < 16; i++) {
        ModuleKind kind =
            i < 4
                ? (i % 2 == 0 ? ModuleKind.OSCILLATOR : ModuleKind.SQUARE_OSCILLATOR)
                : i < 14 ? ModuleKind.MIXER : ModuleKind.SPEAKER;
        ModuleState m =
            ModuleState.create(new UUID(patch + 10, i), OWNER, kind, i % 4 * 2, 0, i / 4 * 2);
        if (kind == ModuleKind.MIXER) m = m.withValue(parameter(4, "LEVEL"), 100);
        if (kind == ModuleKind.OSCILLATOR) m = m.withValue(Parameter.DETUNE, 100);
        if (kind == ModuleKind.SPEAKER) m = m.runtime(true, true);
        modules.add(m);
      }
      for (int i = 0; i < 4; i++)
        cables.add(new Cable(modules.get(i).id(), Port.AUDIO_OUT, modules.get(4).id(), input(i)));
      for (int i = 5; i < 14; i++)
        for (int j = 0; j < 2; j++)
          cables.add(
              new Cable(modules.get(i - 1).id(), Port.AUDIO_OUT, modules.get(i).id(), input(j)));
      for (int i = 14; i < 16; i++)
        cables.add(
            new Cable(modules.get(13).id(), Port.AUDIO_OUT, modules.get(i).id(), Port.AUDIO_IN));
      patches.add(new PatchSnapshot(modules.getFirst().id(), 0, 0, 0, modules, cables, Map.of()));
    }
    return patches;
  }

  @Test
  void chainedMixersRemainLinearAndFeedbackIsRefused() {
    var patch = patch(mixer(), 4);
    var chained = ModuleState.create(new UUID(3, 0), OWNER, ModuleKind.MIXER, 3, 0, 0);
    List<ModuleState> modules = new ArrayList<>(patch.modules());
    modules.add(chained);
    List<Cable> cables = new ArrayList<>(patch.cables());
    cables.removeLast();
    cables.add(new Cable(MIXER, Port.AUDIO_OUT, chained.id(), input(0)));
    cables.add(new Cable(chained.id(), Port.AUDIO_OUT, new UUID(2, 0), Port.AUDIO_IN));
    var result = new PatchSnapshot(MIXER, 0, 1, 0, modules, cables, Map.of());
    compare(
        render(mixer(), 4),
        SquareOscillatorTest.render(new RenderGraph(List.of(result)), 2048),
        .25);
    cables.remove(0);
    cables.add(new Cable(chained.id(), Port.AUDIO_OUT, MIXER, input(0)));
    Assertions.assertThrows(IllegalArgumentException.class, () -> Graph.compile(modules, cables));
  }

  @Test
  void maximumMixerLoadIsBoundedAndFinalOutputProtected() {
    var patches = loadPatches();
    MeasurementTest.benchmark(patches);
    var engine = new com.malevolentgods.modularcraft.dsp.AudioEngine();
    Assertions.assertTrue(engine.submit(0, 1, new RenderGraph(patches)));
    float[] out = new float[256];
    var listener = new RenderGraph.Listener("minecraft:overworld", 3, 0, 3, 0, 1);
    for (int block = 0; block < 100; block++) {
      engine.render(block * 128L, listener, out);
      for (float value : out) Assertions.assertTrue(Float.isFinite(value) && Math.abs(value) <= 1);
    }
  }
}
