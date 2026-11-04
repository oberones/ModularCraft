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
import com.malevolentgods.modularcraft.dsp.Oscillator;
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

class DetuneTest {
  private static final UUID OWNER = new UUID(0, 1), OSCILLATOR = new UUID(1, 1);
  private static final RenderGraph.Listener LISTENER =
      new RenderGraph.Listener("minecraft:overworld", 2.5, .5, .5, 0, 1);

  private static Parameter detune() {
    return Parameter.valueOf("DETUNE");
  }

  private static PatchSnapshot patch(double spread, int note) {
    ModuleState oscillator =
        ModuleState.create(OSCILLATOR, OWNER, ModuleKind.OSCILLATOR, 0, 0, 0)
            .withValue(detune(), spread);
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

  private static double[] render(RenderGraph graph, int frames) {
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
  void spreadDefaultsToZeroAndRejectsInvalidOrForeignEdits() {
    Parameter control = detune();
    Assertions.assertEquals(0, ModuleKind.OSCILLATOR.defaults().get(control));
    for (double valid : new double[] {0, .125, 25, 100})
      Assertions.assertEquals(valid, control.validate(valid));
    for (double invalid : new double[] {-1, 100.001, Double.NaN, Double.POSITIVE_INFINITY})
      Assertions.assertThrows(IllegalArgumentException.class, () -> control.validate(invalid));
    WorldState world = new WorldState();
    ModuleState oscillator = patch(0, -1).modules().getFirst();
    world.place(oscillator);
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> world.edit(new UUID(0, 9), false, OSCILLATOR, oscillator.revision(), control, 25));
    world.edit(OWNER, false, OSCILLATOR, oscillator.revision(), control, 25);
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> world.edit(OWNER, false, OSCILLATOR, oscillator.revision(), control, 50));
    Assertions.assertEquals(25, world.get(OSCILLATOR).value(control));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> patch(0, -1).modules().get(1).withValue(control, 25));
  }

  @Test
  void zeroSpreadRetainsSingleSawOutput() {
    double[] output = render(new RenderGraph(List.of(patch(0, -1))), 4096);
    Oscillator original = new Oscillator();
    for (int sample = 0; sample < output.length; sample++) {
      double expected = original.next(220, .5) * .1 * Math.sqrt(.5);
      if (sample >= 480) Assertions.assertEquals(expected, output[sample], 1e-8);
    }
  }

  @Test
  void spreadProducesCenterAndBothSidePitchesForManualAndMidiInput() {
    for (int note : new int[] {-1, 69}) {
      double[] output = render(new RenderGraph(List.of(patch(100, note))), 96000);
      double scale = note < 0 ? 1 : 2;
      for (double hz : new double[] {207.65234878997256, 220, 233.08188075904496}) {
        double power = MeasurementTest.power(output, hz * scale);
        Assertions.assertTrue(
            power > .2 * MeasurementTest.power(output, 220 * scale),
            "Missing unison voice at " + hz * scale);
        Assertions.assertTrue(power > 50 * MeasurementTest.power(output, hz * scale + 3));
      }
    }
  }

  @Test
  void fractionalSpreadAndCvDisconnectKeepUnison() {
    RenderGraph connected = new RenderGraph(List.of(patch(12.5, 69)));
    render(connected, 1024);
    RenderGraph disconnected = new RenderGraph(List.of(patch(12.5, -1)));
    disconnected.adopt(connected);
    render(disconnected, 1024);
    double[] output = render(disconnected, 96000);
    for (double hz : new double[] {218.41725850807916, 220, 221.59421068921486})
      Assertions.assertTrue(
          MeasurementTest.power(output, hz) > .2 * MeasurementTest.power(output, 220),
          "Missing pitch " + hz);
  }

  @Test
  void liveSpreadChangesPreserveContinuityAndReturnToSingleSaw() {
    RenderGraph original = new RenderGraph(List.of(patch(0, -1)));
    render(original, 2048);
    RenderGraph spread = new RenderGraph(List.of(patch(100, -1)));
    spread.adopt(original);
    double[] changed = render(spread, 1024);
    double[] unchanged = render(original, 1024);
    Assertions.assertEquals(unchanged[0], changed[0], 1e-5, "No phase restart at edit");
    RenderGraph restored = new RenderGraph(List.of(patch(0, -1)));
    restored.adopt(spread);
    double[] single = render(restored, 2048);
    double[] reference = render(original, 2048);
    for (int i = 480; i < single.length; i++)
      Assertions.assertEquals(reference[i], single[i], 1e-8, "Center phase retained");
  }

  @Test
  void extremeMidiUnisonRemainsFiniteAndWithinSelectedLevel() {
    for (int note : new int[] {0, 127})
      for (double sample : render(new RenderGraph(List.of(patch(100, note))), 48000)) {
        Assertions.assertTrue(Double.isFinite(sample));
        Assertions.assertTrue(Math.abs(sample) <= .5 * .1 * Math.sqrt(.5));
      }
  }

  @Test
  void storedDetuneAndSnapshotsRoundtripAndOldNetworkIsRefused() {
    PatchSnapshot patch = patch(25.125, 69);
    Assertions.assertEquals(patch, StateCodec.decode(StateCodec.encode(patch)));
    ModuleState oscillator = patch.modules().getFirst();
    Assertions.assertEquals(
        25.125, StateCodec.moduleBytes(StateCodec.moduleBytes(oscillator)).value(detune()));
    WorldState world = new WorldState();
    world.place(oscillator);
    var loaded = StateCodec.load(StateCodec.save(world, List.of()));
    Assertions.assertEquals(25.125, loaded.state().get(OSCILLATOR).value(detune()));
    for (int version : new int[] {1, 2, 3}) {
      byte[] old = StateCodec.encode(patch);
      old[5] = (byte) version;
      Assertions.assertThrows(IllegalArgumentException.class, () -> StateCodec.decode(old));
    }
  }

  @Test
  void legacyWorldAndBlockRecordsMigrateOnlyTheExactOldControlSet() throws IOException {
    byte[] old = storedOscillator(new String[] {"FREQUENCY", "LEVEL"}, new double[] {330, 37});
    ModuleState module = StateCodec.moduleBytes(old);
    Assertions.assertEquals(0, module.value(detune()));
    Assertions.assertEquals(330, module.value(Parameter.FREQUENCY));
    Assertions.assertEquals(37, module.value(Parameter.LEVEL));
    Assertions.assertEquals(7, module.revision());
    var loaded = StateCodec.load(worldRecord(old));
    Assertions.assertTrue(loaded.quarantined().isEmpty());
    Assertions.assertEquals(module, loaded.state().get(OSCILLATOR));
    byte[] migrated = StateCodec.save(loaded.state(), loaded.quarantined());
    var again = StateCodec.load(migrated);
    Assertions.assertArrayEquals(migrated, StateCodec.save(again.state(), again.quarantined()));
    for (byte[] bad :
        List.of(
            storedOscillator(new String[] {"LEVEL"}, new double[] {37}),
            storedOscillator(new String[] {"FREQUENCY", "LEVEL"}, new double[] {Double.NaN, 37}),
            storedOscillator(
                new String[] {"FREQUENCY", "LEVEL", "DETUNE"}, new double[] {330, 37, -1}),
            storedOscillator(
                new String[] {"FREQUENCY", "LEVEL", "DETUNE", "DETUNE"},
                new double[] {330, 37, 1, 2}))) {
      Assertions.assertThrows(IllegalArgumentException.class, () -> StateCodec.moduleBytes(bad));
      byte[] original = worldRecord(bad);
      var quarantined = StateCodec.load(original);
      Assertions.assertEquals(1, quarantined.quarantined().size());
      Assertions.assertArrayEquals(
          original, StateCodec.save(quarantined.state(), quarantined.quarantined()));
    }
  }

  @Test
  void currentNetworkCannotOmitTheDetuneControl() throws IOException {
    byte[] legacy = storedOscillator(new String[] {"FREQUENCY", "LEVEL"}, new double[] {330, 37});
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeInt(0x4d435301);
      out.writeShort(4);
      out.writeLong(OSCILLATOR.getMostSignificantBits());
      out.writeLong(OSCILLATOR.getLeastSignificantBits());
      out.writeLong(0);
      out.writeLong(1);
      out.writeLong(0);
      out.writeShort(1);
      out.write(legacy);
      out.writeShort(0);
      out.writeShort(0);
      out.writeShort(0);
      byte[] packet = bytes.toByteArray();
      Assertions.assertThrows(IllegalArgumentException.class, () -> StateCodec.decode(packet));
    }
  }

  private static byte[] storedOscillator(String[] names, double[] values) throws IOException {
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeLong(OSCILLATOR.getMostSignificantBits());
      out.writeLong(OSCILLATOR.getLeastSignificantBits());
      out.writeLong(OWNER.getMostSignificantBits());
      out.writeLong(OWNER.getLeastSignificantBits());
      out.writeUTF("OSCILLATOR");
      out.writeUTF("minecraft:overworld");
      out.writeInt(0);
      out.writeInt(0);
      out.writeInt(0);
      out.writeLong(7);
      out.writeByte(names.length);
      for (int i = 0; i < names.length; i++) {
        out.writeUTF(names[i]);
        out.writeDouble(values[i]);
      }
      out.writeBoolean(false);
      out.writeBoolean(false);
      return bytes.toByteArray();
    }
  }

  private static byte[] worldRecord(byte[] module) throws IOException {
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeInt(0x4d435301);
      out.writeShort(1);
      out.writeShort(1);
      out.writeShort(module.length + 1);
      out.writeByte(1);
      out.write(module);
      return bytes.toByteArray();
    }
  }
}
