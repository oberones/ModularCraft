package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.StateCodec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LfoCodecTest {
  @Test
  void snapshotProtocolIsEightAndRefusesSix() {
    ModuleState module =
        ModuleState.create(new UUID(0, 1), new UUID(0, 2), ModuleKind.LFO, 0, 0, 0);
    PatchSnapshot patch =
        new PatchSnapshot(module.id(), 0, 0, 0, List.of(module), List.of(), Map.of());
    byte[] bytes = StateCodec.encode(patch);
    Assertions.assertEquals(8, bytes[5]);
    Assertions.assertEquals(patch, StateCodec.decode(bytes));
    bytes[5] = 6;
    Assertions.assertThrows(IllegalArgumentException.class, () -> StateCodec.decode(bytes));
  }

  static byte[] legacy(ModuleKind kind, List<Parameter> parameters) throws IOException {
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeLong(0);
      out.writeLong(1);
      out.writeLong(0);
      out.writeLong(2);
      out.writeUTF(kind.name());
      out.writeUTF("minecraft:overworld");
      out.writeInt(0);
      out.writeInt(0);
      out.writeInt(0);
      out.writeLong(0);
      out.writeByte(parameters.size());
      for (Parameter p : parameters) {
        out.writeUTF(p.name());
        out.writeDouble(p.initial);
      }
      out.writeBoolean(false);
      out.writeBoolean(false);
      return bytes.toByteArray();
    }
  }

  @Test
  void exactLegacySchemasMigrateAndMalformedSubsetsAreRefused() throws IOException {
    for (ModuleKind kind : List.of(ModuleKind.FILTER, ModuleKind.ENVELOPE)) {
      List<Parameter> old =
          kind == ModuleKind.FILTER
              ? List.of(Parameter.CUTOFF, Parameter.RESONANCE, Parameter.AMOUNT)
              : List.of(Parameter.ATTACK, Parameter.DECAY, Parameter.SUSTAIN, Parameter.RELEASE);
      ModuleState migrated = StateCodec.moduleBytes(legacy(kind, old));
      Assertions.assertEquals(kind.defaults(), migrated.values());
      Assertions.assertEquals(migrated, StateCodec.moduleBytes(StateCodec.moduleBytes(migrated)));
      byte[] malformed = legacy(kind, old.subList(1, old.size()));
      Assertions.assertThrows(
          IllegalArgumentException.class, () -> StateCodec.moduleBytes(malformed));
    }
  }
}
