package com.malevolentgods.modularcraft;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.StateCodec;
import com.malevolentgods.modularcraft.domain.WorldState;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SequencerRedstoneTest {
  private static Parameter mode() {
    return Parameter.valueOf("REDSTONE_MODE");
  }

  @Test
  void modeIsOptInStrictBooleanAndSequencerOnly() {
    ModuleState module =
        ModuleState.create(new UUID(0, 1), new UUID(0, 2), ModuleKind.EUCLIDEAN_SEQUENCER, 0, 0, 0);
    assertEquals(0, module.value(mode()));
    assertEquals(1, module.withValue(mode(), 1).value(mode()));
    for (double value : new double[] {-1, .5, 2, Double.NaN, Double.POSITIVE_INFINITY})
      assertThrows(IllegalArgumentException.class, () -> module.withValue(mode(), value));
    for (ModuleKind kind : ModuleKind.values())
      assertEquals(
          kind == ModuleKind.EUCLIDEAN_SEQUENCER || kind == ModuleKind.STEP_SEQUENCER,
          kind.parameters.contains(mode()));
  }

  @Test
  void settingSurvivesAllCodecsAndOldPeersAreRefused() {
    ModuleState module =
        ModuleState.create(new UUID(0, 1), new UUID(0, 2), ModuleKind.EUCLIDEAN_SEQUENCER, 0, 0, 0)
            .withValue(mode(), 1);
    assertEquals(module.values(), StateCodec.moduleBytes(StateCodec.moduleBytes(module)).values());
    WorldState world = new WorldState();
    world.place(module);
    assertEquals(
        module.values(),
        StateCodec.load(StateCodec.save(world, List.of())).state().get(module.id()).values());
    PatchSnapshot patch =
        new PatchSnapshot(module.id(), 0, 0, 0, List.of(module), List.of(), Map.of());
    byte[] bytes = StateCodec.encode(patch);
    assertEquals(12, bytes[5]);
    assertEquals(patch, StateCodec.decode(bytes));
    bytes[5] = 9;
    assertThrows(IllegalArgumentException.class, () -> StateCodec.decode(bytes));
  }

  @Test
  void exactLegacyBlocksAndWorldsMigrateButMalformedRecordsStayQuarantined() throws IOException {
    List<Parameter> old =
        ModuleKind.EUCLIDEAN_SEQUENCER.parameters.stream().filter(p -> p != mode()).toList();
    for (int schema : new int[] {1, 2}) {
      byte[] legacy = LfoCodecTest.legacy(ModuleKind.EUCLIDEAN_SEQUENCER, old);
      byte[] block = schema == 1 ? legacy : framed(legacy, schema, false);
      ModuleState migrated = StateCodec.moduleBytes(block);
      assertEquals(0, migrated.value(mode()));
      assertEquals(
          migrated.values(), StateCodec.moduleBytes(StateCodec.moduleBytes(migrated)).values());
      var loaded = StateCodec.load(framed(legacy, schema, true));
      assertTrue(loaded.quarantined().isEmpty());
      assertEquals(migrated.values(), loaded.state().get(migrated.id()).values());
      byte[] malformed =
          LfoCodecTest.legacy(ModuleKind.EUCLIDEAN_SEQUENCER, old.subList(1, old.size()));
      byte[] badBlock = schema == 1 ? malformed : framed(malformed, schema, false);
      assertThrows(IllegalArgumentException.class, () -> StateCodec.moduleBytes(badBlock));
      var badWorld = StateCodec.load(framed(malformed, schema, true));
      assertTrue(badWorld.state().modules().isEmpty());
      assertEquals(1, badWorld.quarantined().size());
      var again = StateCodec.load(StateCodec.save(badWorld.state(), badWorld.quarantined()));
      assertArrayEquals(badWorld.quarantined().getFirst(), again.quarantined().getFirst());
    }
  }

  @Test
  void networkRecordsNeverReceiveTheStoredLegacyDefault() throws IOException {
    ModuleState module =
        ModuleState.create(new UUID(0, 1), new UUID(0, 2), ModuleKind.EUCLIDEAN_SEQUENCER, 0, 0, 0);
    byte[] bytes =
        StateCodec.encode(
            new PatchSnapshot(module.id(), 0, 0, 0, List.of(module), List.of(), Map.of()));
    int countOffset, fieldOffset;
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      in.skipNBytes(4 + 2 + 16 + 8 * 3 + 2 + 16 * 2);
      in.readUTF();
      in.readUTF();
      in.skipNBytes(4 * 3 + 8);
      countOffset = bytes.length - in.available();
      int count = in.readUnsignedByte();
      for (int i = 0; i < count - 1; i++) {
        in.readUTF();
        in.readDouble();
      }
      fieldOffset = bytes.length - in.available();
      assertEquals("REDSTONE_MODE", in.readUTF());
      in.readDouble();
      int after = bytes.length - in.available();
      byte[] missing = new byte[bytes.length - (after - fieldOffset)];
      System.arraycopy(bytes, 0, missing, 0, fieldOffset);
      System.arraycopy(bytes, after, missing, fieldOffset, bytes.length - after);
      missing[countOffset]--;
      assertThrows(IllegalArgumentException.class, () -> StateCodec.decode(missing));
    }
  }

  private static byte[] framed(byte[] module, int schema, boolean world) throws IOException {
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeInt(0x4d435301);
      out.writeShort(schema);
      if (world) {
        out.writeShort(1);
        out.writeShort(module.length + 1);
        out.writeByte(1);
      }
      out.write(module);
      return bytes.toByteArray();
    }
  }
}
