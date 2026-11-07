package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.domain.StateCodec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AmpTest {
  @Test
  void speakerOnlyAcceptsAudio() {
    Assertions.assertEquals(List.of(Port.AUDIO_IN), ModuleKind.SPEAKER.ports);
  }

  @Test
  void ampHasGateGainAndAudioOutput() {
    ModuleKind amp = ModuleKind.valueOf("AMP");
    Assertions.assertTrue(
        amp.ports.containsAll(List.of(Port.AUDIO_IN, Port.GAIN_IN, Port.GATE_IN, Port.AUDIO_OUT)));
    Assertions.assertEquals(100, amp.defaults().get(Parameter.valueOf("AMP_LEVEL")));
    Assertions.assertEquals(0, amp.defaults().get(Parameter.valueOf("DRONE")));
  }

  @Test
  void legacySpeakerBecomesAmpWithoutLosingIdentityOrVolume() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeLong(0);
      out.writeLong(1);
      out.writeLong(0);
      out.writeLong(2);
      out.writeUTF("SPEAKER");
      out.writeUTF("minecraft:overworld");
      out.writeInt(3);
      out.writeInt(64);
      out.writeInt(5);
      out.writeLong(7);
      out.writeByte(1);
      out.writeUTF("VOLUME");
      out.writeDouble(23);
      out.writeBoolean(false);
      out.writeBoolean(false);
    }
    ModuleState migrated = StateCodec.moduleBytes(bytes.toByteArray());
    Assertions.assertEquals("AMP", migrated.kind().name());
    Assertions.assertEquals(new UUID(0, 1), migrated.id());
    Assertions.assertEquals(7, migrated.revision());
    Assertions.assertEquals(23, migrated.value(Parameter.valueOf("AMP_LEVEL")));
    Assertions.assertEquals(1, migrated.value(Parameter.valueOf("DRONE")));
    Assertions.assertEquals(migrated, StateCodec.moduleBytes(StateCodec.moduleBytes(migrated)));
  }
}
