package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.KeyboardState;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.StateCodec;
import com.malevolentgods.modularcraft.domain.WorldState;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MidiProtocolTest {
  @Test
  void snapshotFourCarriesFullRangeWhileWorldOneIsUnchanged() {
    ModuleState keyboard =
        ModuleState.create(UUID.randomUUID(), UUID.randomUUID(), ModuleKind.KEYBOARD, 0, 0, 0);
    WorldState world = new WorldState();
    world.place(keyboard);
    byte[] original = StateCodec.save(world, List.of(new byte[] {99, 1, 2}));
    for (int note : new int[] {0, 23, 108, 127}) {
      PatchSnapshot snapshot =
          new PatchSnapshot(
              keyboard.id(),
              0,
              1,
              200,
              List.of(keyboard),
              List.of(),
              Map.of(),
              Map.of(keyboard.id(), new KeyboardState(note, true)));
      byte[] encoded = StateCodec.encode(snapshot);
      Assertions.assertEquals(4, encoded[5]);
      Assertions.assertEquals(snapshot, StateCodec.decode(encoded));
      encoded[5] = 2;
      Assertions.assertThrows(IllegalArgumentException.class, () -> StateCodec.decode(encoded));
    }
    var loaded = StateCodec.load(original);
    Assertions.assertArrayEquals(original, StateCodec.save(loaded.state(), loaded.quarantined()));
  }
}
