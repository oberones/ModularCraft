package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.domain.StateCodec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AmpMigrationTest {
  private static byte[] speaker(double volume) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeLong(1);
      out.writeLong(3);
      out.writeLong(0);
      out.writeLong(1);
      out.writeUTF("SPEAKER");
      out.writeUTF("minecraft:overworld");
      out.writeInt(6);
      out.writeInt(64);
      out.writeInt(0);
      out.writeLong(17);
      out.writeByte(1);
      out.writeUTF("VOLUME");
      out.writeDouble(volume);
      out.writeBoolean(false);
      out.writeBoolean(false);
    }
    return bytes.toByteArray();
  }

  private static byte[] world(List<byte[]> records) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeInt(0x4d435301);
      out.writeShort(1);
      out.writeShort(records.size());
      for (byte[] record : records) {
        out.writeShort(record.length);
        out.write(record);
      }
    }
    return bytes.toByteArray();
  }

  private static byte[] moduleRecord(byte[] raw) {
    byte[] result = new byte[raw.length + 1];
    result[0] = 1;
    System.arraycopy(raw, 0, result, 1, raw.length);
    return result;
  }

  private static byte[] cableRecord(Cable cable) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeByte(2);
      out.writeLong(cable.source().getMostSignificantBits());
      out.writeLong(cable.source().getLeastSignificantBits());
      out.writeUTF(cable.output().name());
      out.writeLong(cable.destination().getMostSignificantBits());
      out.writeLong(cable.destination().getLeastSignificantBits());
      out.writeUTF(cable.input().name());
    }
    return bytes.toByteArray();
  }

  @Test
  void legacyRegistryAndBlockDecodeAgreeAndRetainBothCables() throws IOException {
    var oscillator = AmpAudioTest.module(1, ModuleKind.OSCILLATOR);
    var envelope = AmpAudioTest.module(2, ModuleKind.ENVELOPE);
    UUID id = new UUID(1, 3);
    List<Cable> cables =
        List.of(
            new Cable(oscillator.id(), Port.AUDIO_OUT, id, Port.AUDIO_IN),
            new Cable(envelope.id(), Port.ENVELOPE_OUT, id, Port.GAIN_IN));
    List<byte[]> records = new ArrayList<>();
    for (var m : List.of(oscillator, envelope)) {
      byte[] current = StateCodec.moduleBytes(m);
      records.add(moduleRecord(Arrays.copyOfRange(current, 6, current.length)));
    }
    byte[] old = speaker(42.5);
    records.add(moduleRecord(old));
    for (Cable cable : cables) records.add(cableRecord(cable));
    var restored = StateCodec.load(world(records));
    Assertions.assertTrue(restored.quarantined().isEmpty());
    Assertions.assertEquals(3, restored.state().modules().size());
    Assertions.assertEquals(cables, restored.state().cables());
    var amp = restored.state().get(id);
    Assertions.assertEquals(StateCodec.moduleBytes(old, 1), amp);
    Assertions.assertEquals(ModuleKind.AMP, amp.kind());
    Assertions.assertEquals(42.5, amp.value(Parameter.AMP_LEVEL));
    Assertions.assertEquals(1, amp.value(Parameter.DRONE));
    Assertions.assertFalse(amp.enabled());
    byte[] current = StateCodec.save(restored.state(), restored.quarantined());
    Assertions.assertEquals(2, current[5]);
    var again = StateCodec.load(current);
    Assertions.assertArrayEquals(current, StateCodec.save(again.state(), again.quarantined()));
  }

  @Test
  void quarantinedLegacyRecordsCannotBecomeCurrentSpeakersAfterResave() throws IOException {
    byte[] current = StateCodec.moduleBytes(AmpAudioTest.module(3, ModuleKind.SPEAKER));
    byte[] malformedLegacy = moduleRecord(Arrays.copyOfRange(current, 6, current.length));
    var loaded = StateCodec.load(world(List.of(malformedLegacy)));
    Assertions.assertTrue(loaded.state().modules().isEmpty());
    for (int i = 0; i < 3; i++) {
      byte[] saved = StateCodec.save(loaded.state(), loaded.quarantined());
      loaded = StateCodec.load(saved);
      Assertions.assertTrue(loaded.state().modules().isEmpty(), "Quarantine must never reactivate");
      Assertions.assertEquals(1, loaded.quarantined().size());
      Assertions.assertArrayEquals(malformedLegacy, loaded.quarantined().getFirst());
      Assertions.assertArrayEquals(saved, StateCodec.save(loaded.state(), loaded.quarantined()));
    }
  }

  @Test
  void emptyMaximumAndUnknownLegacyRecordsRemainByteExact() throws IOException {
    byte[] maximum = new byte[4096];
    maximum[0] = 99;
    List<byte[]> records = List.of(new byte[0], maximum, new byte[] {3, 1, 2, 3});
    var loaded = StateCodec.load(world(records));
    for (int i = 0; i < 3; i++) {
      loaded = StateCodec.load(StateCodec.save(loaded.state(), loaded.quarantined()));
      Assertions.assertTrue(loaded.state().modules().isEmpty());
      Assertions.assertEquals(records.size(), loaded.quarantined().size());
      for (int j = 0; j < records.size(); j++)
        Assertions.assertArrayEquals(records.get(j), loaded.quarantined().get(j));
    }
  }

  @Test
  void quarantineDoesNotGrowAWorldAtTheStorageLimit() throws IOException {
    byte[] maximum = new byte[4096];
    maximum[0] = 99;
    List<byte[]> records = new ArrayList<>();
    for (int i = 0; i < 511; i++) records.add(maximum);
    records.add(new byte[StateCodec.MAX_WORLD - 8 - 511 * 4098 - 2]);
    byte[] original = world(records);
    Assertions.assertEquals(StateCodec.MAX_WORLD, original.length);
    var loaded = StateCodec.load(original);
    byte[] saved = StateCodec.save(loaded.state(), loaded.quarantined());
    Assertions.assertEquals(original.length, saved.length);
    var again = StateCodec.load(saved);
    Assertions.assertEquals(512, again.quarantined().size());
    Assertions.assertArrayEquals(saved, StateCodec.save(again.state(), again.quarantined()));
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> StateCodec.save(again.state(), List.of(new byte[4097])));
  }

  @Test
  void malformedLegacyVolumeAndUnknownRecordsRemainOpaque() throws IOException {
    for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1, 101}) {
      byte[] bad = moduleRecord(speaker(value));
      byte[] unknown = {99, 1, 2, 3};
      var loaded = StateCodec.load(world(List.of(bad, unknown)));
      Assertions.assertTrue(loaded.state().modules().isEmpty());
      var again = StateCodec.load(StateCodec.save(loaded.state(), loaded.quarantined()));
      Assertions.assertArrayEquals(bad, again.quarantined().getFirst());
      Assertions.assertArrayEquals(unknown, again.quarantined().get(1));
    }
  }
}
