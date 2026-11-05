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
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SquareOscillatorTest {
  private static final UUID OWNER = new UUID(0, 1), OSCILLATOR = new UUID(1, 1);
  private static final RenderGraph.Listener LISTENER =
      new RenderGraph.Listener("minecraft:overworld", 2.5, .5, .5, 0, 1);

  static Parameter width() {
    return Parameter.valueOf("PULSE_WIDTH");
  }

  static ModuleKind square() {
    return ModuleKind.valueOf("SQUARE_OSCILLATOR");
  }

  static PatchSnapshot patch(double width, double hz, int note) {
    ModuleState oscillator =
        ModuleState.create(OSCILLATOR, OWNER, square(), 0, 0, 0)
            .withValue(width(), width)
            .withValue(Parameter.FREQUENCY, hz);
    ModuleState speaker =
        ModuleState.create(new UUID(1, 2), OWNER, ModuleKind.SPEAKER, 2, 0, 0).runtime(true, true);
    ModuleState keyboard = ModuleState.create(new UUID(1, 3), OWNER, ModuleKind.KEYBOARD, 4, 0, 0);
    List<Cable> cables = new ArrayList<>();
    cables.add(new Cable(OSCILLATOR, Port.AUDIO_OUT, speaker.id(), Port.AUDIO_IN));
    if (note >= 0) cables.add(new Cable(keyboard.id(), Port.CV_OUT, OSCILLATOR, Port.PITCH_IN));
    return new PatchSnapshot(
        OSCILLATOR,
        0,
        1,
        0,
        List.of(oscillator, speaker, keyboard),
        cables,
        Map.of(),
        Map.of(keyboard.id(), new KeyboardState(Math.max(0, note), false)));
  }

  static double[] render(RenderGraph graph, int frames) {
    double[] result = new double[frames];
    float[] buffer = new float[256];
    for (int offset = 0; offset < frames; offset += 128) {
      int count = Math.min(128, frames - offset);
      graph.process(offset, LISTENER, buffer, count);
      for (int i = 0; i < count; i++) result[offset + i] = buffer[2 * i];
    }
    return result;
  }

  @Test
  void defaultsFractionalBoundsAndAuthority() {
    Assertions.assertEquals(
        List.of(Parameter.FREQUENCY, width(), Parameter.LEVEL), square().parameters);
    Assertions.assertEquals(50, square().defaults().get(width()));
    Assertions.assertEquals(ModuleKind.OSCILLATOR.ports, square().ports);
    for (double valid : new double[] {5, 25.125, 50, 95})
      Assertions.assertEquals(valid, width().validate(valid));
    for (double invalid : new double[] {4.999, 95.001, Double.NaN, Double.POSITIVE_INFINITY})
      Assertions.assertThrows(IllegalArgumentException.class, () -> width().validate(invalid));
    Assertions.assertThrows(IllegalArgumentException.class, () -> Parameter.TEMPO.validate(120.5));
    Assertions.assertThrows(IllegalArgumentException.class, () -> Parameter.A_STEPS.validate(4.5));
    ModuleState oscillator = patch(50, 220, -1).modules().getFirst();
    WorldState world = new WorldState();
    world.place(oscillator);
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> world.edit(new UUID(0, 9), false, OSCILLATOR, oscillator.revision(), width(), 25));
    world.edit(OWNER, false, OSCILLATOR, oscillator.revision(), width(), 25.125);
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> world.edit(OWNER, false, OSCILLATOR, oscillator.revision(), width(), 50));
    Assertions.assertEquals(25.125, world.get(OSCILLATOR).value(width()));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> oscillator.withValue(Parameter.DETUNE, 25));
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            ModuleState.create(new UUID(2, 1), OWNER, ModuleKind.OSCILLATOR, 0, 0, 0)
                .withValue(width(), 50));
  }

  @Test
  void squareAndPulseHaveExpectedPitchAndHarmonics() {
    for (int note : new int[] {-1, 69}) {
      double hz = note < 0 ? 220 : 440;
      double[] symmetric = render(new RenderGraph(List.of(patch(50, 220, note))), 96000);
      double fundamental = MeasurementTest.power(symmetric, hz);
      Assertions.assertTrue(fundamental > .00001);
      Assertions.assertTrue(MeasurementTest.power(symmetric, hz * 2) < fundamental * .001);
      Assertions.assertTrue(MeasurementTest.power(symmetric, hz * 3) > fundamental * .08);
      double[] pulse = render(new RenderGraph(List.of(patch(25, 220, note))), 96000);
      Assertions.assertTrue(
          MeasurementTest.power(pulse, hz * 2) > MeasurementTest.power(pulse, hz) * .3);
      Assertions.assertTrue(
          MeasurementTest.power(pulse, hz * 4) < MeasurementTest.power(pulse, hz) * .001);
    }
  }

  @Test
  void liveWidthPitchAndLevelEditsKeepPhaseAndDisconnectRestoresManualPitch() {
    RenderGraph original = new RenderGraph(List.of(patch(50, 220, 69)));
    render(original, 2048);
    RenderGraph changed = new RenderGraph(List.of(patch(5, 220, -1)));
    changed.adopt(original);
    double[] actual = render(changed, 1024), reference = render(original, 1024);
    Assertions.assertEquals(reference[0], actual[0], 1e-5, "No phase restart");
    double[] settled = render(changed, 96000);
    Assertions.assertTrue(
        MeasurementTest.power(settled, 220) > 100 * MeasurementTest.power(settled, 221));
    RenderGraph same = new RenderGraph(List.of(patch(5, 220, -1)));
    same.adopt(changed);
    Assertions.assertArrayEquals(render(changed, 1024), render(same, 1024), 1e-8);
  }

  @Test
  void extremesStayFiniteAndBounded() {
    for (double width : new double[] {5, 50, 95})
      for (double hz : new double[] {20, 8000})
        for (int note : new int[] {-1, 0, 127})
          for (double value : render(new RenderGraph(List.of(patch(width, hz, note))), 4096)) {
            Assertions.assertTrue(Double.isFinite(value));
            Assertions.assertTrue(Math.abs(value) <= .5 * .1 * Math.sqrt(.5));
          }
  }

  @Test
  void malformedStoredRecordsAreQuarantinedWithoutApplyingSawMigration() throws IOException {
    for (String[] names :
        List.of(
            new String[] {"FREQUENCY", "LEVEL"},
            new String[] {"FREQUENCY", "LEVEL", "DETUNE"},
            new String[] {"FREQUENCY", "PULSE_WIDTH", "PULSE_WIDTH"},
            new String[] {"FREQUENCY", "LEVEL", "PULSE_WIDTH"})) {
      byte[] module;
      try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
          DataOutputStream out = new DataOutputStream(bytes)) {
        out.writeLong(OSCILLATOR.getMostSignificantBits());
        out.writeLong(OSCILLATOR.getLeastSignificantBits());
        out.writeLong(OWNER.getMostSignificantBits());
        out.writeLong(OWNER.getLeastSignificantBits());
        out.writeUTF(square().name());
        out.writeUTF("minecraft:overworld");
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
        out.writeLong(0);
        out.writeByte(names.length);
        for (String name : names) {
          out.writeUTF(name);
          out.writeDouble(
              name.equals("FREQUENCY") ? 220 : name.equals("PULSE_WIDTH") ? Double.NaN : 50);
        }
        out.writeBoolean(false);
        out.writeBoolean(false);
        module = bytes.toByteArray();
      }
      Assertions.assertThrows(IllegalArgumentException.class, () -> StateCodec.moduleBytes(module));
      try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
          DataOutputStream out = new DataOutputStream(bytes)) {
        out.writeInt(0x4d435301);
        out.writeShort(1);
        out.writeShort(1);
        out.writeShort(module.length + 1);
        out.writeByte(1);
        out.write(module);
        byte[] original = bytes.toByteArray();
        var loaded = StateCodec.load(original);
        Assertions.assertEquals(1, loaded.quarantined().size());
        Assertions.assertArrayEquals(
            original, StateCodec.save(loaded.state(), loaded.quarantined()));
      }
      byte[] valid = StateCodec.encode(patch(50, 220, -1));
      int marker = -1;
      byte[] name = "PULSE_WIDTH".getBytes(java.nio.charset.StandardCharsets.UTF_8);
      for (int i = 0; i < valid.length - name.length - 8; i++) {
        if (java.util.Arrays.equals(name, 0, name.length, valid, i, i + name.length)) {
          marker = i + name.length;
          break;
        }
      }
      Assertions.assertTrue(marker >= 0);
      java.nio.ByteBuffer.wrap(valid).putDouble(marker, Double.NaN);
      Assertions.assertThrows(IllegalArgumentException.class, () -> StateCodec.decode(valid));
    }
  }

  @Test
  void levelEditRampsToSilenceWithoutResettingPulse() {
    PatchSnapshot original = patch(25, 440, -1);
    RenderGraph before = new RenderGraph(List.of(original));
    render(before, 2048);
    List<ModuleState> modules =
        original.modules().stream()
            .map(m -> m.kind() == square() ? m.withValue(Parameter.LEVEL, 0) : m)
            .toList();
    RenderGraph after =
        new RenderGraph(
            List.of(
                new PatchSnapshot(
                    original.id(),
                    1,
                    2,
                    0,
                    modules,
                    original.cables(),
                    original.envelopes(),
                    original.keyboards())));
    after.adopt(before);
    double[] samples = render(after, 1024), unchanged = render(before, 1024);
    Assertions.assertEquals(unchanged[0], samples[0], .0001);
    for (int i = 1; i < samples.length; i++)
      Assertions.assertTrue(Math.abs(samples[i] - samples[i - 1]) < .02);
    for (int i = 480; i < samples.length; i++) Assertions.assertEquals(0, samples[i]);
  }

  @Test
  void persistenceAndSnapshotsRoundtripWithExplicitOldPeerRefusal() {
    PatchSnapshot patch = patch(25.125, 432.123, 69);
    Assertions.assertEquals(patch, StateCodec.decode(StateCodec.encode(patch)));
    ModuleState module = patch.modules().getFirst();
    Assertions.assertEquals(
        module.runtime(false, false), StateCodec.moduleBytes(StateCodec.moduleBytes(module)));
    WorldState world = new WorldState();
    world.place(module);
    var loaded = StateCodec.load(StateCodec.save(world, List.of()));
    Assertions.assertEquals(25.125, loaded.state().get(OSCILLATOR).value(width()));
    byte[] current = StateCodec.encode(patch);
    Assertions.assertEquals(7, current[5]);
    for (int version = 1; version <= 6; version++) {
      byte[] old = current.clone();
      old[5] = (byte) version;
      Assertions.assertThrows(IllegalArgumentException.class, () -> StateCodec.decode(old));
    }
  }
}
